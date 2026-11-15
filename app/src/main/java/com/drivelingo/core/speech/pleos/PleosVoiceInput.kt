package com.drivelingo.core.speech.pleos

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.OnServerConnectionListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.util.Log
import com.drivelingo.core.speech.ListenRequest
import com.drivelingo.core.speech.ListenResult
import com.drivelingo.core.speech.VoiceInput
import com.drivelingo.core.speech.VoiceSimulation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Pleos SpeechToText SDK 실연동 (`ai.pleos.playground:SpeechToText`, 권한 `pleos.car.permission.STT_SERVICE`).
 *
 * - `request()` 로 한 번 인식을 시작하고, 발화 끝(EPD)에서 SDK 가 `onEndedRecognition()` 을 부르면 끝낸다.
 * - `onUpdated(text, completed)`: completed=true 인 결과를 모아 최종 답으로 쓴다. 최종 결과 없이 끝나면
 *   마지막 중간 결과를 쓰고, 그것도 없으면 무응답이다.
 * - [ListenRequest.maxDurationMs] 가 있으면 그 시간에 `stop()` 하고 그때까지 들은 것을 쓴다.
 *   없으면(긴 대답) 끊지 않고 EPD 를 기다린다. 다만 SDK 가 끝을 알리지 않는 경우를 대비해 [LONG_ANSWER_CAP_MS] 에서 멈춘다.
 * - 인식 언어는 영어(en-US) 로 고정한다. 학습자 답변은 영어다.
 *
 * 리스너는 수업 동안 하나만 등록하고(공식 가이드: request() 전에 등록), 현재 요청([active])으로 전달한다.
 */
class PleosVoiceInput(
    context: Context,
    credentials: PleosCredentials?,
    private val monitor: VoiceSimulation,
) : VoiceInput {

    private val stt = SpeechToText(context.applicationContext, if (credentials != null) Mode.HYBRID else Mode.ON_DEVICE)
    private val ready = CompletableDeferred<Boolean>()

    /** 한 번의 인식 요청. SDK 콜백이 어느 스레드에서 오든 안전하도록 결과는 Deferred 로 넘긴다. */
    private class Recognition {
        val finals = StringBuilder()
        @Volatile var partial: String? = null
        val ended = CompletableDeferred<ListenResult>()

        @Synchronized fun addFinal(text: String) {
            val current = finals.toString()
            // 최종 결과가 누적 문장으로 오는지 구간별로 오는지 문서에 없어 둘 다 처리한다.
            if (current.isEmpty() || text.startsWith(current)) {
                finals.setLength(0)
                finals.append(text)
            } else {
                finals.append(' ').append(text)
            }
        }

        @Synchronized fun result(): ListenResult {
            val text = finals.toString().ifBlank { partial.orEmpty() }.trim()
            return if (text.isEmpty()) ListenResult.NoSpeech else ListenResult.Recognized(text)
        }
    }

    @Volatile private var active: Recognition? = null

    private val listener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = Unit
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit

        override fun onUpdated(stt: String, completed: Boolean) {
            val r = active ?: return
            if (completed) r.addFinal(stt) else r.partial = stt
        }

        override fun onEndedRecognition() {
            active?.let { it.ended.complete(it.result()) }
        }

        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "stt error $errorType")
            val r = active ?: return
            r.ended.complete(
                when (errorType) {
                    // 시간 안에 말하지 않음 → 무응답(다시 묻기 흐름). 그 밖에는 인식 실패(1회 재시도 흐름).
                    ErrorType.TIMEOUT -> r.result()
                    else -> ListenResult.Failed("pleos stt: $errorType")
                },
            )
        }
    }

    init {
        stt.addListener(listener)
        stt.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                Log.i(TAG, "stt initialized (server=${credentials != null})")
                runCatching { stt.setLocale(Locale.US) }.onFailure { Log.w(TAG, "setLocale failed", it) }
                credentials?.let(::register)
                ready.complete(true)
            }

            override fun onFailure(errorType: ErrorType) {
                Log.w(TAG, "stt initialize failed: $errorType")
                ready.complete(false)
            }
        })
    }

    private fun register(credentials: PleosCredentials) {
        stt.registerApp(credentials.clientId, credentials.clientSecret, object : OnServerConnectionListener {
            override fun onConnected() {
                Log.i(TAG, "stt server connected")
            }
            override fun onFailed(errorType: ErrorType) {
                Log.w(TAG, "stt server registration failed: $errorType (on-device only)")
            }
        })
    }

    override suspend fun listen(request: ListenRequest): ListenResult {
        val initialized = withTimeoutOrNull(INIT_TIMEOUT_MS) { ready.await() } ?: false
        if (!initialized) return ListenResult.Failed("pleos stt unavailable")

        val recognition = Recognition()
        active = recognition
        val result = try {
            stt.request()
            val limit = request.maxDurationMs ?: LONG_ANSWER_CAP_MS
            withTimeoutOrNull(limit) { recognition.ended.await() } ?: run {
                // 제한 시간: 인식을 멈추고 그때까지 들은 것을 쓴다.
                runCatching { stt.stop() }
                withTimeoutOrNull(STOP_GRACE_MS) { recognition.ended.await() } ?: recognition.result()
            }
        } finally {
            // 취소(수업 중단)되면 마이크를 바로 닫는다.
            if (!recognition.ended.isCompleted) runCatching { stt.stop() }
            if (active === recognition) active = null
        }
        if (result is ListenResult.Recognized) monitor.setHeard(result.text)
        return result
    }

    override fun cancel() {
        if (active != null) runCatching { stt.stop() }.onFailure { Log.w(TAG, "stt stop failed", it) }
    }

    fun release() {
        stt.removeListener(listener)
        stt.release()
    }

    private companion object {
        const val TAG = "PleosStt"
        const val INIT_TIMEOUT_MS = 5_000L
        const val STOP_GRACE_MS = 1_500L
        /** 긴 대답의 안전 상한. 정상이라면 EPD 가 먼저 끝낸다. */
        const val LONG_ANSWER_CAP_MS = 60_000L
    }
}

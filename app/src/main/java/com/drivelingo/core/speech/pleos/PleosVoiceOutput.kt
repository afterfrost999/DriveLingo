package com.drivelingo.core.speech.pleos

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.tts.listener.OnServerConnectionListener
import android.content.Context
import android.util.Log
import com.drivelingo.core.speech.VoiceOutput
import com.drivelingo.core.speech.VoiceSimulation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Pleos Playground My Project 에서 발급받은 앱 등록 정보. 없으면 On-device 엔진만 쓴다. */
data class PleosCredentials(val clientId: String, val clientSecret: String) {
    companion object {
        fun of(clientId: String, clientSecret: String): PleosCredentials? =
            if (clientId.isBlank() || clientSecret.isBlank()) null else PleosCredentials(clientId, clientSecret)
    }
}

/**
 * Pleos TextToSpeech SDK 실연동 (`ai.pleos.playground:TextToSpeech`, 권한 `pleos.car.permission.TTS_SERVICE`).
 *
 * - 앱 등록 정보가 있으면 HYBRID(서버 우선, 끊기면 On-device), 없으면 ON_DEVICE 로 만든다.
 *   서버 합성은 `registerApp()` 이 `onConnected()` 를 부른 뒤에만 된다(공식 가이드).
 * - `speak(text, isFinal = true)` 로 한 문장씩 요청하고 `EventListener.onDone()` / `onStop()` / `onError()` 에서 끝낸다.
 * - 코루틴이 취소되면(수업 중단) 즉시 `stop()` 한다.
 * - SDK 에 말하기 속도 API 가 없어 [rate](퇴근 복습 느리게)는 아직 적용하지 못한다(docs/pleos-sdk-status.md).
 *
 * [monitor] 는 차량 시뮬레이터 화면에 "지금 읽는 문장"을 보여 주기 위해서만 쓴다.
 */
class PleosVoiceOutput(
    context: Context,
    credentials: PleosCredentials?,
    private val monitor: VoiceSimulation,
) : VoiceOutput {

    private val tts = TextToSpeech(context.applicationContext, if (credentials != null) Mode.HYBRID else Mode.ON_DEVICE)
    private val ready = CompletableDeferred<Boolean>()

    @Volatile private var pending: CompletableDeferred<Unit>? = null
    /** 우리가 stop() 을 불러서 올 onStop() 개수. 다음 문장의 완료로 잘못 처리하지 않도록 무시한다. */
    private val expectedStops = AtomicInteger(0)
    private var currentLocale: Locale? = null

    private val listener = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() {
            pending?.complete(Unit)
        }

        override fun onStop() {
            if (expectedStops.getAndUpdate { if (it > 0) it - 1 else 0 } > 0) return
            pending?.complete(Unit)
        }

        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "tts error $errorType")
            pending?.complete(Unit)
        }
    }

    init {
        tts.addEventListener(listener)
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                Log.i(TAG, "tts initialized (server=${credentials != null})")
                credentials?.let(::register)
                ready.complete(true)
            }

            override fun onFailure(errorType: ErrorType) {
                Log.w(TAG, "tts initialize failed: $errorType")
                ready.complete(false)
            }
        })
    }

    private fun register(credentials: PleosCredentials) {
        tts.registerApp(credentials.clientId, credentials.clientSecret, object : OnServerConnectionListener {
            override fun onConnected() {
                Log.i(TAG, "tts server connected")
            }
            override fun onFailed(errorType: ErrorType) {
                Log.w(TAG, "tts server registration failed: $errorType (on-device only)")
            }
        })
    }

    override suspend fun speak(text: String, rate: Float) {
        monitor.setSpeaking(text)
        try {
            val initialized = withTimeoutOrNull(INIT_TIMEOUT_MS) { ready.await() } ?: false
            if (!initialized) {
                // TTS 를 쓸 수 없으면 수업이 멈춰 버리지 않도록 문장 길이만큼 기다린다(소리 없음).
                Log.w(TAG, "tts unavailable; skipping speech")
                delay(estimateMs(text))
                return
            }
            applyLocaleFor(text)
            val done = CompletableDeferred<Unit>()
            pending = done
            try {
                tts.speak(text, true)
                if (withTimeoutOrNull(estimateMs(text) * 3 + 5_000) { done.await() } == null) {
                    Log.w(TAG, "tts did not finish in time; stopping")
                }
            } finally {
                // 정상 종료가 아니면(취소·시간 초과) 재생을 멈춘다.
                if (!done.isCompleted) stopPlayback()
                if (pending === done) pending = null
            }
        } finally {
            monitor.setSpeaking(null)
        }
    }

    override fun stop() {
        if (pending?.isCompleted == false) stopPlayback()
        monitor.setSpeaking(null)
    }

    private fun stopPlayback() {
        expectedStops.incrementAndGet()
        runCatching { tts.stop() }.onFailure { Log.w(TAG, "tts stop failed", it) }
    }

    /** 영어 수업이지만 한국어 안내 문장이 섞일 수 있어 문장마다 언어를 고른다. 바뀔 때만 설정한다. */
    private fun applyLocaleFor(text: String) {
        val locale = if (text.any { it in '가'..'힣' }) Locale.KOREAN else Locale.US
        if (locale != currentLocale) {
            runCatching { tts.setLocale(locale) }.onFailure { Log.w(TAG, "setLocale($locale) failed", it) }
            currentLocale = locale
        }
    }

    private fun estimateMs(text: String): Long {
        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
        return (words * MS_PER_WORD).coerceIn(MIN_MS, MAX_MS)
    }

    fun release() {
        tts.removeEventListener(listener)
        tts.release()
    }

    private companion object {
        const val TAG = "PleosTts"
        const val INIT_TIMEOUT_MS = 5_000L
        const val MS_PER_WORD = 330L
        const val MIN_MS = 1_200L
        const val MAX_MS = 7_000L
    }
}

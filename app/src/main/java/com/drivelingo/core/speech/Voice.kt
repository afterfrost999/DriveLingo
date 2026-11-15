package com.drivelingo.core.speech

/**
 * 음성 출력(TTS) 경계.
 * 실제 구현 대상: Pleos TextToSpeech SDK (`ai.pleos.playground.tts.TextToSpeech`, speak() + EventListener.onDone()).
 */
interface VoiceOutput {
    /** 재생이 끝나면 반환한다. 코루틴이 취소되면 즉시 멈춰야 한다. */
    suspend fun speak(text: String, rate: Float = 1.0f)
    fun stop()
}

enum class AnswerStyle { REPEAT, SHORT, LONG }

/**
 * @param maxDurationMs null 이면 시간 제한 없음(긴 대답). 끝은 STT 의 발화 종료 감지로 판단한다.
 * @param itemId, expected Mock 이 그럴듯한 답을 만들 때만 쓰는 힌트. 실제 STT 는 무시한다.
 */
data class ListenRequest(
    val style: AnswerStyle,
    val maxDurationMs: Long?,
    val itemId: String,
    val expected: String?,
)

sealed interface ListenResult {
    data class Recognized(val text: String) : ListenResult
    data object NoSpeech : ListenResult
    data class Failed(val reason: String) : ListenResult
}

/**
 * 음성 입력(STT) 경계.
 * 실제 구현 대상: Pleos SpeechToText SDK (`ai.pleos.playground.stt.SpeechToText`, request()/stop() + ResultListener).
 * HYBRID 모드는 네트워크가 끊기면 On-device 엔진으로 자동 전환된다(공식 문서).
 */
interface VoiceInput {
    suspend fun listen(request: ListenRequest): ListenResult
    fun cancel()
}

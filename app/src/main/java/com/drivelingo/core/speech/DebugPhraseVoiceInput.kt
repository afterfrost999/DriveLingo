package com.drivelingo.core.speech

import kotlinx.coroutines.delay

/**
 * 디버그 빌드 전용. 차량 시뮬레이터·adb(`--es say "…"`)로 정해 둔 문장이 있으면 실제 STT 대신 그 문장을
 * 들은 것으로 돌려준다. 마이크 없이도 Pleos 음성 빌드에서 음성 명령과 대답 흐름을 시험하기 위한 것이다.
 * 정해 둔 문장이 없으면 실제 STT([delegate])를 그대로 쓴다.
 */
class DebugPhraseVoiceInput(
    private val delegate: VoiceInput,
    private val simulation: VoiceSimulation,
) : VoiceInput {

    override suspend fun listen(request: ListenRequest): ListenResult {
        val phrase = simulation.consumeNextPhrase() ?: return delegate.listen(request)
        delay(SPOKEN_PHRASE_MS)
        simulation.setHeard(phrase)
        return ListenResult.Recognized(phrase)
    }

    override fun cancel() = delegate.cancel()

    private companion object {
        const val SPOKEN_PHRASE_MS = 1_500L
    }
}

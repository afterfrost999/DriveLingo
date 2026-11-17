package com.drivelingo.core.speech

import com.drivelingo.core.audio.Earcon
import com.drivelingo.core.audio.EarconPlayer
import com.drivelingo.core.audio.EarconSynth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 다음 한 번의 음성 인식 결과를 강제로 정한다(테스트 콘솔). 한 번 쓰이면 AUTO 로 돌아간다. */
enum class NextAnswer(val label: String) { AUTO("자동"), NOT_RECOGNIZED("인식 실패"), NO_SPEECH("무응답") }

/** Mock 음성 장치들이 공유하는 조작 상태. */
class VoiceSimulation {
    val nextAnswer = MutableStateFlow(NextAnswer.AUTO)
    /** 다음 한 번의 인식 결과를 이 문장으로 정한다(음성 명령 시험용). 한 번 쓰이면 비운다. */
    val nextPhrase = MutableStateFlow<String?>(null)
    /** 켜면 AI 코치 응답이 타임아웃보다 늦어진다. */
    val slowCoach = MutableStateFlow(false)

    private val _nowSpeaking = MutableStateFlow<String?>(null)
    val nowSpeaking: StateFlow<String?> = _nowSpeaking.asStateFlow()

    private val _lastHeard = MutableStateFlow<String?>(null)
    val lastHeard: StateFlow<String?> = _lastHeard.asStateFlow()

    private val _lastEarcon = MutableStateFlow<String?>(null)
    /** 마지막으로 낸 효과음(차량 시뮬레이터 표시용). 실제 재생 여부와 관계없이 기록한다. */
    val lastEarcon: StateFlow<String?> = _lastEarcon.asStateFlow()

    internal fun setSpeaking(text: String?) { _nowSpeaking.value = text }
    internal fun setHeard(text: String?) { _lastHeard.value = text }
    fun setEarcon(label: String?) { _lastEarcon.value = label }

    internal fun consumeNextAnswer(): NextAnswer = nextAnswer.value.also { nextAnswer.value = NextAnswer.AUTO }
    internal fun consumeNextPhrase(): String? = nextPhrase.value.also { nextPhrase.value = null }
}

/** 실제 소리 없이 문장 길이만큼 기다리는 TTS. */
class SimulatedVoiceOutput(private val simulation: VoiceSimulation) : VoiceOutput {
    override suspend fun speak(text: String, rate: Float) {
        simulation.setSpeaking(text)
        try {
            val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
            delay((words * MS_PER_WORD / rate).toLong().coerceIn(MIN_MS, MAX_MS))
        } finally {
            simulation.setSpeaking(null)
        }
    }

    override fun stop() = simulation.setSpeaking(null)

    private companion object {
        const val MS_PER_WORD = 330
        const val MIN_MS = 1_200L
        const val MAX_MS = 7_000L
    }
}

/** 미리 정해 둔 학습자 답변을 돌려주는 STT. 일부러 틀린 문장을 섞어 교정 흐름을 보여 준다. */
class SimulatedVoiceInput(private val simulation: VoiceSimulation) : VoiceInput {

    private var turnCount = 0

    override suspend fun listen(request: ListenRequest): ListenResult {
        simulation.consumeNextPhrase()?.let { phrase ->
            delay(SPOKEN_PHRASE_MS)
            simulation.setHeard(phrase)
            return ListenResult.Recognized(phrase)
        }
        val forced = simulation.consumeNextAnswer()
        val speakingTime = when (request.style) {
            AnswerStyle.LONG -> 6_000L
            AnswerStyle.SHORT -> 2_800L
            AnswerStyle.REPEAT -> 2_200L
        }
        val limit = request.maxDurationMs
        return when (forced) {
            NextAnswer.NO_SPEECH -> {
                delay(limit ?: SILENCE_TIMEOUT_MS)
                ListenResult.NoSpeech
            }
            NextAnswer.NOT_RECOGNIZED -> {
                delay(speakingTime)
                ListenResult.Failed("simulated recognition failure")
            }
            NextAnswer.AUTO -> {
                if (limit != null && speakingTime > limit) {
                    delay(limit)
                    return ListenResult.NoSpeech
                }
                delay(speakingTime)
                val text = MockLearnerAnswers.answerFor(request, turnCount++)
                simulation.setHeard(text)
                ListenResult.Recognized(text)
            }
        }
    }

    override fun cancel() = Unit

    private companion object {
        const val SILENCE_TIMEOUT_MS = 8_000L
        const val SPOKEN_PHRASE_MS = 1_500L
    }
}

/** Mock 학습자 답변. 실제 서비스에서는 존재하지 않는 데이터다. */
internal object MockLearnerAnswers {

    private val byItem = mapOf(
        "warmup" to Pair("How was your weekend?", "I go to camping with my family."),
        "practice-0" to Pair("Thanks for come, everyone.", "Thanks for coming, everyone. Today we talk about the new project."),
        "practice-1" to Pair("Shall we get started?", "Okay, everyone is here. Shall we get started?"),
        "roleplay" to Pair("No problem. Shall we get started?", "No problem. Thanks for coming, everyone. Shall we get started?"),
        "personal" to Pair("Yes, I have meeting at ten.", "I have a team meeting on Thursday. My manager and two designers will be there."),
        "day-talk" to Pair("It was busy but good.", "It was busy. I had a long meeting, but I said thanks for coming, everyone."),
    )

    fun answerFor(request: ListenRequest, turn: Int): String {
        val expected = request.expected
        if (request.style == AnswerStyle.REPEAT && expected != null) {
            // 네 번에 한 번은 단어 하나를 빠뜨린다.
            if (turn % 4 == 3) return expected.split(" ").dropLast(1).joinToString(" ")
            return expected
        }
        val pair = byItem[request.itemId] ?: return expected ?: "Sorry, I don't know."
        return if (request.style == AnswerStyle.LONG) pair.second else pair.first
    }
}

/** 소리 없이 효과음 길이만큼 기다리고 차량 시뮬레이터에 표시만 한다. */
class SimulatedEarconPlayer(private val simulation: VoiceSimulation) : EarconPlayer {
    override suspend fun play(earcon: Earcon) {
        simulation.setEarcon(earcon.label)
        delay(EarconSynth.durationMs(earcon).toLong())
    }
}

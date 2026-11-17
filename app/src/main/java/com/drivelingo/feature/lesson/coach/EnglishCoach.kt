package com.drivelingo.feature.lesson.coach

import com.drivelingo.core.curriculum.Level
import com.drivelingo.core.progress.Correction
import com.drivelingo.core.speech.VoiceSimulation
import com.drivelingo.feature.lesson.engine.ActivityType
import com.drivelingo.feature.lesson.engine.CoachFeedback
import com.drivelingo.feature.lesson.engine.PhraseMatcher
import kotlinx.coroutines.delay

data class CoachRequest(
    val level: Level,
    val itemId: String,
    val activity: ActivityType,
    val question: String,
    val expectedAnswer: String,
    val answer: String,
    /** 정답이 없는 내 얘기 질문. 모범 답안은 예시로만 들려준다. */
    val openEnded: Boolean = false,
)

/**
 * AI 영어 코치(LLM) 경계.
 *
 * 실제 구현은 차량 앱 → 자체 백엔드(Spring Boot) → LLM API 순서로 호출해야 한다.
 * LLM API 키는 차량 앱에 넣지 않는다. 백엔드는 이번 프로토타입 범위가 아니라서 [MockEnglishCoach] 만 있다.
 *
 * 리캐스트 원칙: 틀린 문장을 지적하지 않고 자연스러운 문장으로 되받아 말해 준다.
 */
interface EnglishCoach {
    suspend fun feedback(request: CoachRequest): CoachFeedback
}

class MockEnglishCoach(private val simulation: VoiceSimulation) : EnglishCoach {

    override suspend fun feedback(request: CoachRequest): CoachFeedback {
        delay(if (simulation.slowCoach.value) SLOW_MS else NORMAL_MS)

        val normalized = request.answer.lowercase()
        KNOWN_MISTAKES.firstOrNull { (pattern, _) -> pattern in normalized }?.let { (_, corrected) ->
            return CoachFeedback(
                reply = "Oh, I see. $corrected Nice!",
                correct = false,
                recast = Correction(request.answer, corrected),
            )
        }
        val similarity = PhraseMatcher.similarity(request.answer, request.expectedAnswer)
        return if (request.openEnded) {
            // 내 얘기 질문은 학습자 답이 모범 답안과 달라도 맞는 답이다. 예시와 다르다고 고쳐 주지 않는다.
            CoachFeedback("Thanks for sharing! For example, you could also say: ${request.expectedAnswer}", correct = true)
        } else if (similarity >= 0.6f) {
            CoachFeedback("Great! That sounds natural.", correct = true)
        } else {
            CoachFeedback("Nice! You could also say: ${request.expectedAnswer}", correct = true)
        }
    }

    private companion object {
        const val NORMAL_MS = 800L
        const val SLOW_MS = 10_000L

        /** Mock 전용 교정 규칙. 실제로는 LLM 이 판단한다. */
        val KNOWN_MISTAKES = listOf(
            "i go to camping" to "I went camping with my family.",
            "thanks for come" to "Thanks for coming, everyone.",
            "i have meeting" to "I have a meeting at ten.",
            "today we talk about" to "Today, we'll talk about the new project.",
        )
    }
}

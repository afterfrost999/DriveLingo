package com.drivelingo.feature.onboarding

import com.drivelingo.core.curriculum.Level

/**
 * 레벨 테스트 결과 → 추천 레벨. 순수 함수.
 *
 * - 따라 말하기(4→8→14단어) 통과 개수로 기본 레벨을 정하고
 * - 짧은 인터뷰의 평균 단어 수로 위쪽 레벨을 가르고
 * - 자기 평가와 두 단계 이상 차이 나지 않게 맞춘다.
 */
object LevelEstimator {

    const val REPEAT_PASS = 0.8f

    fun estimate(selfLevel: Int, repeatScores: List<Float>, interviewWords: List<Int>): Level {
        val passed = repeatScores.count { it >= REPEAT_PASS }
        val avgWords = if (interviewWords.isEmpty()) 0.0 else interviewWords.average()
        val measured = when {
            passed <= 1 -> 0
            passed == 2 -> 1
            avgWords < 10 -> 1
            avgWords < 20 -> 2
            else -> 3
        }
        val self = selfLevel.coerceIn(0, 3)
        return Level.entries[measured.coerceIn(self - 1, self + 1).coerceIn(0, 3)]
    }
}

/** 레벨 테스트 문항(약 3분). */
object LevelTestContent {
    val selfAssessment = listOf(
        "인사 정도만 할 수 있어요",
        "짧은 문장으로 일상 얘기를 해요",
        "회의에서 내 생각을 말할 수 있어요",
        "회의를 영어로 이끌 수 있어요",
    )

    /** 4단어 → 8단어 → 14단어 */
    val repeatSentences = listOf(
        "Nice to meet you.",
        "I usually drive to work at eight o'clock.",
        "Could we move our weekly meeting to Thursday because I have a client visit?",
    )

    val interviewQuestions = listOf(
        "What do you do at work?",
        "Tell me about a problem you had at work recently.",
    )

    /** Mock STT 가 돌려줄 인터뷰 답변(실제 서비스에는 없음). */
    val mockInterviewAnswers = listOf(
        "I work in sales. I make reports every day.",
        "The meeting was late, so I missed my lunch.",
    )

    const val VOICE_TEST_SENTENCE = "Hello, I'm ready to learn."
}

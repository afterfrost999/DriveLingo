package com.drivelingo.feature.lesson.engine

import com.drivelingo.core.progress.Correction

/** 문장 비교. 토큰 단위 최장 공통 부분수열 비율(0..1). */
object PhraseMatcher {
    fun tokens(text: String): List<String> =
        text.lowercase().replace(Regex("[^a-z0-9' ]"), " ").split(" ").filter { it.isNotBlank() }

    fun similarity(a: String, b: String): Float {
        val x = tokens(a)
        val y = tokens(b)
        if (x.isEmpty() && y.isEmpty()) return 1f
        if (x.isEmpty() || y.isEmpty()) return 0f
        val dp = Array(x.size + 1) { IntArray(y.size + 1) }
        for (i in 1..x.size) for (j in 1..y.size) {
            dp[i][j] = if (x[i - 1] == y[j - 1]) dp[i - 1][j - 1] + 1 else maxOf(dp[i - 1][j], dp[i][j - 1])
        }
        return 2f * dp[x.size][y.size] / (x.size + y.size)
    }
}

object WordCounter {
    fun count(text: String): Int = PhraseMatcher.tokens(text).size
}

/**
 * AI 코치를 쓸 수 없을 때(오프라인, 응답 지연, 오류) 기기 안에서 만드는 피드백.
 * 따라 말하기는 정답 문장이 있어 채점할 수 있고, 열린 대답은 채점하지 않고 모범 답안만 들려준다.
 */
object LocalFeedback {
    const val REPEAT_PASS = 0.8f

    fun build(turn: Turn, answer: String): CoachFeedback {
        val expected = turn.expectedAnswer
        if (turn.activity == ActivityType.REPEAT) {
            return if (PhraseMatcher.similarity(answer, expected) >= REPEAT_PASS) {
                CoachFeedback("Perfect.", correct = true)
            } else {
                CoachFeedback("Almost. Listen again. $expected", correct = false, recast = Correction(answer, expected))
            }
        }
        return CoachFeedback("Good. Here's one more way to say it. $expected", correct = true)
    }
}

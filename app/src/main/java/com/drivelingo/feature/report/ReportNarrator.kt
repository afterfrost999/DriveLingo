package com.drivelingo.feature.report

import com.drivelingo.core.driving.Interruption
import com.drivelingo.core.driving.LearningMode
import com.drivelingo.core.progress.LessonTally

/** 수업 기록 → 리포트 문장. "오늘 길은 …였고, 신호 대기에서 한 번 길게 말했어요" */
object ReportNarrator {

    fun drivingLine(tally: LessonTally): String {
        val turns = tally.turnsByMode
        val longTurns = turns[LearningMode.LONG_ANSWER] ?: 0
        val dominant = turns.filterKeys { it != LearningMode.LONG_ANSWER && it != LearningMode.SILENT }
            .maxByOrNull { it.value }?.key
        val base = when (dominant) {
            LearningMode.SHORT_ANSWER -> "오늘 길은 막힘없이 달려서 짧게 대답 위주였고"
            LearningMode.REPEAT_AFTER -> "오늘 길은 가다 서다가 많아서 따라 말하기 위주였고"
            LearningMode.LISTEN_ONLY -> "오늘 길은 복잡한 구간이 많아서 듣기 위주였고"
            LearningMode.DETAILED_REVIEW -> "오늘은 주차 중에 차분히 연습했고"
            else -> null
        }
        val longPart = if (longTurns > 0) "신호 대기에서 ${countWord(longTurns)} 길게 말했어요" else null
        return when {
            base != null && longPart != null -> "$base, $longPart"
            base != null -> base.removeSuffix("고") + "어요"
            longPart != null -> "오늘은 $longPart"
            else -> "오늘은 차분하게 들으며 수업을 마쳤어요"
        }
    }

    /** 멈춘 기록. 없으면 null. */
    fun pauseLine(tally: LessonTally): String? {
        val pauses = tally.pausesByReason.filterValues { it > 0 }
        if (pauses.isEmpty()) return null
        val (reason, count) = pauses.entries.sortedWith(compareByDescending<Map.Entry<Interruption, Int>> { it.value }.thenBy { it.key.priority }).first()
        val total = pauses.values.sum()
        val reasonText = if (pauses.size == 1) "${withParticle(reason.label)} ${count}번" else "${withParticle(reason.label)} 포함 ${total}번"
        return "$reasonText 멈췄지만 콤보는 그대로 이어졌어요"
    }

    private fun countWord(n: Int) = when (n) {
        1 -> "한 번"
        2 -> "두 번"
        3 -> "세 번"
        else -> "${n}번"
    }

    /** 받침에 따라 "으로/로". (받침 ㄹ 은 "로") */
    internal fun withParticle(word: String): String {
        val last = word.lastOrNull() ?: return word
        if (last !in '가'..'힣') return "${word}로"
        val jong = (last - '가') % 28
        return if (jong == 0 || jong == 8) "${word}로" else "${word}으로"
    }
}

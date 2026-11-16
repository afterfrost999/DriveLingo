package com.drivelingo.feature.lesson.engine

import com.drivelingo.core.curriculum.ItemKind
import com.drivelingo.core.curriculum.LessonItem
import com.drivelingo.core.driving.LearningMode

/** 수업 아이템 + 현재 학습 모드 → 이번 턴의 활동. */
object ActivityPlanner {

    const val REPEAT_LIMIT_MS = 8_000L
    const val SHORT_LIMIT_MS = 12_000L
    const val DETAILED_LIMIT_MS = 20_000L

    /**
     * SILENT 모드에서는 턴을 만들지 않는다(null).
     * @param listenOnly "듣기만 할게" 명령. 말하기 활동을 듣기로 낮춘다(침묵은 그대로).
     */
    fun plan(item: LessonItem, mode: LearningMode, offline: Boolean, listenOnly: Boolean = false): Turn? {
        // 오프라인이면 LLM 이 필요한 열린 대답 대신 기기에서 채점할 수 있는 따라 말하기로 바꾼다.
        val online = if (offline && mode in OPEN_ANSWER_MODES) LearningMode.REPEAT_AFTER else mode
        val effective = if (listenOnly && LearningMode.LISTEN_ONLY.isMoreRestrictiveThan(online)) LearningMode.LISTEN_ONLY else online
        val target = item.target.english

        if (effective == LearningMode.SILENT) return null
        if (item.kind == ItemKind.WRAP_UP || effective == LearningMode.LISTEN_ONLY) {
            return Turn(item.id, ActivityType.LISTEN, item.listenScript, item.modelAnswer, null, effective)
        }
        if (item.kind == ItemKind.INTRODUCE || effective == LearningMode.REPEAT_AFTER) {
            val prompt = if (item.kind == ItemKind.INTRODUCE) {
                "New phrase. $target. Repeat after me. $target"
            } else {
                "Repeat after me. $target"
            }
            return Turn(item.id, ActivityType.REPEAT, prompt, target, REPEAT_LIMIT_MS, effective)
        }
        return when (effective) {
            LearningMode.SHORT_ANSWER ->
                Turn(item.id, ActivityType.SHORT_ANSWER, item.shortQuestion, item.modelAnswer, SHORT_LIMIT_MS, effective)
            LearningMode.LONG_ANSWER ->
                Turn(item.id, ActivityType.LONG_ANSWER, item.longQuestion, item.modelAnswer, null, effective)
            LearningMode.DETAILED_REVIEW ->
                Turn(item.id, ActivityType.DETAILED_REVIEW, item.shortQuestion, item.modelAnswer, DETAILED_LIMIT_MS, effective)
            else -> error("handled above: $effective")
        }
    }

    private val OPEN_ANSWER_MODES = setOf(LearningMode.SHORT_ANSWER, LearningMode.LONG_ANSWER, LearningMode.DETAILED_REVIEW)
}

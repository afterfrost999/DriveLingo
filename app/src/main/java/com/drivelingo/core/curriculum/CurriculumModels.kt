package com.drivelingo.core.curriculum

import kotlinx.serialization.Serializable

@Serializable
enum class Level(val code: String, val title: String, val cefr: String, val theme: String) {
    L1("L1", "입문", "A1", "인사와 기본 표현"),
    L2("L2", "초급", "A2", "회사의 하루"),
    L3("L3", "중급", "B1", "내 생각 말하기"),
    L4("L4", "상급", "B2", "회의를 이끌기"),
}

enum class CommuteType(val label: String) { MORNING("출근"), EVENING("퇴근") }

/**
 * 수업에서 연습하는 표현 하나.
 * @param korean 우리말 뜻. 아침에 교정된 문장처럼 뜻이 따로 없으면 빈 문자열.
 * @param cue 이 표현이 나오게 하는 상황 질문("…What do you say?"). 짧은 대답 문항에 쓴다.
 * @param partner 롤플레이에서 상대(동료)가 먼저 하는 말. 이 표현이 자연스러운 대답이 되도록 쓴다.
 */
data class Expression(
    val english: String,
    val korean: String,
    val cue: String = "",
    val partner: String = "",
)

enum class ItemKind {
    /** 새 표현 소개 → 마이크 모드에서는 따라 말하기로 이어진다. */
    INTRODUCE,
    PRACTICE,
    REVIEW,
    ROLEPLAY,
    PERSONAL,
    /** 마무리 안내. 항상 듣기. */
    WRAP_UP,
}

/**
 * 수업의 한 단위. 운전 상황에 따라 같은 아이템이 다른 활동(듣기/따라 말하기/짧은 대답/긴 대답)으로 바뀐다.
 * 그래서 아이템은 활동별 문장을 모두 갖는다.
 */
data class LessonItem(
    val id: String,
    val kind: ItemKind,
    val target: Expression,
    val listenScript: String,
    val shortQuestion: String,
    val longQuestion: String,
    val modelAnswer: String,
)

data class LessonPlan(
    val lessonNumber: Int,
    val level: Level,
    val weekNumber: Int,
    val weekTitle: String,
    val dayIndex: Int,
    val commute: CommuteType,
    val topic: String,
    val newExpressions: List<Expression>,
    val reviewExpressions: List<Expression>,
    val items: List<LessonItem>,
    /** 퇴근 복습은 한 단계 느리게. TTS 속도 배율. */
    val speechRate: Float,
) {
    val dayLabel: String get() = DAY_LABELS[dayIndex]
    val title: String get() = "${dayLabel}요일 ${commute.label} 수업"
    val courseLabel: String get() = "${level.code} ${level.title} · ${weekNumber}주차 $weekTitle · $lessonNumber/$LESSONS_PER_WEEK"

    companion object {
        const val LESSONS_PER_WEEK = 10
        val DAY_LABELS = listOf("월", "화", "수", "목", "금")
    }
}

data class CourseWeek(val number: Int, val title: String, val keyPhrase: String)

data class DayPlan(
    val dayLabel: String,
    val morningTopic: String,
    val eveningTopic: String,
    val isUnitRoleplay: Boolean,
)

data class Course(val level: Level, val title: String, val weeks: List<CourseWeek>) {
    val totalLessons: Int get() = weeks.size * LessonPlan.LESSONS_PER_WEEK
}

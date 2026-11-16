package com.drivelingo.core.progress

import com.drivelingo.core.curriculum.CommuteType
import com.drivelingo.core.curriculum.Level
import com.drivelingo.core.driving.Interruption
import com.drivelingo.core.driving.LearningMode
import kotlinx.serialization.Serializable

@Serializable
data class UserSettings(
    val onboarded: Boolean = false,
    val micPermissionGranted: Boolean = false,
    val level: Level = Level.L2,
    /** 출발 시 자동 시작. 자동 재생 정책 검토 전이라 기본값은 꺼짐 (docs/policy-review.md). */
    val autoStartOnDeparture: Boolean = false,
    /** 구간 랭킹은 명시적으로 참여한 사용자만. 기본값 꺼짐. */
    val rankingOptIn: Boolean = false,
)

@Serializable
data class Correction(val original: String, val corrected: String)

/** 한 수업의 누적 기록. 중단 지점 저장과 결과 저장에 같이 쓴다. */
@Serializable
data class LessonTally(
    val words: Int = 0,
    val combo: Int = 0,
    val maxCombo: Int = 0,
    val wordsWhileStopped: Int = 0,
    val turnsByMode: Map<LearningMode, Int> = emptyMap(),
    val pausesByReason: Map<Interruption, Int> = emptyMap(),
    val corrections: List<Correction> = emptyList(),
    val coachFallbacks: Int = 0,
    /** "듣기만 할게"로 진행한 수업. 스트릭은 인정하고 구간 점수(말한 양)는 올리지 않는다. */
    val listenOnly: Boolean = false,
)

@Serializable
data class LessonCheckpoint(
    val level: Level,
    val week: Int,
    val lessonNumber: Int,
    val itemIndex: Int,
    val tally: LessonTally,
)

@Serializable
data class LessonResultRecord(
    val level: Level,
    val week: Int,
    val lessonNumber: Int,
    val title: String,
    val topic: String,
    val commute: CommuteType,
    val newExpressionCount: Int,
    val tally: LessonTally,
    val date: String,
)

@Serializable
data class DailyRecord(
    val date: String,
    val morningDone: Boolean = false,
    val eveningDone: Boolean = false,
    val words: Int = 0,
)

/** 위치·음성 원본은 저장하지 않는다. 말한 양과 꾸준함만 기록한다. */
@Serializable
data class LearningProgress(
    val level: Level = Level.L2,
    val week: Int = 1,
    val completedLessonsInWeek: Int = 0,
    val streakDays: Int = 0,
    val studyDates: List<String> = emptyList(),
    /** 운전한 날. 차를 안 탄 날(재택·휴가)은 스트릭을 끊지 않기 위해 따로 기록한다. */
    val tripDates: List<String> = emptyList(),
    val daily: DailyRecord? = null,
    val corrections: List<Correction> = emptyList(),
    val lastResult: LessonResultRecord? = null,
    val checkpoint: LessonCheckpoint? = null,
    val weeklySegmentWords: Int = 0,
) {
    val nextLessonNumber: Int get() = completedLessonsInWeek + 1
}

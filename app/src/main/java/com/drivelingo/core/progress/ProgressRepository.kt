package com.drivelingo.core.progress

import com.drivelingo.core.curriculum.CommuteType
import com.drivelingo.core.curriculum.Level
import com.drivelingo.core.curriculum.LessonPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * 사용자 설정과 학습 진도. 앱 전체가 공유한다(홈·수업·리포트·코스).
 *
 * 쓰기는 즉시 메모리에 반영하고 저장소에는 비동기로 쓴다. ViewModel 이 정리되는 중에도 호출할 수 있도록
 * suspend 함수로 만들지 않았다.
 */
class ProgressRepository(
    private val store: KeyValueStore,
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val json = Json { ignoreUnknownKeys = true; allowStructuredMapKeys = true }

    private val _settings = MutableStateFlow(load(KEY_SETTINGS) ?: UserSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private val _progress = MutableStateFlow(load(KEY_PROGRESS) ?: MockSeed.progress(today()))
    val progress: StateFlow<LearningProgress> = _progress.asStateFlow()

    fun todayString(): String = today().toString()

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        _settings.value = transform(_settings.value)
        save(KEY_SETTINGS, _settings.value)
    }

    fun completeOnboarding(level: Level, micGranted: Boolean, rankingOptIn: Boolean) {
        updateSettings { it.copy(onboarded = true, level = level, micPermissionGranted = micGranted, rankingOptIn = rankingOptIn) }
        if (_progress.value.level != level) changeLevel(level)
    }

    /** 레벨을 바꾸면 해당 레벨 1주차부터. 스트릭과 교정 기록은 유지한다. */
    fun changeLevel(level: Level) {
        updateSettings { it.copy(level = level) }
        updateProgress { it.copy(level = level, week = 1, completedLessonsInWeek = 0, checkpoint = null) }
    }

    fun saveCheckpoint(checkpoint: LessonCheckpoint) = updateProgress { it.copy(checkpoint = checkpoint) }

    fun clearCheckpoint() = updateProgress { it.copy(checkpoint = null) }

    /** 운전한 날 기록(스트릭 판단용). 위치·거리·시간은 저장하지 않는다. */
    fun recordTripToday() {
        val date = todayString()
        if (date !in _progress.value.tripDates) {
            updateProgress { it.copy(tripDates = (it.tripDates + date).takeLast(MAX_DATES)) }
        }
    }

    fun completeLesson(result: LessonResultRecord) = updateProgress { current ->
        val date = today()
        val dateString = date.toString()
        val newStreak = StreakRules.afterStudy(
            streak = current.streakDays,
            studyDates = current.studyDates.map(LocalDate::parse).toSet(),
            tripDates = current.tripDates.map(LocalDate::parse).toSet(),
            today = date,
        )
        val daily = current.daily?.takeIf { it.date == dateString } ?: DailyRecord(dateString)
        val updatedDaily = daily.copy(
            morningDone = daily.morningDone || result.commute == CommuteType.MORNING,
            eveningDone = daily.eveningDone || result.commute == CommuteType.EVENING,
            words = daily.words + result.tally.words,
        )
        val finishedWeek = current.completedLessonsInWeek + 1 >= LessonPlan.LESSONS_PER_WEEK
        current.copy(
            week = if (finishedWeek) (current.week + 1).coerceAtMost(4) else current.week,
            completedLessonsInWeek = if (finishedWeek) 0 else current.completedLessonsInWeek + 1,
            streakDays = newStreak,
            studyDates = (current.studyDates + dateString).distinct().takeLast(MAX_DATES),
            tripDates = (current.tripDates + dateString).distinct().takeLast(MAX_DATES),
            daily = updatedDaily,
            corrections = (result.tally.corrections + current.corrections).distinct().take(MAX_CORRECTIONS),
            lastResult = result,
            checkpoint = null,
            // 듣기만 한 수업은 구간 점수를 받지 않는다(UX 기획서 9장 "동승자").
            weeklySegmentWords = current.weeklySegmentWords + if (result.tally.listenOnly) 0 else result.tally.words,
        )
    }

    /** 프로토타입 데이터 초기화(설정 화면). */
    fun resetAll() {
        store.clear()
        _settings.value = UserSettings()
        _progress.value = MockSeed.progress(today())
    }

    private fun updateProgress(transform: (LearningProgress) -> LearningProgress) {
        _progress.value = transform(_progress.value)
        save(KEY_PROGRESS, _progress.value)
    }

    private inline fun <reified T> load(key: String): T? =
        store.read(key)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    private inline fun <reified T> save(key: String, value: T) = store.write(key, json.encodeToString(value))

    private companion object {
        const val KEY_SETTINGS = "settings"
        const val KEY_PROGRESS = "progress"
        const val MAX_DATES = 60
        const val MAX_CORRECTIONS = 20
    }
}

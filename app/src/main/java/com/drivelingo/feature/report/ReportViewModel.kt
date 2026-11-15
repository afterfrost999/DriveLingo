package com.drivelingo.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelingo.app.AppContainer
import com.drivelingo.core.curriculum.CommuteType
import com.drivelingo.core.driving.DrivingStatus
import com.drivelingo.core.progress.Correction
import com.drivelingo.core.progress.DayMark
import com.drivelingo.core.progress.LearningProgress
import com.drivelingo.core.progress.MockRanking
import com.drivelingo.core.progress.StreakRules
import com.drivelingo.core.progress.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ReportUiState(
    val hasResult: Boolean,
    val title: String = "",
    val subtitle: String = "",
    val words: Int = 0,
    val maxCombo: Int = 0,
    val wordsWhileStopped: Int = 0,
    val drivingLine: String = "",
    val pauseLine: String? = null,
    val corrections: List<Correction> = emptyList(),
    val nextLabel: String = "",
    val nextText: String = "",
    val streakDays: Int = 0,
    val weekMarks: List<DayMark> = emptyList(),
    val rankingOptIn: Boolean = false,
    val ranking: MockRanking.Board? = null,
    /** 주행 규제 중이면 큰 숫자 두 개만 보여 준다(LIMIT_CONTENT). */
    val uiRestricted: Boolean = false,
    val playingText: String? = null,
)

class ReportViewModel(private val container: AppContainer) : ViewModel() {

    private val playing = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    val uiState: StateFlow<ReportUiState> = combine(
        container.progress.progress, container.progress.settings, container.drivingMonitor.status, playing,
    ) { progress, settings, driving, playingText -> build(progress, settings, driving, playingText) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, build(
            container.progress.progress.value, container.progress.settings.value,
            container.drivingMonitor.status.value, null,
        ))

    /** 교정된 표현 다시 듣기. TTS 는 Mock 이라 소리 없이 재생 중 표시만 한다. */
    fun play(text: String) {
        viewModelScope.launch {
            playing.value = text
            container.voiceOutput.speak(text)
            playing.value = null
        }
    }

    private fun build(progress: LearningProgress, settings: UserSettings, driving: DrivingStatus, playingText: String?): ReportUiState {
        val result = progress.lastResult ?: return ReportUiState(hasResult = false, uiRestricted = driving.uiRestricted)
        val tally = result.tally
        val morning = result.commute == CommuteType.MORNING
        val today = LocalDate.parse(container.progress.todayString())
        val week = container.curriculum.course(result.level).weeks.getOrNull(result.week - 1)
        return ReportUiState(
            hasResult = true,
            title = "${result.commute.label} 수업 완료!",
            subtitle = buildString {
                append("${result.level.code} · ${week?.title ?: result.topic} ${result.lessonNumber}/10")
                if (result.newExpressionCount > 0) append(" · 새 표현 ${result.newExpressionCount}개")
            },
            words = tally.words,
            maxCombo = tally.maxCombo,
            wordsWhileStopped = tally.wordsWhileStopped,
            drivingLine = ReportNarrator.drivingLine(tally),
            pauseLine = ReportNarrator.pauseLine(tally),
            corrections = tally.corrections.distinct().take(3),
            nextLabel = if (morning) "오늘 수업 1/2 · 다음은 퇴근 수업" else "오늘 수업 2/2 · 내일 아침에 만나요",
            nextText = if (morning) "저녁엔 오늘 표현 ${result.newExpressionCount}개를 다시 써 봐요" else "오늘 고친 표현은 내일 수업에 한 번 더 나와요",
            streakDays = progress.streakDays,
            weekMarks = StreakRules.weekMarks(today, progress.studyDates.map(LocalDate::parse).toSet()),
            rankingOptIn = settings.rankingOptIn,
            ranking = if (settings.rankingOptIn) MockRanking.board(progress.weeklySegmentWords) else null,
            uiRestricted = driving.uiRestricted,
            playingText = playingText,
        )
    }
}

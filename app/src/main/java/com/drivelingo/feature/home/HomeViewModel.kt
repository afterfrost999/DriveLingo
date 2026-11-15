package com.drivelingo.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelingo.app.AppContainer
import com.drivelingo.core.curriculum.CommuteType
import com.drivelingo.core.driving.DrivingStatus
import com.drivelingo.core.progress.LearningProgress
import com.drivelingo.core.progress.MockRanking
import com.drivelingo.core.progress.UserSettings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val streakDays: Int,
    val commute: CommuteType,
    val subheader: String,
    val headline: String,
    val courseLabel: String,
    val lessonTitle: String,
    val lessonSummary: String,
    val reviewExpressions: List<String>,
    val canResume: Boolean,
    val rankingOptIn: Boolean,
    val segment: String,
    val rankLine: String,
    val footnote: String,
    /** 주행 규제 중: 숫자·부가 정보를 숨기고 시작 버튼만 남긴다. */
    val uiRestricted: Boolean,
    val hasReport: Boolean,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _autoStart = Channel<Unit>(Channel.CONFLATED)
    /** 사용자가 "출발 시 자동 시작"을 명시적으로 켠 경우에만 발생한다. */
    val autoStartEvents: Flow<Unit> = _autoStart.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        container.progress.progress,
        container.progress.settings,
        container.drivingMonitor.status,
    ) { progress, settings, driving -> buildState(progress, settings, driving) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, buildState(
            container.progress.progress.value, container.progress.settings.value, container.drivingMonitor.status.value,
        ))

    init {
        viewModelScope.launch {
            var wasParked = container.drivingMonitor.status.value.isParked
            container.drivingMonitor.status.collect { status ->
                val departed = wasParked && status.uiRestricted && status.signals.ignitionOn
                wasParked = status.isParked
                if (departed && container.progress.settings.value.autoStartOnDeparture) _autoStart.trySend(Unit)
            }
        }
    }

    private fun buildState(progress: LearningProgress, settings: UserSettings, driving: DrivingStatus): HomeUiState {
        val plan = container.curriculum.lessonPlan(progress.level, progress.week, progress.nextLessonNumber)
        val eta = driving.signals.minutesToArrival
        val morning = plan.commute == CommuteType.MORNING
        val board = MockRanking.board(progress.weeklySegmentWords)
        val checkpoint = progress.checkpoint?.takeIf {
            it.level == progress.level && it.week == progress.week && it.lessonNumber == progress.nextLessonNumber
        }

        val subheader = buildString {
            if (eta != null) append("도착까지 ${eta}분 · ")
            append(if (morning) "오늘 아침 수업은 10분이에요" else "저녁 수업은 10분이에요")
        }
        val headline = if (morning) "오늘은 ${plan.topic},\n같이 해볼까요?" else "오늘 회의에서\n아침 표현 써 봤어요?"
        val summary = if (morning) {
            listOfNotNull(
                "새 표현 ${plan.newExpressions.size}개",
                if (plan.reviewExpressions.isNotEmpty()) "어제 표현 꺼내기" else null,
                "롤플레이",
            ).joinToString(" · ")
        } else {
            "새 표현 없음 · 말 속도 한 단계 느리게 · \"오늘 하루 어땠어요?\" 대화"
        }
        val footnote = when {
            !morning -> "퇴근길은 복습만 해요 · 아침보다 한 단계 느리게 말해요"
            settings.autoStartOnDeparture -> "그냥 출발해도 자동으로 시작해요 · 출발하면 숫자는 화면에서 사라져요"
            else -> "시작 버튼을 누르면 시작해요 · 출발하면 숫자는 화면에서 사라져요"
        }

        return HomeUiState(
            streakDays = progress.streakDays,
            commute = plan.commute,
            subheader = subheader,
            headline = headline,
            courseLabel = plan.courseLabel,
            lessonTitle = if (morning) "${plan.title} · 10분" else "${plan.title} · 아침 표현 다시 쓰기",
            lessonSummary = summary,
            reviewExpressions = if (morning) emptyList() else plan.reviewExpressions.map { it.english },
            canResume = checkpoint != null && checkpoint.itemIndex > 0,
            rankingOptIn = settings.rankingOptIn,
            segment = "오늘 구간 · ${board.segment}",
            rankLine = "이번 주 ${board.myRank}위 · 마스터까지 ${board.wordsToMaster}단어",
            footnote = footnote,
            uiRestricted = driving.uiRestricted,
            hasReport = progress.lastResult != null,
        )
    }
}

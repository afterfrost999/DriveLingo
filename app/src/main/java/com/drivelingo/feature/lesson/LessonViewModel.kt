package com.drivelingo.feature.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelingo.app.AppContainer
import com.drivelingo.core.curriculum.CommuteType
import com.drivelingo.core.driving.Interruption
import com.drivelingo.feature.lesson.engine.LessonEvent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 수업 화면 상태 관리. 수업 진행 규칙은 엔진에, 실행은 [LessonSession] 에 있고
 * 여기서는 세션을 만들고 화면 상태로 바꾸고 사용자 입력을 전달만 한다.
 * 세션이 ViewModel 에 있으므로 화면 회전·리사이즈에도 수업이 끊기지 않는다.
 */
class LessonViewModel(private val container: AppContainer) : ViewModel() {

    private val session: LessonSession

    val uiState: StateFlow<LessonUiState>

    init {
        val progress = container.progress.progress.value
        val lessonNumber = progress.nextLessonNumber
        val checkpoint = progress.checkpoint?.takeIf {
            it.level == progress.level && it.week == progress.week && it.lessonNumber == lessonNumber
        }
        // 직전(아침) 수업에서 교정된 표현은 퇴근 복습에 한 번 더 나온다.
        val corrections = progress.lastResult
            ?.takeIf { it.commute == CommuteType.MORNING && it.lessonNumber == lessonNumber - 1 }
            ?.tally?.corrections?.map { it.corrected }.orEmpty()
        val plan = container.curriculum.lessonPlan(progress.level, progress.week, lessonNumber, corrections)

        session = LessonSession(
            plan = plan,
            checkpoint = checkpoint,
            drivingMonitor = container.drivingMonitor,
            voiceOutput = container.voiceOutput,
            voiceInput = container.voiceInput,
            coach = container.coach,
            audioFocus = container.audioFocus,
            progress = container.progress,
            scope = viewModelScope,
            clock = container.clock,
        )

        uiState = combine(session.state, container.drivingMonitor.status, LessonUiMapper::map)
            .stateIn(
                viewModelScope, SharingStarted.Eagerly,
                LessonUiMapper.map(session.state.value, container.drivingMonitor.status.value),
            )

        session.start()

        // 시동이 꺼지면(운행 종료) 중단 지점을 저장하고 멈춘다.
        viewModelScope.launch {
            container.drivingMonitor.status.map { it.signals.ignitionOn }.distinctUntilChanged().filter { !it }.first()
            session.stop()
        }
    }

    fun togglePause() {
        val state = session.state.value
        val needsResume = state.userPaused || Interruption.AUDIO_FOCUS_LOST in state.signalInterruptions
        session.send(if (needsResume) LessonEvent.UserResume(container.clock()) else LessonEvent.UserPause)
    }

    fun repeat() = session.send(LessonEvent.Repeat)

    /** 화면을 나갈 때(뒤로 가기). 중단 지점을 저장한다. */
    fun leave() {
        if (!session.state.value.isTerminal) session.stop()
    }

    override fun onCleared() {
        leave()
    }
}

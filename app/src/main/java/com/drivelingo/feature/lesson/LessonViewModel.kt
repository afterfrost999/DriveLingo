package com.drivelingo.feature.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelingo.app.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * 수업 화면 상태 관리. 수업 진행 규칙은 엔진에, 실행은 [LessonSession] 에, 소유는 [LessonController] 에 있고
 * 여기서는 진행 중인 수업에 붙어 화면 상태로 바꾸고 사용자 입력을 전달만 한다.
 * 수업이 앱 수명에 있으므로 화면 회전·리사이즈에도, 운전대 버튼으로 조작해도 같은 수업이 이어진다.
 */
class LessonViewModel(container: AppContainer) : ViewModel() {

    private val lessons = container.lessons

    /** 화면을 연 시점에 진행 중이던 수업(없으면 새로 시작한 수업). */
    private val session: LessonSession = lessons.startOrAttach()

    val uiState: StateFlow<LessonUiState> =
        combine(session.state, container.drivingMonitor.status, session.listening, LessonUiMapper::map)
            .stateIn(
                viewModelScope, SharingStarted.Eagerly,
                LessonUiMapper.map(session.state.value, container.drivingMonitor.status.value),
            )

    fun togglePause() = lessons.togglePause()

    fun repeat() = lessons.repeat()

    /** 화면을 나갈 때(뒤로 가기). 중단 지점을 저장한다. */
    fun leave() = lessons.stop(only = session)

    override fun onCleared() {
        leave()
    }
}

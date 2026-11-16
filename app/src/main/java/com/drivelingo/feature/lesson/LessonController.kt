package com.drivelingo.feature.lesson

import com.drivelingo.core.audio.EarconPlayer
import com.drivelingo.core.curriculum.CommuteType
import com.drivelingo.core.curriculum.CurriculumRepository
import com.drivelingo.core.curriculum.LessonPlan
import com.drivelingo.core.driving.DrivingMonitor
import com.drivelingo.core.driving.Interruption
import com.drivelingo.core.progress.ProgressRepository
import com.drivelingo.core.speech.VoiceInput
import com.drivelingo.core.speech.VoiceOutput
import com.drivelingo.core.vehicle.AudioFocusRequester
import com.drivelingo.feature.lesson.coach.EnglishCoach
import com.drivelingo.feature.lesson.engine.LessonEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 앱 전체에서 수업 하나를 소유한다.
 *
 * 수업 화면(LessonViewModel)뿐 아니라 미디어 세션(운전대 재생·일시정지, 차량 미디어 화면)도 같은 수업을
 * 조작해야 하므로, 세션을 화면이 아니라 앱 수명에 둔다. 화면은 [startOrAttach] 로 붙기만 한다.
 *
 * 안전 규칙: 수업(소리·마이크)은 사용자가 시작 버튼이나 운전대 재생 버튼을 눌렀을 때만 시작한다.
 * 이 클래스는 스스로 수업을 시작하지 않는다.
 */
class LessonController(
    private val drivingMonitor: DrivingMonitor,
    private val voiceOutput: VoiceOutput,
    private val voiceInput: VoiceInput,
    private val earcons: EarconPlayer,
    private val coach: EnglishCoach,
    private val audioFocus: AudioFocusRequester,
    private val curriculum: CurriculumRepository,
    private val progress: ProgressRepository,
    private val appScope: CoroutineScope,
    private val clock: () -> Long,
) {
    private val _session = MutableStateFlow<LessonSession?>(null)
    /** 가장 최근 수업. 끝난 수업도 결과 화면이 볼 수 있도록 다음 수업을 시작할 때까지 남겨 둔다. */
    val session: StateFlow<LessonSession?> = _session.asStateFlow()

    private var sessionScope: CoroutineScope? = null

    /** 진행 중인 수업(끝나지 않은 것). */
    private val active: LessonSession? get() = _session.value?.takeIf { !it.state.value.isTerminal }

    /** 진행 중인 수업이 있으면 그 수업에 붙고, 없으면 다음 수업을 시작한다. */
    @Synchronized
    fun startOrAttach(): LessonSession {
        active?.let { return it }
        sessionScope?.cancel()

        val p = progress.progress.value
        val lessonNumber = p.nextLessonNumber
        val checkpoint = p.checkpoint?.takeIf {
            it.level == p.level && it.week == p.week && it.lessonNumber == lessonNumber
        }
        val scope = CoroutineScope(appScope.coroutineContext + SupervisorJob(appScope.coroutineContext[Job]))
        val session = LessonSession(
            plan = nextPlan(),
            checkpoint = checkpoint,
            drivingMonitor = drivingMonitor,
            voiceOutput = voiceOutput,
            voiceInput = voiceInput,
            earcons = earcons,
            coach = coach,
            audioFocus = audioFocus,
            progress = progress,
            scope = scope,
            clock = clock,
        )
        sessionScope = scope
        _session.value = session
        session.start()

        // 시동이 꺼지면(운행 종료) 중단 지점을 저장하고 멈춘다.
        scope.launch {
            drivingMonitor.status.map { it.signals.ignitionOn }.distinctUntilChanged().filter { !it }.first()
            stop(session)
        }
        // 끝나면 세션의 작업(신호 구독·타이머)을 정리한다. 최종 상태는 [session] 으로 계속 볼 수 있다.
        scope.launch {
            session.state.first { it.isTerminal }
            scope.cancel()
        }
        return session
    }

    /** 다음에 시작할 수업. 직전(아침) 수업에서 교정된 표현은 퇴근 복습에 한 번 더 나온다. */
    fun nextPlan(): LessonPlan {
        val p = progress.progress.value
        val lessonNumber = p.nextLessonNumber
        val corrections = p.lastResult
            ?.takeIf { it.commute == CommuteType.MORNING && it.lessonNumber == lessonNumber - 1 }
            ?.tally?.corrections?.map { it.corrected }.orEmpty()
        return curriculum.lessonPlan(p.level, p.week, lessonNumber, corrections)
    }

    /** 수업을 멈추고 중단 지점을 저장한다. [only] 를 주면 그 수업이 지금 수업일 때만 멈춘다. */
    @Synchronized
    fun stop(only: LessonSession? = null) {
        val current = _session.value ?: return
        if (only != null && only !== current) return
        if (!current.state.value.isTerminal) current.stop()
        sessionScope?.cancel()
        sessionScope = null
    }

    /** 화면의 재생/일시정지 버튼. 사용자 일시정지나 포커스 영구 상실이면 재개, 아니면 일시정지. */
    fun togglePause() {
        val session = active ?: return
        val state = session.state.value
        val needsResume = state.userPaused || Interruption.AUDIO_FOCUS_LOST in state.signalInterruptions
        session.send(if (needsResume) LessonEvent.UserResume(clock()) else LessonEvent.UserPause)
    }

    /** 운전대·미디어 화면의 일시정지. 이미 멈춰 있으면 엔진이 무시한다. */
    fun pause() {
        active?.send(LessonEvent.UserPause)
    }

    /** 운전대·미디어 화면의 재생. 진행 중인 수업이 있으면 재개하고, 없으면 사용자가 누른 것이므로 새로 시작한다. */
    fun play() {
        val session = active
        if (session == null) startOrAttach() else session.send(LessonEvent.UserResume(clock()))
    }

    fun repeat() {
        active?.send(LessonEvent.Repeat)
    }
}

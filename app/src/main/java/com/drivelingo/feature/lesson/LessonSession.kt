package com.drivelingo.feature.lesson

import android.util.Log
import com.drivelingo.core.audio.EarconPlayer
import com.drivelingo.core.curriculum.LessonPlan
import com.drivelingo.core.driving.DrivingMonitor
import com.drivelingo.core.driving.InterruptionDetector
import com.drivelingo.core.progress.LessonCheckpoint
import com.drivelingo.core.progress.LessonResultRecord
import com.drivelingo.core.progress.ProgressRepository
import com.drivelingo.core.speech.AnswerStyle
import com.drivelingo.core.speech.ListenRequest
import com.drivelingo.core.speech.ListenResult
import com.drivelingo.core.speech.VoiceInput
import com.drivelingo.core.speech.VoiceOutput
import com.drivelingo.core.vehicle.AudioFocusRequester
import com.drivelingo.feature.lesson.coach.CoachRequest
import com.drivelingo.feature.lesson.coach.EnglishCoach
import com.drivelingo.feature.lesson.engine.ActivityType
import com.drivelingo.feature.lesson.engine.LessonEffect
import com.drivelingo.feature.lesson.engine.LessonEngine
import com.drivelingo.feature.lesson.engine.LessonEvent
import com.drivelingo.feature.lesson.engine.LessonState
import com.drivelingo.feature.lesson.engine.Turn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 수업 한 번의 실행기. 엔진(순수 로직)과 바깥 세계(TTS·STT·AI·차량 신호·저장소)를 연결한다.
 *
 * - 모든 이벤트는 하나의 채널에서 순서대로 처리한다 → 상태 경쟁이 없다.
 * - 음성 작업은 한 번에 하나([audioJob])만 돌린다 → TTS 재생 중에는 STT 가 열리지 않는다.
 */
class LessonSession(
    private val plan: LessonPlan,
    checkpoint: LessonCheckpoint?,
    private val drivingMonitor: DrivingMonitor,
    private val voiceOutput: VoiceOutput,
    private val voiceInput: VoiceInput,
    private val earcons: EarconPlayer,
    private val coach: EnglishCoach,
    private val audioFocus: AudioFocusRequester,
    private val progress: ProgressRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) {
    private val events = Channel<LessonEvent>(Channel.UNLIMITED)

    private val _state = MutableStateFlow(
        LessonEngine.initial(
            plan = plan,
            mode = drivingMonitor.status.value.mode,
            startItemIndex = checkpoint?.itemIndex ?: 0,
            tally = checkpoint?.tally ?: com.drivelingo.core.progress.LessonTally(),
            offline = !drivingMonitor.status.value.signals.networkAvailable,
        ),
    )
    val state: StateFlow<LessonState> = _state.asStateFlow()

    private val _listening = MutableStateFlow<ListenWindow?>(null)

    /** 마이크가 열려 있는 동안의 대답 시간. 효과음과 에코 가드가 끝나 실제로 듣기 시작할 때부터다. */
    val listening: StateFlow<ListenWindow?> = _listening.asStateFlow()

    private var audioJob: Job? = null
    private var coachJob: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch { for (event in events) dispatch(event) }

        val status = drivingMonitor.status
        scope.launch { status.map { it.mode }.distinctUntilChanged().collect { send(LessonEvent.SituationChanged(it)) } }
        scope.launch {
            status.map { InterruptionDetector.detect(it.signals) }.distinctUntilChanged()
                .collect { send(LessonEvent.InterruptionsChanged(it, clock())) }
        }
        scope.launch {
            status.map { it.signals.networkAvailable }.distinctUntilChanged().collect { send(LessonEvent.NetworkChanged(it)) }
        }
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                send(LessonEvent.Tick(clock()))
            }
        }
        send(LessonEvent.Start(clock()))
    }

    fun send(event: LessonEvent) {
        events.trySend(event)
    }

    /** 화면을 떠나거나 운행이 끝날 때. 중단 지점을 저장한다. */
    fun stop() {
        // 채널 처리 루프가 이미 취소됐을 수 있으므로 직접 처리한다.
        dispatch(LessonEvent.Stop)
        events.close()
    }

    @Synchronized
    private fun dispatch(event: LessonEvent) {
        val transition = LessonEngine.reduce(_state.value, event)
        _state.value = transition.state
        transition.effects.forEach(::execute)
    }

    private fun execute(effect: LessonEffect) {
        when (effect) {
            is LessonEffect.Speak -> {
                audioJob?.cancel()
                audioJob = scope.launch {
                    effect.cue?.let { earcons.play(it) }
                    voiceOutput.speak(effect.text, effect.rate)
                    send(LessonEvent.SpeechFinished(effect.token))
                }
            }
            is LessonEffect.Listen -> {
                audioJob?.cancel()
                voiceOutput.stop()
                audioJob = scope.launch {
                    // '띵'이 끝난 뒤 에코 가드만큼 기다리고 마이크를 연다(STT 가 효과음을 듣지 않도록).
                    effect.cue?.let { earcons.play(it) }
                    delay(effect.echoGuardMs)
                    val window = ListenWindow(effect.token, effect.turn.answerLimitMs)
                    _listening.value = window
                    val result = try {
                        voiceInput.listen(effect.turn.toListenRequest())
                    } finally {
                        _listening.compareAndSet(window, null)
                    }
                    send(
                        when (result) {
                            is ListenResult.Recognized -> LessonEvent.AnswerRecognized(effect.token, result.text)
                            ListenResult.NoSpeech -> LessonEvent.AnswerFailed(effect.token, noSpeech = true)
                            is ListenResult.Failed -> LessonEvent.AnswerFailed(effect.token, noSpeech = false)
                        },
                    )
                }
            }
            is LessonEffect.PlayEarcon -> {
                audioJob?.cancel()
                audioJob = scope.launch { earcons.play(effect.earcon) }
            }
            LessonEffect.StopAudio -> {
                audioJob?.cancel()
                coachJob?.cancel()
                voiceOutput.stop()
                voiceInput.cancel()
            }
            is LessonEffect.RequestFeedback -> {
                coachJob?.cancel()
                coachJob = scope.launch {
                    val request = CoachRequest(
                        level = plan.level, itemId = effect.turn.itemId, activity = effect.turn.activity,
                        question = effect.turn.prompt, expectedAnswer = effect.turn.expectedAnswer, answer = effect.answer,
                    )
                    val reply = withTimeoutOrNull(COACH_TIMEOUT_MS) {
                        runCatching { coach.feedback(request) }
                            .onFailure { Log.w(TAG, "coach failed", it) }
                            .getOrNull()
                    }
                    send(if (reply != null) LessonEvent.CoachReplied(effect.token, reply) else LessonEvent.CoachFailed(effect.token))
                }
            }
            LessonEffect.RequestAudioFocus -> audioFocus.requestAudioFocus()
            LessonEffect.AbandonAudioFocus -> audioFocus.abandonAudioFocus()
            is LessonEffect.SaveCheckpoint -> progress.saveCheckpoint(
                LessonCheckpoint(plan.level, plan.weekNumber, plan.lessonNumber, effect.itemIndex, effect.tally),
            )
            is LessonEffect.Finish -> progress.completeLesson(
                LessonResultRecord(
                    level = plan.level, week = plan.weekNumber, lessonNumber = plan.lessonNumber,
                    title = plan.title, topic = plan.topic, commute = plan.commute,
                    newExpressionCount = plan.newExpressions.size, tally = effect.tally,
                    date = progress.todayString(),
                ),
            )
        }
    }

    private fun Turn.toListenRequest() = ListenRequest(
        style = when (activity) {
            ActivityType.REPEAT -> AnswerStyle.REPEAT
            ActivityType.LONG_ANSWER -> AnswerStyle.LONG
            else -> AnswerStyle.SHORT
        },
        maxDurationMs = answerLimitMs,
        itemId = itemId,
        expected = expectedAnswer,
    )

    private companion object {
        const val TAG = "LessonSession"
        const val TICK_MS = 250L
        /** AI 응답 대기 한도. 넘기면 로컬 피드백으로 넘어간다. */
        const val COACH_TIMEOUT_MS = 6_000L
    }
}

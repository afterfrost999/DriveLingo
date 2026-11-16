package com.drivelingo.feature.lesson.engine

import com.drivelingo.core.audio.Earcon
import com.drivelingo.core.curriculum.LessonPlan
import com.drivelingo.core.driving.Interruption
import com.drivelingo.core.driving.LearningMode
import com.drivelingo.core.progress.Correction
import com.drivelingo.core.progress.LessonTally
import com.drivelingo.core.speech.command.VoiceCommand

/** 한 턴에서 학습자가 하는 활동. 같은 아이템도 운전 상황에 따라 활동이 달라진다. */
enum class ActivityType { LISTEN, REPEAT, SHORT_ANSWER, LONG_ANSWER, DETAILED_REVIEW }

/**
 * 진행 중인 턴. 턴 시작 시점의 학습 모드로 정해지고, 턴이 끝날 때까지 바뀌지 않는다.
 * (모드 변경은 턴 경계에서 적용한다. 학습자의 긴 대답을 중간에 끊지 않기 위함.)
 */
data class Turn(
    val itemId: String,
    val activity: ActivityType,
    val prompt: String,
    val expectedAnswer: String,
    /** null = 시간 제한 없음. */
    val answerLimitMs: Long?,
    val mode: LearningMode,
) {
    val expectsAnswer: Boolean get() = activity != ActivityType.LISTEN
}

/** AI 코치(또는 로컬 대체 로직)의 응답. [recast] 는 학습자 문장을 자연스럽게 고친 것. */
data class CoachFeedback(
    val reply: String,
    val correct: Boolean,
    val recast: Correction? = null,
)

enum class LessonPhase {
    /** 아직 시작 전. */
    READY,
    /** 코치가 질문·안내를 말하는 중 (화면: 듣는 중). */
    PROMPT,
    /** 학습자가 말할 차례 (화면: 말할 차례 / 길게 말해 보세요). */
    ANSWER,
    /** AI 코치 응답 대기. */
    THINKING,
    /** 코치가 피드백(리캐스트)을 말하는 중. */
    FEEDBACK,
    /** 출발 직후·도착 직전처럼 말하지 않는 구간. 상황이 풀리면 이어서 한다. */
    QUIET,
    /** 즉시 중단 이벤트로 멈춤. 진행 상황은 유지. */
    PAUSED,
    /** 마무리 인사. */
    CLOSING,
    FINISHED,
    /** 운행 종료 등으로 중단. 중단 지점이 저장된다. */
    STOPPED,
}

data class LessonState(
    val plan: LessonPlan,
    val phase: LessonPhase = LessonPhase.READY,
    val itemIndex: Int = 0,
    val turn: Turn? = null,
    val retries: Int = 0,
    val lastAnswer: String? = null,
    val feedback: CoachFeedback? = null,
    /** 가장 최근에 판단된 학습 모드. 다음 턴부터 적용된다. */
    val mode: LearningMode,
    val signalInterruptions: Set<Interruption> = emptySet(),
    val userPaused: Boolean = false,
    /** 중단 이벤트가 끝난 뒤 이 시각 전에는 재개하지 않는다. */
    val resumeNotBeforeMs: Long = 0,
    val pausedFrom: LessonPhase? = null,
    val offline: Boolean = false,
    /**
     * 음성 요청마다 1씩 증가. 멈춤·건너뛰기 후 늦게 도착한 콜백(이전 TTS 완료, 이전 STT 결과)을 버리는 데 쓴다.
     */
    val audioToken: Int = 0,
    val tally: LessonTally = LessonTally(),
    /** 말 속도 단계. 0 = 수업 기본 속도, 클수록 느리게("천천히" 명령). */
    val rateStep: Int = 0,
    /** "듣기만 할게": 이번 수업은 마이크를 쓰지 않는다(동승자). */
    val listenOnly: Boolean = false,
    /** 이번 턴에서 받은 음성 명령 수. 잡음이 명령으로 계속 잡혀 같은 턴에 갇히지 않도록 센다. */
    val commandsThisTurn: Int = 0,
) {
    val activeInterruptions: Set<Interruption>
        get() = if (userPaused) signalInterruptions + Interruption.USER_PAUSE else signalInterruptions

    val progress: Float
        get() = if (plan.items.isEmpty()) 1f else (itemIndex.toFloat() / plan.items.size).coerceIn(0f, 1f)

    val isTerminal: Boolean get() = phase == LessonPhase.FINISHED || phase == LessonPhase.STOPPED
}

sealed interface LessonEvent {
    data class Start(val nowMs: Long) : LessonEvent
    data class SituationChanged(val mode: LearningMode) : LessonEvent
    data class InterruptionsChanged(val active: Set<Interruption>, val nowMs: Long) : LessonEvent
    data class NetworkChanged(val online: Boolean) : LessonEvent
    data class Tick(val nowMs: Long) : LessonEvent
    data class SpeechFinished(val token: Int) : LessonEvent
    data class AnswerRecognized(val token: Int, val text: String) : LessonEvent
    data class AnswerFailed(val token: Int, val noSpeech: Boolean) : LessonEvent
    data class CoachReplied(val token: Int, val feedback: CoachFeedback) : LessonEvent
    data class CoachFailed(val token: Int) : LessonEvent
    data object UserPause : LessonEvent
    data class UserResume(val nowMs: Long) : LessonEvent
    data object Repeat : LessonEvent
    /** 운전대·화면에서 온 명령(예: 운전대 "다음"). 음성 명령은 [AnswerRecognized] 안에서 엔진이 직접 알아듣는다. */
    data class Command(val command: VoiceCommand) : LessonEvent
    data object Stop : LessonEvent
}

/** 엔진이 바깥(TTS·STT·AI·저장소)에 요청하는 일. 실행은 LessonSession 이 한다. */
sealed interface LessonEffect {
    /** [cue] 가 있으면 효과음을 먼저 내고 말한다. */
    data class Speak(val token: Int, val text: String, val rate: Float, val cue: Earcon? = null) : LessonEffect
    /**
     * [cue](말할 차례 '띵')를 낸 뒤 [echoGuardMs] 만큼 기다리고 마이크를 연다.
     * TTS 꼬리 소리나 효과음을 STT 가 다시 인식하지 않도록.
     */
    data class Listen(val token: Int, val turn: Turn, val echoGuardMs: Long, val cue: Earcon? = Earcon.YourTurn) : LessonEffect
    /** 말 없이 효과음만 낸다(멈춤 '톡'). */
    data class PlayEarcon(val earcon: Earcon) : LessonEffect
    data object StopAudio : LessonEffect
    data class RequestFeedback(val token: Int, val turn: Turn, val answer: String) : LessonEffect
    data object RequestAudioFocus : LessonEffect
    data object AbandonAudioFocus : LessonEffect
    data class SaveCheckpoint(val itemIndex: Int, val tally: LessonTally) : LessonEffect
    data class Finish(val tally: LessonTally) : LessonEffect
}

data class Transition(val state: LessonState, val effects: List<LessonEffect> = emptyList())

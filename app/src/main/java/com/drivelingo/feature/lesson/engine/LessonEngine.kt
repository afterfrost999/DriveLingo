package com.drivelingo.feature.lesson.engine

import com.drivelingo.core.audio.Earcon
import com.drivelingo.core.curriculum.CommuteType
import com.drivelingo.core.curriculum.ItemKind
import com.drivelingo.core.curriculum.LessonPlan
import com.drivelingo.core.driving.Interruption
import com.drivelingo.core.driving.LearningMode
import com.drivelingo.core.progress.LessonTally
import com.drivelingo.core.speech.command.VoiceCommand
import com.drivelingo.core.speech.command.VoiceCommandParser
import com.drivelingo.feature.lesson.engine.LessonEffect.AbandonAudioFocus
import com.drivelingo.feature.lesson.engine.LessonEffect.Finish
import com.drivelingo.feature.lesson.engine.LessonEffect.Listen
import com.drivelingo.feature.lesson.engine.LessonEffect.PlayEarcon
import com.drivelingo.feature.lesson.engine.LessonEffect.RequestAudioFocus
import com.drivelingo.feature.lesson.engine.LessonEffect.RequestFeedback
import com.drivelingo.feature.lesson.engine.LessonEffect.SaveCheckpoint
import com.drivelingo.feature.lesson.engine.LessonEffect.Speak
import com.drivelingo.feature.lesson.engine.LessonEffect.StopAudio
import com.drivelingo.feature.lesson.engine.LessonPhase.ANSWER
import com.drivelingo.feature.lesson.engine.LessonPhase.CLOSING
import com.drivelingo.feature.lesson.engine.LessonPhase.FEEDBACK
import com.drivelingo.feature.lesson.engine.LessonPhase.FINISHED
import com.drivelingo.feature.lesson.engine.LessonPhase.PAUSED
import com.drivelingo.feature.lesson.engine.LessonPhase.PROMPT
import com.drivelingo.feature.lesson.engine.LessonPhase.QUIET
import com.drivelingo.feature.lesson.engine.LessonPhase.READY
import com.drivelingo.feature.lesson.engine.LessonPhase.STOPPED
import com.drivelingo.feature.lesson.engine.LessonPhase.THINKING

/**
 * 수업 상태 머신. `(상태, 이벤트) → (새 상태, 효과)` 순수 함수다.
 * Android·코루틴·시간에 의존하지 않으므로 단위 테스트로 모든 전환을 검증한다(LessonEngineTest).
 * 전환표는 docs/architecture.md 에 있다.
 *
 * 핵심 규칙
 * - 학습 모드 변경은 턴 경계에서만 적용한다. 진행 중인 대답(특히 긴 대답)은 끊지 않는다.
 * - 즉시 중단 이벤트는 어느 단계에서든 바로 멈추고(StopAudio), 진행 상황·콤보는 유지한다.
 * - 재개는 (1) 모든 중단 사유 해소 (2) 사유별 대기 시간 경과 (3) 현재 모드 재확인 후에만 한다.
 *   현재 모드가 SILENT 면 말하지 않고 QUIET 로 간다.
 * - 오디오 포커스 영구 상실·사용자 일시정지는 자동 재개하지 않는다.
 *
 * 효과음(UX 기획서 4장): 말할 차례 '띵'(Listen), 콤보(맞은 답 접수), 멈춤 '톡'(사용자 일시정지·방향지시등),
 * 재개, 긴 대답 질문 '띵동', 세션 완료. 효과음은 말 앞에 붙여([LessonEffect.Speak.cue]) 순서가 섞이지 않게 한다.
 *
 * 음성 명령(기획서 "손을 쓰지 않는 조작"): 말할 차례에 들은 말이 명령이면([VoiceCommandParser]) 대답 대신 처리한다.
 * 명령은 재시도 횟수를 쓰지 않는다. 운전대 "다음"은 [LessonEvent.Command] 로 들어온다.
 */
object LessonEngine {

    const val ECHO_GUARD_MS = 300L
    const val MAX_RETRIES = 1
    /** "천천히" 단계별 말 속도 배율. */
    val RATE_STEPS = listOf(1.0f, 0.85f, 0.7f)
    /** 한 턴에서 명령을 이만큼 받으면 다음 활동으로 넘어간다(잡음이 명령으로 계속 잡히는 경우 대비). */
    const val MAX_COMMANDS_PER_TURN = 4

    const val HELP_LINE = "이렇게 말할 수 있어요. 'Repeat', 'Next', 'Slower', 'In Korean', 'I don't know', 'Wait'. " +
        "그만하려면 'That's enough'라고 하세요."

    fun initial(
        plan: LessonPlan,
        mode: LearningMode,
        startItemIndex: Int = 0,
        tally: LessonTally = LessonTally(),
        offline: Boolean = false,
    ) = LessonState(plan = plan, mode = mode, itemIndex = startItemIndex, tally = tally, offline = offline)

    fun reduce(s: LessonState, e: LessonEvent): Transition {
        if (s.isTerminal) return Transition(s)
        return when (e) {
            is LessonEvent.Start -> onStart(s)
            is LessonEvent.SituationChanged -> onSituation(s, e.mode)
            is LessonEvent.InterruptionsChanged -> onInterruptions(s, e.active, e.nowMs)
            is LessonEvent.NetworkChanged -> onNetwork(s, e.online)
            is LessonEvent.Tick -> if (s.phase == PAUSED) tryResume(s, e.nowMs) else Transition(s)
            is LessonEvent.SpeechFinished -> if (e.token == s.audioToken) onSpeechFinished(s) else Transition(s)
            is LessonEvent.AnswerRecognized ->
                if (s.phase == ANSWER && e.token == s.audioToken) onAnswer(s, e.text) else Transition(s)
            is LessonEvent.AnswerFailed ->
                if (s.phase == ANSWER && e.token == s.audioToken) onAnswerFailed(s) else Transition(s)
            is LessonEvent.CoachReplied ->
                if (s.phase == THINKING && e.token == s.audioToken) applyFeedback(s, e.feedback, fallback = false) else Transition(s)
            is LessonEvent.CoachFailed ->
                if (s.phase == THINKING && e.token == s.audioToken) applyLocalFeedback(s) else Transition(s)
            LessonEvent.UserPause -> onUserPause(s)
            is LessonEvent.UserResume -> onUserResume(s, e.nowMs)
            LessonEvent.Repeat -> onRepeat(s)
            is LessonEvent.Command ->
                if (s.phase in EXTERNAL_COMMAND_PHASES) onCommand(s, e.command).withLeading(StopAudio) else Transition(s)
            LessonEvent.Stop -> onStop(s)
        }
    }

    // ---- 시작 / 턴 진행 ----

    private fun onStart(s: LessonState): Transition {
        if (s.phase != READY) return Transition(s)
        if (s.activeInterruptions.isNotEmpty()) {
            return Transition(s.copy(phase = PAUSED, pausedFrom = READY), listOf(RequestAudioFocus))
        }
        return beginTurn(s, countTurn = true).withLeading(RequestAudioFocus)
    }

    private fun beginTurn(s: LessonState, countTurn: Boolean, resumed: Boolean = false, lead: String? = null): Transition {
        val item = s.plan.items.getOrNull(s.itemIndex) ?: return close(s)
        val turn = ActivityPlanner.plan(item, s.mode, s.offline, s.listenOnly)
            ?: return Transition(s.copy(phase = QUIET, turn = null, feedback = null))
        val tally = if (countTurn) s.tally.copy(turnsByMode = s.tally.turnsByMode.increment(turn.mode)) else s.tally
        val prompt = listOfNotNull(if (resumed) "Okay, let's continue." else lead, turn.prompt).joinToString(" ")
        val cue = when {
            resumed -> Earcon.Resume
            turn.activity == ActivityType.LONG_ANSWER -> Earcon.LongQuestion
            else -> null
        }
        return speak(
            s.copy(phase = PROMPT, turn = turn, retries = 0, lastAnswer = null, feedback = null, tally = tally, commandsThisTurn = 0),
            prompt,
            cue,
        )
    }

    private fun onSpeechFinished(s: LessonState): Transition = when (s.phase) {
        PROMPT -> {
            val turn = s.turn ?: return Transition(s)
            if (turn.expectsAnswer) {
                val token = s.audioToken + 1
                Transition(s.copy(phase = ANSWER, audioToken = token), listOf(Listen(token, turn, ECHO_GUARD_MS)))
            } else {
                completeItem(s)
            }
        }
        FEEDBACK -> completeItem(s)
        CLOSING -> finish(s)
        else -> Transition(s)
    }

    private fun completeItem(s: LessonState, lead: String? = null): Transition {
        val next = s.itemIndex + 1
        val advanced = s.copy(itemIndex = next, turn = null, feedback = null, lastAnswer = null)
        return beginTurn(advanced, countTurn = true, lead = lead).withLeading(SaveCheckpoint(next, s.tally))
    }

    private fun close(s: LessonState): Transition {
        if (s.mode == LearningMode.SILENT) return finish(s)
        val line = if (s.plan.commute == CommuteType.MORNING) {
            "That's all for this morning. Try today's phrases at work!"
        } else {
            "That's all for today. Drive safe!"
        }
        return speak(s.copy(phase = CLOSING, turn = null), line, Earcon.SessionComplete)
    }

    private fun finish(s: LessonState) =
        Transition(s.copy(phase = FINISHED, turn = null), listOf(AbandonAudioFocus, Finish(s.tally)))

    // ---- 대답 처리 ----

    private fun onAnswer(s: LessonState, text: String): Transition {
        val turn = s.turn ?: return Transition(s)
        VoiceCommandParser.parse(text, turn.expectedAnswer)?.let { return onCommand(s, it.command) }
        val answered = s.copy(lastAnswer = text)
        // 따라 말하기는 정답이 있으니 기기에서 바로 채점. 오프라인이면 AI 를 부르지 않는다.
        if (turn.activity == ActivityType.REPEAT || s.offline) {
            return applyFeedback(answered, LocalFeedback.build(turn, text), fallback = s.offline && turn.activity != ActivityType.REPEAT)
        }
        val token = s.audioToken + 1
        return Transition(answered.copy(phase = THINKING, audioToken = token), listOf(RequestFeedback(token, turn, text)))
    }

    private fun onAnswerFailed(s: LessonState): Transition {
        val turn = s.turn ?: return Transition(s)
        if (s.retries < MAX_RETRIES) {
            val lead = if (turn.activity == ActivityType.LONG_ANSWER) "Take your time." else "No rush. Let's try once more."
            return speak(s.copy(phase = PROMPT, retries = s.retries + 1), "$lead ${turn.prompt}")
        }
        // 무응답·인식 실패는 운전 때문일 수 있으므로 콤보를 끊지 않고 모범 답안만 들려준다.
        val feedback = CoachFeedback("That's okay. You can say: ${turn.expectedAnswer}", correct = false)
        return speak(s.copy(phase = FEEDBACK, feedback = feedback), feedback.reply)
    }

    private fun applyLocalFeedback(s: LessonState): Transition {
        val turn = s.turn ?: return Transition(s)
        return applyFeedback(s, LocalFeedback.build(turn, s.lastAnswer.orEmpty()), fallback = true)
    }

    private fun applyFeedback(s: LessonState, feedback: CoachFeedback, fallback: Boolean): Transition {
        val words = WordCounter.count(s.lastAnswer.orEmpty())
        val t = s.tally
        val combo = if (feedback.correct) t.combo + 1 else 0
        val tally = t.copy(
            words = t.words + words,
            combo = combo,
            maxCombo = maxOf(t.maxCombo, combo),
            wordsWhileStopped = t.wordsWhileStopped + if (s.turn?.mode == LearningMode.LONG_ANSWER) words else 0,
            corrections = feedback.recast?.let { t.corrections + it } ?: t.corrections,
            coachFallbacks = t.coachFallbacks + if (fallback) 1 else 0,
        )
        // 콤보가 올라갈 때만 콤보음. 틀려서 콤보가 끊기면 소리 없이 되받아 준다(감점 느낌을 주지 않도록).
        val cue = if (combo > t.combo) Earcon.Combo(combo) else null
        return speak(s.copy(phase = FEEDBACK, feedback = feedback, tally = tally), feedback.reply, cue)
    }

    // ---- 운전 상황 / 네트워크 ----

    private fun onSituation(s: LessonState, mode: LearningMode): Transition {
        val updated = s.copy(mode = mode)
        if (s.phase == QUIET && mode != LearningMode.SILENT && updated.activeInterruptions.isEmpty()) {
            return beginTurn(updated, countTurn = true)
        }
        return Transition(updated)
    }

    private fun onNetwork(s: LessonState, online: Boolean): Transition {
        val updated = s.copy(offline = !online)
        // 응답을 기다리던 중 끊기면 기다리지 않고 바로 로컬 피드백으로 넘어간다.
        if (!online && s.phase == THINKING) return applyLocalFeedback(updated)
        return Transition(updated)
    }

    // ---- 즉시 중단 / 재개 ----

    private fun onInterruptions(s: LessonState, active: Set<Interruption>, nowMs: Long): Transition {
        val added = active - s.signalInterruptions
        val removed = s.signalInterruptions - active
        val resumeAt = if (removed.isEmpty()) s.resumeNotBeforeMs
        else maxOf(s.resumeNotBeforeMs, nowMs + removed.maxOf { it.resumeDelayMs })
        val counted = if (s.phase != READY) s.tally.copy(pausesByReason = s.tally.pausesByReason.incrementAll(added)) else s.tally
        // 오디오 포커스 영구 상실 등은 신호가 풀려도 자동 재개하지 않는다 → 사용자 재개 대기 상태로 둔다.
        val needsUser = added.any { it.needsUserAction }
        val updated = s.copy(
            signalInterruptions = active, resumeNotBeforeMs = resumeAt, tally = counted,
            userPaused = s.userPaused || needsUser,
        )

        return when {
            s.phase == READY -> Transition(updated)
            s.phase == PAUSED -> tryResume(updated, nowMs)
            updated.activeInterruptions.isNotEmpty() -> pause(updated)
            else -> Transition(updated)
        }
    }

    private fun pause(s: LessonState): Transition {
        val token = s.audioToken + 1
        val effects = if (Interruption.allowPauseCue(s.activeInterruptions)) listOf(StopAudio, PlayEarcon(Earcon.Pause))
        else listOf(StopAudio)
        return Transition(s.copy(phase = PAUSED, pausedFrom = s.phase, audioToken = token), effects)
    }

    private fun tryResume(s: LessonState, nowMs: Long): Transition {
        if (s.activeInterruptions.isNotEmpty() || nowMs < s.resumeNotBeforeMs) return Transition(s)
        val from = s.pausedFrom
        val resumed = s.copy(pausedFrom = null)
        val feedback = s.feedback
        return when {
            // 이미 채점까지 끝난 턴: 피드백만 다시 들려주고 다음으로. 말할 수 없는 구간이면 조용히 넘어간다.
            from == FEEDBACK && feedback != null ->
                if (s.mode == LearningMode.SILENT) completeItem(resumed)
                else speak(resumed.copy(phase = FEEDBACK), feedback.reply, Earcon.Resume)
            from == CLOSING -> close(resumed)
            // 그 밖에는 현재 모드로 턴을 다시 짠다(멈춘 사이 운전 상황이 바뀌었을 수 있다).
            else -> {
                val freshTurn = from == READY || from == QUIET
                beginTurn(resumed, countTurn = freshTurn, resumed = !freshTurn)
            }
        }
    }

    private fun onUserPause(s: LessonState): Transition {
        if (s.phase == READY || s.userPaused) return Transition(s)
        val updated = s.copy(
            userPaused = true,
            tally = s.tally.copy(pausesByReason = s.tally.pausesByReason.increment(Interruption.USER_PAUSE)),
        )
        return if (s.phase == PAUSED) Transition(updated) else pause(updated)
    }

    private fun onUserResume(s: LessonState, nowMs: Long): Transition {
        val focusLost = Interruption.AUDIO_FOCUS_LOST in s.signalInterruptions
        if (!s.userPaused && !focusLost) return Transition(s)
        val updated = s.copy(userPaused = false)
        val result = if (updated.phase == PAUSED) tryResume(updated, nowMs) else Transition(updated)
        // 오디오 포커스는 사용자의 명시적 동작이 있을 때만 다시 요청한다.
        return if (focusLost) result.withLeading(RequestAudioFocus) else result
    }

    private fun onRepeat(s: LessonState): Transition {
        val turn = s.turn
        return when {
            (s.phase == PROMPT || s.phase == ANSWER) && turn != null ->
                speak(s.copy(phase = PROMPT), turn.prompt).withLeading(StopAudio)
            s.phase == FEEDBACK && s.feedback != null ->
                speak(s, s.feedback.reply).withLeading(StopAudio)
            else -> Transition(s)
        }
    }

    // ---- 음성 명령 ----

    private fun onCommand(s: LessonState, command: VoiceCommand): Transition {
        val turn = s.turn ?: return if (command == VoiceCommand.PAUSE) onUserPause(s) else Transition(s)
        val counted = s.copy(commandsThisTurn = s.commandsThisTurn + 1)
        if (counted.commandsThisTurn > MAX_COMMANDS_PER_TURN && command != VoiceCommand.PAUSE) {
            return completeItem(counted, lead = "Let's try the next one.")
        }
        return when (command) {
            VoiceCommand.PAUSE -> onUserPause(counted)
            VoiceCommand.REPEAT -> reprompt(counted, turn, lead = null)
            VoiceCommand.NEXT -> completeItem(counted, lead = "Okay, next one.")
            VoiceCommand.SLOWER -> {
                val step = minOf(s.rateStep + 1, RATE_STEPS.lastIndex)
                val lead = if (step == s.rateStep) "This is my slowest speed." else "Sure, I'll slow down."
                reprompt(counted.copy(rateStep = step), turn, lead)
            }
            VoiceCommand.FASTER -> {
                val step = maxOf(s.rateStep - 1, 0)
                val lead = if (step == s.rateStep) "This is my normal speed." else "Okay, a little faster."
                reprompt(counted.copy(rateStep = step), turn, lead)
            }
            VoiceCommand.KOREAN -> reprompt(counted, turn, koreanExplanation(s))
            VoiceCommand.DONT_KNOW -> demonstrate(counted, turn)
            VoiceCommand.END_TODAY -> wrapUp(counted)
            VoiceCommand.LISTEN_ONLY -> {
                val quiet = counted.copy(listenOnly = true, tally = counted.tally.copy(listenOnly = true), feedback = null)
                beginTurn(quiet, countTurn = false, lead = "Sure. Just listen along today.")
            }
            VoiceCommand.HELP -> reprompt(counted, turn, HELP_LINE)
        }
    }

    /** 같은 턴의 질문을 다시 들려주고 다시 듣는다. 재시도 횟수는 그대로 둔다. */
    private fun reprompt(s: LessonState, turn: Turn, lead: String?): Transition =
        speak(
            s.copy(phase = PROMPT, lastAnswer = null, feedback = null),
            listOfNotNull(lead, turn.prompt).joinToString(" "),
        )

    /** "모르겠어": 코치가 먼저 시범을 보이고 따라 하게 한다. 콤보는 끊지만 감점은 없다(UX 기획서 9장 "말이 막힘"). */
    private fun demonstrate(s: LessonState, turn: Turn): Transition {
        val expected = turn.expectedAnswer
        val lead = if (turn.activity == ActivityType.REPEAT) "Listen carefully." else "No problem. Let me go first."
        val demo = turn.copy(
            activity = ActivityType.REPEAT,
            prompt = "$lead $expected Now you try.",
            expectedAnswer = expected,
            answerLimitMs = ActivityPlanner.REPEAT_LIMIT_MS,
        )
        return speak(
            s.copy(phase = PROMPT, turn = demo, lastAnswer = null, feedback = null, tally = s.tally.copy(combo = 0)),
            demo.prompt,
        )
    }

    /** "오늘은 그만": 마무리 블록으로 건너뛴다. 마무리가 없거나 이미 지났으면 마무리 인사로. */
    private fun wrapUp(s: LessonState): Transition {
        val wrapIndex = s.plan.items.indexOfLast { it.kind == ItemKind.WRAP_UP }
        if (wrapIndex <= s.itemIndex) return close(s.copy(itemIndex = s.plan.items.size))
        val jumped = s.copy(itemIndex = wrapIndex, turn = null, feedback = null, lastAnswer = null)
        return beginTurn(jumped, countTurn = true, lead = "Okay, let's wrap up for today.")
            .withLeading(SaveCheckpoint(wrapIndex, s.tally))
    }

    /** 지금 연습하는 표현의 뜻을 한국어로 알려 준다. 조사(이라는/라는)는 받침으로 고른다. */
    private fun koreanExplanation(s: LessonState): String {
        val target = s.plan.items.getOrNull(s.itemIndex)?.target ?: return "지금 질문을 한 번 더 들려줄게요."
        val korean = target.korean.trim().trimEnd('.', '?', '!')
        if (korean.isEmpty()) return "지금 질문을 한 번 더 들려줄게요."
        return "지금 표현은 우리말로 '$korean'${KoreanJosa.ira(korean)}는 뜻이에요. 다시 들어 볼게요."
    }

    private fun onStop(s: LessonState) = Transition(
        s.copy(phase = STOPPED, turn = null),
        listOf(StopAudio, AbandonAudioFocus, SaveCheckpoint(s.itemIndex, s.tally)),
    )

    // ---- helpers ----

    private fun speak(s: LessonState, text: String, cue: Earcon? = null): Transition {
        val token = s.audioToken + 1
        val rate = s.plan.speechRate * RATE_STEPS[s.rateStep.coerceIn(0, RATE_STEPS.lastIndex)]
        return Transition(s.copy(audioToken = token), listOf(Speak(token, text, rate, cue)))
    }

    /** 운전대·화면 명령을 받는 단계. 멈춤·침묵·마무리 중에는 무시한다. */
    private val EXTERNAL_COMMAND_PHASES = setOf(PROMPT, ANSWER, THINKING, FEEDBACK)

    private fun Transition.withLeading(effect: LessonEffect) = copy(effects = listOf(effect) + effects)

    private fun <K> Map<K, Int>.increment(key: K): Map<K, Int> = this + (key to (this[key] ?: 0) + 1)

    private fun <K> Map<K, Int>.incrementAll(keys: Set<K>): Map<K, Int> = keys.fold(this) { acc, k -> acc.increment(k) }
}

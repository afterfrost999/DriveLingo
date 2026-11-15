package com.drivelingo.feature.lesson.engine

import com.drivelingo.core.curriculum.Level
import com.drivelingo.core.curriculum.MockCurriculumRepository
import com.drivelingo.core.driving.Interruption
import com.drivelingo.core.driving.LearningMode
import com.drivelingo.core.progress.Correction
import com.drivelingo.core.progress.LessonTally
import com.drivelingo.feature.lesson.engine.LessonEffect.Finish
import com.drivelingo.feature.lesson.engine.LessonEffect.Listen
import com.drivelingo.feature.lesson.engine.LessonEffect.RequestAudioFocus
import com.drivelingo.feature.lesson.engine.LessonEffect.RequestFeedback
import com.drivelingo.feature.lesson.engine.LessonEffect.SaveCheckpoint
import com.drivelingo.feature.lesson.engine.LessonEffect.Speak
import com.drivelingo.feature.lesson.engine.LessonEffect.StopAudio
import com.drivelingo.feature.lesson.engine.LessonEvent.AnswerFailed
import com.drivelingo.feature.lesson.engine.LessonEvent.AnswerRecognized
import com.drivelingo.feature.lesson.engine.LessonEvent.CoachFailed
import com.drivelingo.feature.lesson.engine.LessonEvent.CoachReplied
import com.drivelingo.feature.lesson.engine.LessonEvent.InterruptionsChanged
import com.drivelingo.feature.lesson.engine.LessonEvent.NetworkChanged
import com.drivelingo.feature.lesson.engine.LessonEvent.SituationChanged
import com.drivelingo.feature.lesson.engine.LessonEvent.SpeechFinished
import com.drivelingo.feature.lesson.engine.LessonEvent.Start
import com.drivelingo.feature.lesson.engine.LessonEvent.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonEngineTest {

    /** L2 1주차 화요일 출근 수업: warmup, intro-0, practice-0, intro-1, practice-1, roleplay, personal, wrap-up */
    private val plan = MockCurriculumRepository().lessonPlan(Level.L2, week = 1, lessonNumber = 3)

    private class Harness(var state: LessonState) {
        fun send(event: LessonEvent): List<LessonEffect> {
            val t = LessonEngine.reduce(state, event)
            state = t.state
            return t.effects
        }

        fun speechDone() = send(SpeechFinished(state.audioToken))
        fun answer(text: String) = send(AnswerRecognized(state.audioToken, text))
        fun coach(feedback: CoachFeedback) = send(CoachReplied(state.audioToken, feedback))
    }

    private fun harness(
        mode: LearningMode = LearningMode.SHORT_ANSWER,
        startItem: Int = 0,
        tally: LessonTally = LessonTally(),
        offline: Boolean = false,
    ) = Harness(LessonEngine.initial(plan, mode, startItem, tally, offline))

    @Test
    fun `start requests audio focus then speaks the first prompt`() {
        val h = harness()
        val effects = h.send(Start(0))
        assertEquals(RequestAudioFocus, effects.first())
        assertTrue(effects[1] is Speak)
        assertEquals(LessonPhase.PROMPT, h.state.phase)
        assertEquals(ActivityType.SHORT_ANSWER, h.state.turn?.activity)
        assertEquals(1, h.state.tally.turnsByMode[LearningMode.SHORT_ANSWER])
    }

    @Test
    fun `microphone opens only after speech ends with an echo guard`() {
        val h = harness()
        h.send(Start(0))
        val effects = h.speechDone()
        val listen = effects.single() as Listen
        assertEquals(LessonEngine.ECHO_GUARD_MS, listen.echoGuardMs)
        assertEquals(ActivityPlanner.SHORT_LIMIT_MS, listen.turn.answerLimitMs)
        assertEquals(LessonPhase.ANSWER, h.state.phase)
    }

    @Test
    fun `stale speech completion is ignored`() {
        val h = harness()
        h.send(Start(0))
        val staleToken = h.state.audioToken - 1
        val effects = h.send(SpeechFinished(staleToken))
        assertTrue(effects.isEmpty())
        assertEquals(LessonPhase.PROMPT, h.state.phase)
    }

    @Test
    fun `open answer goes to coach and correct feedback grows combo`() {
        val h = harness()
        h.send(Start(0))
        h.speechDone()
        val effects = h.answer("How was your weekend?")
        assertTrue(effects.single() is RequestFeedback)
        assertEquals(LessonPhase.THINKING, h.state.phase)

        h.coach(CoachFeedback("Great!", correct = true))
        assertEquals(LessonPhase.FEEDBACK, h.state.phase)
        assertEquals(4, h.state.tally.words)
        assertEquals(1, h.state.tally.combo)

        val tallyAfterFeedback = h.state.tally
        val next = h.speechDone()
        assertEquals(SaveCheckpoint(1, tallyAfterFeedback), next.first())
        assertEquals(1, h.state.itemIndex)
        assertEquals(ActivityType.REPEAT, h.state.turn?.activity) // 새 표현 소개는 따라 말하기
    }

    @Test
    fun `repeat answers are scored on device without calling the coach`() {
        val h = harness(startItem = 1)
        h.send(Start(0))
        h.speechDone()
        val effects = h.answer("Thanks for coming, everyone.")
        assertTrue(effects.none { it is RequestFeedback })
        assertEquals(LessonPhase.FEEDBACK, h.state.phase)
        assertEquals(1, h.state.tally.combo)
    }

    @Test
    fun `wrong answer resets combo and records the recast`() {
        val h = harness(tally = LessonTally(combo = 3, maxCombo = 3))
        h.send(Start(0))
        h.speechDone()
        h.answer("I go to camping with my family.")
        val recast = Correction("I go to camping with my family.", "I went camping with my family.")
        h.coach(CoachFeedback("Oh, I see. I went camping with my family.", correct = false, recast = recast))
        assertEquals(0, h.state.tally.combo)
        assertEquals(3, h.state.tally.maxCombo)
        assertEquals(listOf(recast), h.state.tally.corrections)
    }

    @Test
    fun `turn signal pauses immediately and keeps progress and combo`() {
        val h = harness(tally = LessonTally(combo = 2, maxCombo = 2))
        h.send(Start(0))
        h.speechDone()
        val tokenBefore = h.state.audioToken

        val effects = h.send(InterruptionsChanged(setOf(Interruption.TURN_SIGNAL), nowMs = 1_000))
        assertEquals(listOf(StopAudio), effects)
        assertEquals(LessonPhase.PAUSED, h.state.phase)
        assertEquals(1, h.state.tally.pausesByReason[Interruption.TURN_SIGNAL])
        assertEquals(2, h.state.tally.combo)

        // 멈춘 뒤 늦게 도착한 STT 결과는 버린다.
        h.send(AnswerRecognized(tokenBefore, "late answer"))
        assertEquals(LessonPhase.PAUSED, h.state.phase)
        assertNull(h.state.lastAnswer)
    }

    @Test
    fun `resume waits for the per-event delay then replays the turn`() {
        val h = harness()
        h.send(Start(0))
        h.speechDone()
        h.send(InterruptionsChanged(setOf(Interruption.TURN_SIGNAL), 1_000))
        h.send(InterruptionsChanged(emptySet(), 2_000))
        assertEquals(LessonPhase.PAUSED, h.state.phase)

        h.send(Tick(2_000 + Interruption.TURN_SIGNAL.resumeDelayMs - 1))
        assertEquals(LessonPhase.PAUSED, h.state.phase)

        val effects = h.send(Tick(2_000 + Interruption.TURN_SIGNAL.resumeDelayMs))
        assertEquals(LessonPhase.PROMPT, h.state.phase)
        assertTrue((effects.single() as Speak).text.startsWith("Okay, let's continue."))
        assertEquals(1, h.state.tally.turnsByMode[LearningMode.SHORT_ANSWER]) // 재개는 새 턴으로 세지 않는다
    }

    @Test
    fun `resume re-plans the turn with the current driving mode`() {
        val h = harness()
        h.send(Start(0))
        h.send(InterruptionsChanged(setOf(Interruption.PHONE_CALL), 0))
        h.send(SituationChanged(LearningMode.REPEAT_AFTER))
        h.send(InterruptionsChanged(emptySet(), 10_000))
        h.send(Tick(20_000))
        assertEquals(ActivityType.REPEAT, h.state.turn?.activity)
    }

    @Test
    fun `resume in a silent section waits quietly until it is safe`() {
        val h = harness()
        h.send(Start(0))
        h.send(InterruptionsChanged(setOf(Interruption.NAV_GUIDANCE), 0))
        h.send(SituationChanged(LearningMode.SILENT))
        h.send(InterruptionsChanged(emptySet(), 0))
        h.send(Tick(10_000))
        assertEquals(LessonPhase.QUIET, h.state.phase)

        val effects = h.send(SituationChanged(LearningMode.SHORT_ANSWER))
        assertEquals(LessonPhase.PROMPT, h.state.phase)
        assertTrue(effects.single() is Speak)
    }

    @Test
    fun `mode change never cuts a long answer and applies from the next turn`() {
        val h = harness(mode = LearningMode.LONG_ANSWER, startItem = 2)
        h.send(Start(0))
        assertEquals(ActivityType.LONG_ANSWER, h.state.turn?.activity)
        assertNull(h.state.turn?.answerLimitMs) // 시간 제한 없음
        h.speechDone()

        h.send(SituationChanged(LearningMode.SHORT_ANSWER)) // 신호가 바뀌어 출발
        assertEquals(LessonPhase.ANSWER, h.state.phase)
        assertEquals(ActivityType.LONG_ANSWER, h.state.turn?.activity)

        h.answer("Okay everyone is here so shall we get started")
        h.coach(CoachFeedback("Great!", correct = true))
        assertEquals(9, h.state.tally.wordsWhileStopped)

        h.speechDone()
        assertEquals(LearningMode.SHORT_ANSWER, h.state.turn?.mode)
    }

    @Test
    fun `coach timeout falls back to local feedback`() {
        val h = harness()
        h.send(Start(0))
        h.speechDone()
        h.answer("How was your weekend?")
        h.send(CoachFailed(h.state.audioToken))
        assertEquals(LessonPhase.FEEDBACK, h.state.phase)
        assertEquals(1, h.state.tally.coachFallbacks)
        assertTrue(h.state.feedback!!.reply.contains("How was your weekend?"))
    }

    @Test
    fun `coach reply arriving after a pause is ignored`() {
        val h = harness()
        h.send(Start(0))
        h.speechDone()
        h.answer("How was your weekend?")
        val token = h.state.audioToken
        h.send(InterruptionsChanged(setOf(Interruption.SUDDEN_BRAKING), 0))
        h.send(CoachReplied(token, CoachFeedback("Great!", true)))
        assertEquals(LessonPhase.PAUSED, h.state.phase)
        assertEquals(0, h.state.tally.words)
    }

    @Test
    fun `offline lessons switch open answers to on-device repeat cards`() {
        val h = harness(offline = true)
        h.send(Start(0))
        assertEquals(ActivityType.REPEAT, h.state.turn?.activity)
    }

    @Test
    fun `losing network while waiting for the coach answers locally right away`() {
        val h = harness()
        h.send(Start(0))
        h.speechDone()
        h.answer("How was your weekend?")
        h.send(NetworkChanged(online = false))
        assertEquals(LessonPhase.FEEDBACK, h.state.phase)
        assertTrue(h.state.offline)
    }

    @Test
    fun `recognition failure retries once then gives the model answer without breaking combo`() {
        val h = harness(tally = LessonTally(combo = 2, maxCombo = 2))
        h.send(Start(0))
        h.speechDone()

        val retry = h.send(AnswerFailed(h.state.audioToken, noSpeech = true))
        assertEquals(LessonPhase.PROMPT, h.state.phase)
        assertTrue((retry.single() as Speak).text.startsWith("No rush."))

        h.speechDone()
        h.send(AnswerFailed(h.state.audioToken, noSpeech = false))
        assertEquals(LessonPhase.FEEDBACK, h.state.phase)
        assertTrue(h.state.feedback!!.reply.startsWith("That's okay."))
        assertEquals(2, h.state.tally.combo)
    }

    @Test
    fun `permanent audio focus loss needs an explicit user resume`() {
        val h = harness()
        h.send(Start(0))
        h.send(InterruptionsChanged(setOf(Interruption.AUDIO_FOCUS_LOST), 0))
        assertEquals(LessonPhase.PAUSED, h.state.phase)

        // 신호가 풀려도 사용자가 재개하기 전에는 다시 말하지 않는다.
        h.send(InterruptionsChanged(emptySet(), 1_000))
        h.send(Tick(60_000))
        assertEquals(LessonPhase.PAUSED, h.state.phase)

        h.send(InterruptionsChanged(setOf(Interruption.AUDIO_FOCUS_LOST), 61_000))
        val effects = h.send(LessonEvent.UserResume(62_000))
        assertEquals(RequestAudioFocus, effects.first())
        assertEquals(LessonPhase.PAUSED, h.state.phase)

        h.send(InterruptionsChanged(emptySet(), 62_100)) // 포커스 다시 획득
        assertEquals(LessonPhase.PROMPT, h.state.phase)
    }

    @Test
    fun `user pause and resume`() {
        val h = harness()
        h.send(Start(0))
        h.send(LessonEvent.UserPause)
        assertEquals(LessonPhase.PAUSED, h.state.phase)
        h.send(Tick(100_000))
        assertEquals(LessonPhase.PAUSED, h.state.phase)
        h.send(LessonEvent.UserResume(100_000))
        assertEquals(LessonPhase.PROMPT, h.state.phase)
    }

    @Test
    fun `starting while interrupted waits and then starts fresh`() {
        val h = harness()
        h.send(InterruptionsChanged(setOf(Interruption.PHONE_CALL), 0))
        h.send(Start(0))
        assertEquals(LessonPhase.PAUSED, h.state.phase)
        h.send(InterruptionsChanged(emptySet(), 0))
        val effects = h.send(Tick(5_000))
        assertFalse((effects.single() as Speak).text.startsWith("Okay"))
        assertEquals(1, h.state.tally.turnsByMode[LearningMode.SHORT_ANSWER])
    }

    @Test
    fun `last item leads to closing and finish`() {
        val h = harness(startItem = plan.items.lastIndex)
        h.send(Start(0))
        assertEquals(ActivityType.LISTEN, h.state.turn?.activity) // 마무리는 항상 듣기
        h.speechDone()
        assertEquals(LessonPhase.CLOSING, h.state.phase)
        val effects = h.speechDone()
        assertEquals(LessonPhase.FINISHED, h.state.phase)
        assertTrue(effects.any { it is Finish })
        assertTrue(h.send(Start(0)).isEmpty()) // 종료 후 이벤트 무시
    }

    @Test
    fun `stop saves checkpoint and a new session resumes from it`() {
        val h = harness(startItem = 5, tally = LessonTally(words = 30, combo = 3, maxCombo = 3))
        h.send(Start(0))
        val effects = h.send(LessonEvent.Stop)
        assertEquals(LessonPhase.STOPPED, h.state.phase)
        val checkpoint = effects.filterIsInstance<SaveCheckpoint>().single()
        assertEquals(5, checkpoint.itemIndex)
        assertEquals(30, checkpoint.tally.words)

        val resumed = harness(startItem = checkpoint.itemIndex, tally = checkpoint.tally)
        resumed.send(Start(0))
        assertEquals("roleplay", resumed.state.turn?.itemId)
        assertEquals(3, resumed.state.tally.combo)
    }

    @Test
    fun `silent mode skips speaking entirely`() {
        val h = harness(mode = LearningMode.SILENT)
        val effects = h.send(Start(0))
        assertEquals(LessonPhase.QUIET, h.state.phase)
        assertTrue(effects.none { it is Speak })
    }
}

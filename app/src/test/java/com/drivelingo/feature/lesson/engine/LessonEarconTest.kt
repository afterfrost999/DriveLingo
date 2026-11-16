package com.drivelingo.feature.lesson.engine

import com.drivelingo.core.audio.Earcon
import com.drivelingo.core.curriculum.Level
import com.drivelingo.core.curriculum.MockCurriculumRepository
import com.drivelingo.core.driving.Interruption
import com.drivelingo.core.driving.LearningMode
import com.drivelingo.core.progress.LessonTally
import com.drivelingo.feature.lesson.engine.LessonEffect.Listen
import com.drivelingo.feature.lesson.engine.LessonEffect.PlayEarcon
import com.drivelingo.feature.lesson.engine.LessonEffect.Speak
import com.drivelingo.feature.lesson.engine.LessonEffect.StopAudio
import com.drivelingo.feature.lesson.engine.LessonEvent.AnswerRecognized
import com.drivelingo.feature.lesson.engine.LessonEvent.CoachReplied
import com.drivelingo.feature.lesson.engine.LessonEvent.InterruptionsChanged
import com.drivelingo.feature.lesson.engine.LessonEvent.SpeechFinished
import com.drivelingo.feature.lesson.engine.LessonEvent.Start
import com.drivelingo.feature.lesson.engine.LessonEvent.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 효과음이 UX 기획서 4장 규칙대로 나는지. */
class LessonEarconTest {

    private val plan = MockCurriculumRepository().lessonPlan(Level.L2, week = 1, lessonNumber = 3)

    private class Harness(var state: LessonState) {
        fun send(event: LessonEvent): List<LessonEffect> {
            val t = LessonEngine.reduce(state, event)
            state = t.state
            return t.effects
        }
        fun speechDone() = send(SpeechFinished(state.audioToken))
        fun answer(text: String) = send(AnswerRecognized(state.audioToken, text))
    }

    private fun harness(mode: LearningMode = LearningMode.SHORT_ANSWER, startItem: Int = 0, tally: LessonTally = LessonTally()) =
        Harness(LessonEngine.initial(plan, mode, startItem, tally))

    @Test
    fun `microphone opens with the your-turn cue`() {
        val h = harness()
        h.send(Start(0))
        val listen = h.speechDone().single() as Listen
        assertEquals(Earcon.YourTurn, listen.cue)
    }

    @Test
    fun `long answer question starts with ding-dong`() {
        val h = harness(mode = LearningMode.LONG_ANSWER, startItem = 2)
        val speak = h.send(Start(0)).filterIsInstance<Speak>().single()
        assertEquals(Earcon.LongQuestion, speak.cue)
    }

    @Test
    fun `combo cue rises with each accepted answer and is silent when the combo breaks`() {
        val h = harness(tally = LessonTally(combo = 2, maxCombo = 2))
        h.send(Start(0))
        h.speechDone()
        h.answer("How was your weekend?")
        val good = h.send(CoachReplied(h.state.audioToken, CoachFeedback("Great!", correct = true))).single() as Speak
        assertEquals(Earcon.Combo(3), good.cue)

        h.speechDone() // 다음 아이템(새 표현 소개 → 따라 말하기)
        h.speechDone()
        val miss = h.answer("something else entirely").single() as Speak
        assertNull(miss.cue)
        assertEquals(0, h.state.tally.combo)
    }

    @Test
    fun `pause cue only for user pause and turn signal`() {
        val quiet = setOf(
            Interruption.NAV_GUIDANCE, Interruption.PHONE_CALL, Interruption.VEHICLE_WARNING,
            Interruption.SUDDEN_BRAKING, Interruption.AUDIO_FOCUS_TRANSIENT, Interruption.AUDIO_FOCUS_LOST,
        )
        quiet.forEach { reason ->
            val h = harness()
            h.send(Start(0))
            assertEquals("$reason", listOf(StopAudio), h.send(InterruptionsChanged(setOf(reason), 1_000)))
        }
        // 방향지시등과 내비 안내가 겹치면 내비 소리를 방해하지 않도록 조용히 멈춘다.
        val both = harness()
        both.send(Start(0))
        assertEquals(listOf(StopAudio), both.send(InterruptionsChanged(setOf(Interruption.TURN_SIGNAL, Interruption.NAV_GUIDANCE), 0)))

        val user = harness()
        user.send(Start(0))
        assertEquals(listOf(StopAudio, PlayEarcon(Earcon.Pause)), user.send(LessonEvent.UserPause))
    }

    @Test
    fun `resuming plays the resume cue before continuing`() {
        val h = harness()
        h.send(Start(0))
        h.speechDone()
        h.send(InterruptionsChanged(setOf(Interruption.TURN_SIGNAL), 1_000))
        h.send(InterruptionsChanged(emptySet(), 2_000))
        val speak = h.send(Tick(10_000)).single() as Speak
        assertEquals(Earcon.Resume, speak.cue)
        assertTrue(speak.text.startsWith("Okay, let's continue."))
    }

    @Test
    fun `closing line starts with the session complete melody`() {
        val h = harness(startItem = plan.items.lastIndex)
        h.send(Start(0))
        val closing = h.speechDone().filterIsInstance<Speak>().single()
        assertEquals(Earcon.SessionComplete, closing.cue)
    }
}

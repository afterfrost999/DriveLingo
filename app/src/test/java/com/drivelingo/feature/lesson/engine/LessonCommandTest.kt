package com.drivelingo.feature.lesson.engine

import com.drivelingo.core.audio.Earcon
import com.drivelingo.core.curriculum.ItemKind
import com.drivelingo.core.curriculum.Level
import com.drivelingo.core.curriculum.MockCurriculumRepository
import com.drivelingo.core.driving.Interruption
import com.drivelingo.core.driving.LearningMode
import com.drivelingo.core.progress.LessonTally
import com.drivelingo.core.speech.command.VoiceCommand
import com.drivelingo.feature.lesson.engine.LessonEffect.Listen
import com.drivelingo.feature.lesson.engine.LessonEffect.PlayEarcon
import com.drivelingo.feature.lesson.engine.LessonEffect.SaveCheckpoint
import com.drivelingo.feature.lesson.engine.LessonEffect.Speak
import com.drivelingo.feature.lesson.engine.LessonEffect.StopAudio
import com.drivelingo.feature.lesson.engine.LessonEvent.AnswerRecognized
import com.drivelingo.feature.lesson.engine.LessonEvent.InterruptionsChanged
import com.drivelingo.feature.lesson.engine.LessonEvent.SpeechFinished
import com.drivelingo.feature.lesson.engine.LessonEvent.Start
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 말할 차례에 들은 음성 명령과 운전대 "다음" 처리. */
class LessonCommandTest {

    /** L2 1주차 화요일 출근 수업: warmup, intro-0, practice-0, intro-1, practice-1, roleplay, personal, wrap-up */
    private val plan = MockCurriculumRepository().lessonPlan(Level.L2, week = 1, lessonNumber = 3)

    private class Harness(var state: LessonState) {
        fun send(event: LessonEvent): List<LessonEffect> {
            val t = LessonEngine.reduce(state, event)
            state = t.state
            return t.effects
        }
        fun speechDone() = send(SpeechFinished(state.audioToken))
        fun say(text: String) = send(AnswerRecognized(state.audioToken, text))
    }

    /** 첫 질문을 듣고 말할 차례가 된 상태. */
    private fun answering(mode: LearningMode = LearningMode.SHORT_ANSWER, startItem: Int = 0, tally: LessonTally = LessonTally()): Harness {
        val h = Harness(LessonEngine.initial(plan, mode, startItem, tally))
        h.send(Start(0))
        h.speechDone()
        assertEquals(LessonPhase.ANSWER, h.state.phase)
        return h
    }

    @Test
    fun `say that again repeats the same question without using a retry`() {
        val h = answering()
        val prompt = h.state.turn!!.prompt
        val speak = h.say("Sorry, can you say that again?").single() as Speak
        assertEquals(prompt, speak.text)
        assertEquals(LessonPhase.PROMPT, h.state.phase)
        assertEquals(0, h.state.retries)
        assertEquals(0, h.state.itemIndex)
        assertTrue(h.speechDone().single() is Listen) // 다시 듣는다
    }

    @Test
    fun `skip moves to the next activity and saves the checkpoint`() {
        val h = answering()
        val effects = h.say("Skip this one")
        assertEquals(1, h.state.itemIndex)
        assertEquals(1, effects.filterIsInstance<SaveCheckpoint>().single().itemIndex)
        assertTrue(effects.filterIsInstance<Speak>().single().text.startsWith("Okay, next one."))
        assertEquals(0, h.state.tally.words) // 건너뛴 턴은 말한 양에 넣지 않는다
    }

    @Test
    fun `slower lowers the speech rate step by step and faster brings it back`() {
        val h = answering()
        val base = plan.speechRate
        val first = h.say("Slow down please").single() as Speak
        assertEquals(base * 0.85f, first.rate, 0.001f)
        h.speechDone()
        h.say("천천히")
        h.speechDone()
        val third = h.say("Too fast").single() as Speak
        assertTrue(third.text.startsWith("This is my slowest speed."))
        assertEquals(base * 0.7f, third.rate, 0.001f)
        h.speechDone()
        val faster = h.say("Speed up").single() as Speak
        assertEquals(base * 0.85f, faster.rate, 0.001f)
        // 속도는 다음 활동에도 이어진다.
        h.speechDone()
        val next = h.say("Next").filterIsInstance<Speak>().single()
        assertEquals(base * 0.85f, next.rate, 0.001f)
    }

    @Test
    fun `in korean explains the current phrase in korean then asks again`() {
        val h = answering(startItem = 1) // intro-0 (따라 말하기)
        val speak = h.say("What does that mean?").single() as Speak
        val korean = plan.items[1].target.korean.trimEnd('.')
        assertTrue(speak.text, speak.text.contains("'$korean'"))
        assertTrue(speak.text.endsWith(h.state.turn!!.prompt))
    }

    @Test
    fun `i dont know gets a demonstration to repeat and breaks the combo without penalty`() {
        val h = answering(tally = LessonTally(combo = 3, maxCombo = 3, words = 10))
        val expected = h.state.turn!!.expectedAnswer
        val speak = h.say("I don't know").single() as Speak
        assertTrue(speak.text.contains(expected))
        assertEquals(ActivityType.REPEAT, h.state.turn!!.activity)
        assertEquals(0, h.state.tally.combo)
        assertEquals(3, h.state.tally.maxCombo)
        assertEquals(10, h.state.tally.words)

        h.speechDone()
        val reply = h.say(expected).single() as Speak
        assertEquals(1, h.state.tally.combo)
        assertEquals(Earcon.Combo(1), reply.cue)
    }

    @Test
    fun `thats enough for today jumps to the wrap up`() {
        val h = answering()
        val effects = h.say("That's enough for today")
        val wrap = plan.items.indexOfLast { it.kind == ItemKind.WRAP_UP }
        assertEquals(wrap, h.state.itemIndex)
        assertEquals(ActivityType.LISTEN, h.state.turn!!.activity)
        assertTrue(effects.filterIsInstance<Speak>().single().text.startsWith("Okay, let's wrap up for today."))
        h.speechDone()
        assertEquals(LessonPhase.CLOSING, h.state.phase)
    }

    @Test
    fun `listen only keeps the lesson going without the microphone`() {
        val h = answering()
        h.say("I'll just listen")
        assertTrue(h.state.listenOnly)
        assertTrue(h.state.tally.listenOnly)
        assertEquals(0, h.state.itemIndex)
        assertEquals(ActivityType.LISTEN, h.state.turn!!.activity)
        // 다음 활동도 듣기로 진행하고 마이크를 열지 않는다.
        repeat(3) { assertTrue(h.speechDone().none { it is Listen }) }
        // 침묵 구간은 그대로 침묵.
        h.send(LessonEvent.SituationChanged(LearningMode.SILENT))
        h.send(InterruptionsChanged(setOf(Interruption.TURN_SIGNAL), 0))
        h.send(InterruptionsChanged(emptySet(), 0))
        h.send(LessonEvent.Tick(60_000))
        assertEquals(LessonPhase.QUIET, h.state.phase)
    }

    @Test
    fun `wait pauses with the pause cue until the user resumes`() {
        val h = answering()
        assertEquals(listOf(StopAudio, PlayEarcon(Earcon.Pause)), h.say("Hold on"))
        assertEquals(LessonPhase.PAUSED, h.state.phase)
        assertTrue(h.state.userPaused)
        h.send(LessonEvent.Tick(100_000))
        assertEquals(LessonPhase.PAUSED, h.state.phase)
    }

    @Test
    fun `help lists commands then asks again`() {
        val h = answering()
        val speak = h.say("What can I say?").single() as Speak
        assertTrue(speak.text.startsWith(LessonEngine.HELP_LINE))
    }

    @Test
    fun `a normal answer is still graded`() {
        val h = answering()
        val effects = h.say("It was great. I went hiking.")
        assertTrue(effects.single() is LessonEffect.RequestFeedback)
    }

    @Test
    fun `steering wheel next skips even while the coach is talking but not while paused`() {
        val h = Harness(LessonEngine.initial(plan, LearningMode.SHORT_ANSWER))
        h.send(Start(0))
        assertEquals(LessonPhase.PROMPT, h.state.phase)
        val effects = h.send(LessonEvent.Command(VoiceCommand.NEXT))
        assertEquals(StopAudio, effects.first())
        assertEquals(1, h.state.itemIndex)

        h.send(InterruptionsChanged(setOf(Interruption.PHONE_CALL), 0))
        assertTrue(h.send(LessonEvent.Command(VoiceCommand.NEXT)).isEmpty())
        assertEquals(1, h.state.itemIndex)
    }

    @Test
    fun `endless commands on one turn move on instead of looping`() {
        val h = answering()
        repeat(LessonEngine.MAX_COMMANDS_PER_TURN) {
            h.say("Again")
            h.speechDone()
            assertEquals(0, h.state.itemIndex)
        }
        h.say("Again")
        assertEquals(1, h.state.itemIndex)
        assertFalse(h.state.turn == null)
    }
}

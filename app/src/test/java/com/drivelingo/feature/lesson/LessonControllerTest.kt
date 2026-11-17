package com.drivelingo.feature.lesson

import com.drivelingo.core.curriculum.MockCurriculumRepository
import com.drivelingo.core.driving.DrivingMonitor
import com.drivelingo.core.progress.InMemoryStore
import com.drivelingo.core.progress.ProgressRepository
import com.drivelingo.core.speech.SimulatedVoiceInput
import com.drivelingo.core.speech.SimulatedVoiceOutput
import com.drivelingo.core.speech.VoiceSimulation
import com.drivelingo.core.vehicle.mock.DrivingPreset
import com.drivelingo.core.vehicle.mock.MockDrivingSignalSource
import com.drivelingo.feature.lesson.coach.MockEnglishCoach
import com.drivelingo.feature.lesson.engine.LessonPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LessonControllerTest {

    private fun TestScope.controller(): Pair<LessonController, MockDrivingSignalSource> {
        val scope = backgroundScope
        val clock = { testScheduler.currentTime }
        val mock = MockDrivingSignalSource(scope, DrivingPreset.PARKED)
        val sim = VoiceSimulation()
        val controller = LessonController(
            drivingMonitor = DrivingMonitor(mock, scope, clock),
            voiceOutput = SimulatedVoiceOutput(sim),
            voiceInput = SimulatedVoiceInput(sim),
            coach = MockEnglishCoach(sim),
            audioFocus = mock,
            curriculum = MockCurriculumRepository(),
            progress = ProgressRepository(InMemoryStore()),
            appScope = scope,
            clock = clock,
        )
        return controller to mock
    }

    @Test
    fun `nothing starts until the user presses start or play`() = runTest {
        val (lessons, _) = controller()
        runCurrent()
        assertNull(lessons.session.value)
        lessons.pause()
        lessons.repeat()
        lessons.togglePause()
        assertNull("pause/repeat must never start a lesson", lessons.session.value)
    }

    @Test
    fun `screen and media buttons share the same running lesson`() = runTest {
        val (lessons, _) = controller()
        val fromScreen = lessons.startOrAttach()
        lessons.play()
        assertSame(fromScreen, lessons.session.value)
        assertSame(fromScreen, lessons.startOrAttach())
    }

    @Test
    fun `media pause and play pause and resume the lesson`() = runTest {
        val (lessons, _) = controller()
        val session = lessons.startOrAttach()
        runCurrent()
        lessons.pause()
        runCurrent()
        assertTrue(session.state.value.userPaused)
        assertEquals(LessonPhase.PAUSED, session.state.value.phase)
        lessons.play()
        runCurrent()
        assertTrue(!session.state.value.userPaused)
    }

    @Test
    fun `leaving an old screen does not stop a newer lesson`() = runTest {
        val (lessons, _) = controller()
        val first = lessons.startOrAttach()
        lessons.stop()
        assertEquals(LessonPhase.STOPPED, first.state.value.phase)

        val second = lessons.startOrAttach()
        assertNotSame(first, second)
        lessons.stop(only = first)
        assertTrue(!second.state.value.isTerminal)
    }

    @Test
    fun `ignition off stops the lesson and saves a checkpoint`() = runTest {
        val (lessons, mock) = controller()
        val session = lessons.startOrAttach()
        runCurrent()
        mock.applyPreset(DrivingPreset.IGNITION_OFF)
        runCurrent()
        assertEquals(LessonPhase.STOPPED, session.state.value.phase)
    }
}

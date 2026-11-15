package com.drivelingo.feature.lesson

import com.drivelingo.core.curriculum.Level
import com.drivelingo.core.curriculum.MockCurriculumRepository
import com.drivelingo.core.driving.DrivingSituation
import com.drivelingo.core.driving.DrivingStatus
import com.drivelingo.core.driving.Interruption
import com.drivelingo.core.driving.LearningMode
import com.drivelingo.core.vehicle.DrivingSignals
import com.drivelingo.core.vehicle.mock.DrivingPreset
import com.drivelingo.feature.lesson.engine.LessonEngine
import com.drivelingo.feature.lesson.engine.LessonEvent
import com.drivelingo.feature.lesson.engine.LessonState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonUiMapperTest {

    private val plan = MockCurriculumRepository().lessonPlan(Level.L2, 1, 3)

    private fun status(preset: DrivingPreset, situation: DrivingSituation): DrivingStatus {
        val signals = preset.applyTo(DrivingSignals())
        return DrivingStatus(situation, situation, signals.uxRestrictions, signals)
    }

    private fun run(mode: LearningMode, startItem: Int, vararg events: LessonEvent): LessonState =
        events.fold(LessonEngine.initial(plan, mode, startItem)) { s, e -> LessonEngine.reduce(s, e).state }

    @Test
    fun `long answer at a red light shows the amber prompt and badge`() {
        val s0 = run(LearningMode.LONG_ANSWER, 2, LessonEvent.Start(0))
        val s = LessonEngine.reduce(s0, LessonEvent.SpeechFinished(s0.audioToken)).state
        val ui = LessonUiMapper.map(s, status(DrivingPreset.STOPPED_AT_LIGHT, DrivingSituation.STOPPED))
        assertEquals(VoiceVisual.LONG_SPEAK_TURN, ui.visual)
        assertEquals("길게 말해 보세요", ui.headline)
        assertEquals("정차 질문 · 신호 대기", ui.badge)
    }

    @Test
    fun `text details are shown only while parked`() {
        val s = run(LearningMode.SHORT_ANSWER, 0, LessonEvent.Start(0))
        assertNull(LessonUiMapper.map(s, status(DrivingPreset.CRUISING, DrivingSituation.CRUISING)).detail)
        assertNotNull(LessonUiMapper.map(s, status(DrivingPreset.PARKED, DrivingSituation.PARKED)).detail)
    }

    @Test
    fun `paused state names the highest priority reason`() {
        val s = run(
            LearningMode.SHORT_ANSWER, 0, LessonEvent.Start(0),
            LessonEvent.InterruptionsChanged(setOf(Interruption.TURN_SIGNAL, Interruption.PHONE_CALL), 0),
        )
        val ui = LessonUiMapper.map(s, status(DrivingPreset.CRUISING, DrivingSituation.CRUISING))
        assertEquals(VoiceVisual.PAUSED, ui.visual)
        assertEquals("잠시 멈춤", ui.headline)
        assertTrue(ui.caption!!.startsWith("전화"))
        assertTrue(ui.isPaused)
        assertFalse(ui.canTogglePause) // 안전 사유로 멈춘 동안은 사용자가 재개할 수 없다
    }
}

package com.drivelingo.feature.media

import android.support.v4.media.session.PlaybackStateCompat
import com.drivelingo.core.curriculum.Level
import com.drivelingo.core.curriculum.MockCurriculumRepository
import com.drivelingo.core.driving.Interruption
import com.drivelingo.core.driving.LearningMode
import com.drivelingo.feature.lesson.engine.LessonPhase
import com.drivelingo.feature.lesson.engine.LessonState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonPlaybackMapperTest {

    private val plan = MockCurriculumRepository().lessonPlan(Level.L2, 1, 1, emptyList())
    private fun state(phase: LessonPhase, interruptions: Set<Interruption> = emptySet(), userPaused: Boolean = false) =
        LessonState(plan = plan, phase = phase, mode = LearningMode.SHORT_ANSWER, signalInterruptions = interruptions, userPaused = userPaused)

    private fun LessonPlayback.can(action: Long) = actions and action != 0L

    @Test
    fun `no lesson is stopped and can only be started`() {
        val p = LessonPlaybackMapper.map(null)
        assertEquals(PlaybackStateCompat.STATE_STOPPED, p.state)
        assertTrue(p.can(PlaybackStateCompat.ACTION_PLAY))
        assertFalse(p.can(PlaybackStateCompat.ACTION_PAUSE))
    }

    @Test
    fun `running lesson can be paused, repeated and skipped`() {
        val p = LessonPlaybackMapper.map(state(LessonPhase.ANSWER))
        assertEquals(PlaybackStateCompat.STATE_PLAYING, p.state)
        assertTrue(p.can(PlaybackStateCompat.ACTION_PAUSE))
        assertTrue(p.can(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS))
        assertTrue(p.can(PlaybackStateCompat.ACTION_SKIP_TO_NEXT))
        assertEquals("말할 차례", p.subtitle)
    }

    @Test
    fun `paused lesson shows the top reason and can be resumed`() {
        val p = LessonPlaybackMapper.map(state(LessonPhase.PAUSED, setOf(Interruption.TURN_SIGNAL, Interruption.PHONE_CALL)))
        assertEquals(PlaybackStateCompat.STATE_PAUSED, p.state)
        assertTrue(p.can(PlaybackStateCompat.ACTION_PLAY))
        assertFalse(p.can(PlaybackStateCompat.ACTION_SKIP_TO_NEXT)) // 멈춘 동안에는 건너뛰지 않는다
        assertEquals("잠시 멈춤 · 전화", p.subtitle)
    }

    @Test
    fun `finished lesson is stopped`() {
        val p = LessonPlaybackMapper.map(state(LessonPhase.FINISHED))
        assertEquals(PlaybackStateCompat.STATE_STOPPED, p.state)
        assertEquals("오늘 수업 완료", p.subtitle)
    }

    @Test
    fun `media subtitle never shows lesson sentences or numbers`() {
        LessonPhase.entries.forEach { phase ->
            val subtitle = LessonPlaybackMapper.map(state(phase)).subtitle
            assertFalse("$phase: $subtitle", subtitle.any { it.isDigit() })
            assertFalse("$phase: $subtitle", subtitle.any { it in 'A'..'Z' || it in 'a'..'z' })
        }
    }
}

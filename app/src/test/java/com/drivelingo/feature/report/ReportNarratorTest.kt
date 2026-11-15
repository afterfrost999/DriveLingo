package com.drivelingo.feature.report

import com.drivelingo.core.driving.Interruption
import com.drivelingo.core.driving.LearningMode
import com.drivelingo.core.progress.LessonTally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportNarratorTest {

    @Test
    fun `driving line summarizes the dominant mode and long answers`() {
        val tally = LessonTally(turnsByMode = mapOf(LearningMode.SHORT_ANSWER to 6, LearningMode.LONG_ANSWER to 1))
        assertEquals("오늘 길은 막힘없이 달려서 짧게 대답 위주였고, 신호 대기에서 한 번 길게 말했어요", ReportNarrator.drivingLine(tally))
    }

    @Test
    fun `driving line without long answers ends the sentence`() {
        val tally = LessonTally(turnsByMode = mapOf(LearningMode.REPEAT_AFTER to 4))
        assertEquals("오늘 길은 가다 서다가 많아서 따라 말하기 위주였어요", ReportNarrator.drivingLine(tally))
    }

    @Test
    fun `pause line uses the right particle`() {
        val tally = LessonTally(pausesByReason = mapOf(Interruption.TURN_SIGNAL to 2))
        assertEquals("방향지시등으로 2번 멈췄지만 콤보는 그대로 이어졌어요", ReportNarrator.pauseLine(tally))
        assertEquals("전화로", ReportNarrator.withParticle("전화"))
        assertEquals("급제동으로", ReportNarrator.withParticle("급제동"))
        assertNull(ReportNarrator.pauseLine(LessonTally()))
    }
}

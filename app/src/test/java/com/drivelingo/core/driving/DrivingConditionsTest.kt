package com.drivelingo.core.driving

import com.drivelingo.core.driving.DrivingCondition.NIGHT
import com.drivelingo.core.driving.DrivingCondition.RAIN
import com.drivelingo.core.driving.DrivingCondition.UNFAMILIAR_ROUTE
import com.drivelingo.core.vehicle.DrivingSignals
import org.junit.Assert.assertEquals
import org.junit.Test

class DrivingConditionsTest {

    @Test
    fun `wipers headlamps and a new route are detected, unknown means none`() {
        assertEquals(emptySet<DrivingCondition>(), DrivingConditions.detect(DrivingSignals()))
        assertEquals(emptySet<DrivingCondition>(), DrivingConditions.detect(DrivingSignals(wipersOn = false, headlampsOn = false)))
        assertEquals(
            setOf(RAIN, NIGHT, UNFAMILIAR_ROUTE),
            DrivingConditions.detect(DrivingSignals(wipersOn = true, headlampsOn = true, unfamiliarRoute = true)),
        )
    }

    @Test
    fun `each driving situation drops exactly one step`() {
        val rain = setOf(RAIN)
        assertEquals(LearningMode.SHORT_ANSWER, DrivingConditions.adjust(DrivingSituation.STOPPED, rain))
        assertEquals(LearningMode.REPEAT_AFTER, DrivingConditions.adjust(DrivingSituation.CRUISING, rain))
        assertEquals(LearningMode.LISTEN_ONLY, DrivingConditions.adjust(DrivingSituation.STOP_AND_GO, rain))
        assertEquals(LearningMode.SILENT, DrivingConditions.adjust(DrivingSituation.COMPLEX, rain))
        assertEquals(LearningMode.SILENT, DrivingConditions.adjust(DrivingSituation.DEPARTING, rain))
    }

    @Test
    fun `several conditions still drop only one step`() {
        assertEquals(LearningMode.REPEAT_AFTER, DrivingConditions.adjust(DrivingSituation.CRUISING, setOf(RAIN, NIGHT, UNFAMILIAR_ROUTE)))
    }

    @Test
    fun `parked review and clear weather are unchanged`() {
        assertEquals(LearningMode.DETAILED_REVIEW, DrivingConditions.adjust(DrivingSituation.PARKED, setOf(RAIN, NIGHT)))
        DrivingSituation.entries.forEach { assertEquals(it.mode, DrivingConditions.adjust(it, emptySet())) }
    }

    @Test
    fun `intermittent wipers and tunnels do not flip the mode back and forth`() {
        val latch = ConditionLatch()
        assertEquals(setOf(RAIN), latch.update(setOf(RAIN), 0))
        // 간헐 와이퍼가 멈춘 사이에도 비로 본다.
        assertEquals(setOf(RAIN), latch.update(emptySet(), 10_000))
        assertEquals(setOf(RAIN), latch.update(emptySet(), 120_000))
        assertEquals(emptySet<DrivingCondition>(), latch.update(emptySet(), 120_001))
        // 터널을 지나며 잠깐 켜진 전조등은 1분 뒤 풀린다.
        latch.update(setOf(NIGHT), 200_000)
        assertEquals(setOf(NIGHT), latch.update(emptySet(), 230_000))
        assertEquals(emptySet<DrivingCondition>(), latch.update(emptySet(), 260_001))
    }

    @Test
    fun `status mode applies the adjustment`() {
        val signals = DrivingSignals(wipersOn = true)
        val status = DrivingStatus(DrivingSituation.CRUISING, DrivingSituation.CRUISING, null, signals, setOf(RAIN))
        assertEquals(LearningMode.REPEAT_AFTER, status.mode)
    }
}

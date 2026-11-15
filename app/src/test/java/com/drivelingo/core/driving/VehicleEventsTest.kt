package com.drivelingo.core.driving

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleEventsTest {

    @Test
    fun `new warning interrupts only for the hold time`() {
        val gate = NewWarningGate(holdMs = 5_000)
        gate.update(setOf("ABS"), nowMs = 1_000)
        assertTrue(gate.isActive(1_000))
        assertTrue(gate.isActive(5_999))
        assertFalse(gate.isActive(6_000))
    }

    @Test
    fun `warning that stays on does not block the lesson forever`() {
        val gate = NewWarningGate(holdMs = 5_000)
        gate.update(setOf("ENGINE_TEMPERATURE"), nowMs = 0)
        gate.update(setOf("ENGINE_TEMPERATURE"), nowMs = 3_000)
        gate.update(setOf("ENGINE_TEMPERATURE"), nowMs = 30_000)
        assertFalse(gate.isActive(30_000))
    }

    @Test
    fun `another warning turning on interrupts again`() {
        val gate = NewWarningGate(holdMs = 5_000)
        gate.update(setOf("ABS"), nowMs = 0)
        gate.update(setOf("ABS", "MASTER_WARNING"), nowMs = 20_000)
        assertTrue(gate.isActive(20_000))
    }

    @Test
    fun `warning turning off and on again counts as new`() {
        val gate = NewWarningGate(holdMs = 5_000)
        gate.update(setOf("FCA"), nowMs = 0)
        gate.update(emptySet(), nowMs = 10_000)
        gate.update(setOf("FCA"), nowMs = 12_000)
        assertTrue(gate.isActive(12_000))
    }
}

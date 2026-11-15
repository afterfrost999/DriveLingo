package com.drivelingo.core.driving

import com.drivelingo.core.vehicle.Gear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // ---- 급제동 ----

    @Test
    fun `hard deceleration with brake pressed is sudden braking`() {
        val d = SuddenBrakingDetector(thresholdKmhPerSec = 13f, holdMs = 1_000)
        d.onSpeed(0, 60f, brakePedalPressed = true)
        d.onSpeed(200, 56f, brakePedalPressed = true) // 20 km/h/s
        assertTrue(d.isActive(200))
        assertTrue(d.isActive(1_199))
        assertFalse(d.isActive(1_200))
    }

    @Test
    fun `gentle deceleration is not sudden braking`() {
        val d = SuddenBrakingDetector(thresholdKmhPerSec = 13f)
        d.onSpeed(0, 60f, brakePedalPressed = true)
        d.onSpeed(1_000, 55f, brakePedalPressed = true) // 5 km/h/s
        assertFalse(d.isActive(1_000))
    }

    @Test
    fun `deceleration without brake pedal is not sudden braking, unknown pedal still counts`() {
        val released = SuddenBrakingDetector()
        released.onSpeed(0, 60f, brakePedalPressed = false)
        released.onSpeed(200, 50f, brakePedalPressed = false)
        assertFalse(released.isActive(200))

        val unknown = SuddenBrakingDetector()
        unknown.onSpeed(0, 60f, brakePedalPressed = null)
        unknown.onSpeed(200, 50f, brakePedalPressed = null)
        assertTrue(unknown.isActive(200))
    }

    @Test
    fun `stale speed sample is not used for deceleration`() {
        val d = SuddenBrakingDetector(maxGapMs = 1_500)
        d.onSpeed(0, 60f, brakePedalPressed = true)
        d.onSpeed(5_000, 0f, brakePedalPressed = true)
        assertFalse(d.isActive(5_000))
    }

    // ---- 출발 ----

    @Test
    fun `departure is counted from leaving park`() {
        val t = DepartureTracker()
        t.onGear(Gear.PARK, nowMs = 0)
        assertNull(t.minutesSinceDeparture(0))
        t.onGear(Gear.DRIVE, nowMs = 10_000)
        assertEquals(0, t.minutesSinceDeparture(10_000))
        assertEquals(2, t.minutesSinceDeparture(10_000 + 125_000))
        t.onGear(Gear.PARK, nowMs = 200_000)
        assertNull(t.minutesSinceDeparture(200_000))
    }

    @Test
    fun `departure is unknown when park was never seen, and brief unknown gear is ignored`() {
        val t = DepartureTracker()
        t.onGear(Gear.DRIVE, nowMs = 0)
        assertNull(t.minutesSinceDeparture(0))

        t.onGear(Gear.PARK, nowMs = 1_000)
        t.onGear(null, nowMs = 2_000)
        t.onGear(Gear.UNKNOWN, nowMs = 2_500)
        t.onGear(Gear.DRIVE, nowMs = 3_000)
        assertEquals(0, t.minutesSinceDeparture(3_000))
    }
}

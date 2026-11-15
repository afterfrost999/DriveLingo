package com.drivelingo.core.vehicle.car

import com.drivelingo.core.vehicle.DrivingSignals
import com.drivelingo.core.vehicle.TurnSignal
import com.drivelingo.core.vehicle.UxRestrictions
import com.drivelingo.core.vehicle.mock.DrivingPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarSignalMergerTest {

    /** Mock 은 일부러 실제 값과 다른 값을 넣어 둔다. 실제 소스가 있는 신호에 Mock 값이 새어 나오면 안 된다. */
    private val mock = DrivingPreset.CRUISING.applyTo(DrivingSignals()).copy(
        turnSignal = TurnSignal.RIGHT,
        vehicleWarningActive = true,
        networkAvailable = false,
    )

    @Test
    fun `real sources win over mock`() {
        val merged = CarSignalMerger.merge(
            mock,
            CarReadings(ux = UxRestrictions.UNRESTRICTED, vehicle = PleosVehicleState(turnSignal = TurnSignal.LEFT)),
            DerivedEvents(vehicleWarning = false),
        )
        assertEquals(UxRestrictions.UNRESTRICTED, merged.uxRestrictions)
        assertEquals(TurnSignal.LEFT, merged.turnSignal)
        assertFalse(merged.vehicleWarningActive)
    }

    @Test
    fun `unreadable real signals fall back to safe defaults, not mock`() {
        val merged = CarSignalMerger.merge(mock, CarReadings(), DerivedEvents())
        assertNull("unknown UX must stay unknown (safe restricted mode)", merged.uxRestrictions)
        assertEquals(TurnSignal.OFF, merged.turnSignal)
        assertFalse(merged.vehicleWarningActive)
    }

    @Test
    fun `signals without a real source still come from mock`() {
        val merged = CarSignalMerger.merge(mock, CarReadings(), DerivedEvents())
        assertFalse(merged.networkAvailable)
        assertEquals(mock.minutesToArrival, merged.minutesToArrival)
    }

    @Test
    fun `derived warning event is passed through`() {
        val merged = CarSignalMerger.merge(mock, CarReadings(), DerivedEvents(vehicleWarning = true))
        assertTrue(merged.vehicleWarningActive)
    }
}

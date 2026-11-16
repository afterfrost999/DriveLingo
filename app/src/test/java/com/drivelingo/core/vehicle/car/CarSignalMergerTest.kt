package com.drivelingo.core.vehicle.car

import com.drivelingo.core.audio.AudioEnvironment
import com.drivelingo.core.vehicle.AudioFocus
import com.drivelingo.core.vehicle.DrivingSignals
import com.drivelingo.core.vehicle.Gear
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
            CarReadings(
                ux = UxRestrictions.UNRESTRICTED,
                vehicle = PleosVehicleState(turnSignal = TurnSignal.LEFT),
                motion = CarMotion(speedKmh = 0f, gear = Gear.PARK),
            ),
            DerivedEvents(vehicleWarning = false, minutesSinceDeparture = null),
        )
        assertEquals(UxRestrictions.UNRESTRICTED, merged.uxRestrictions)
        assertEquals(TurnSignal.LEFT, merged.turnSignal)
        assertFalse(merged.vehicleWarningActive)
        assertEquals(0f, merged.speedKmh)
        assertEquals(Gear.PARK, merged.gear)
        assertNull(merged.minutesSinceDeparture)
    }

    @Test
    fun `unreadable real signals fall back to safe defaults, not mock`() {
        val merged = CarSignalMerger.merge(mock, CarReadings(), DerivedEvents())
        assertNull("unknown UX must stay unknown (safe restricted mode)", merged.uxRestrictions)
        assertEquals(TurnSignal.OFF, merged.turnSignal)
        assertFalse(merged.vehicleWarningActive)
        assertNull("unknown speed must stay unknown", merged.speedKmh)
        assertEquals(Gear.UNKNOWN, merged.gear)
        assertFalse(merged.suddenBraking)
    }

    @Test
    fun `vehicle gear codes map to app gears`() {
        assertEquals(Gear.PARK, CarPropertyReader.gearFromVehicleGear(4))
        assertEquals(Gear.REVERSE, CarPropertyReader.gearFromVehicleGear(2))
        assertEquals(Gear.NEUTRAL, CarPropertyReader.gearFromVehicleGear(1))
        assertEquals(Gear.DRIVE, CarPropertyReader.gearFromVehicleGear(8))
        assertEquals(Gear.DRIVE, CarPropertyReader.gearFromVehicleGear(16)) // 수동 1단
        assertEquals(Gear.UNKNOWN, CarPropertyReader.gearFromVehicleGear(0))
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

    @Test
    fun `audio environment comes from the real audio source`() {
        val noisyMock = mock.copy(audioFocus = AudioFocus.LOST, phoneCallActive = true, navVoiceGuidanceActive = true)
        val quiet = CarSignalMerger.merge(noisyMock, CarReadings(), DerivedEvents())
        assertEquals(AudioFocus.GAINED, quiet.audioFocus)
        assertFalse(quiet.phoneCallActive)
        assertFalse(quiet.navVoiceGuidanceActive)

        val real = CarReadings(audio = AudioEnvironment(AudioFocus.LOST_TRANSIENT, navGuidanceActive = true, phoneCallActive = false))
        val merged = CarSignalMerger.merge(mock, real, DerivedEvents())
        assertEquals(AudioFocus.LOST_TRANSIENT, merged.audioFocus)
        assertTrue(merged.navVoiceGuidanceActive)
    }

    @Test
    fun `wipers and headlamps come from the vehicle, unknown stays unknown`() {
        val noisyMock = mock.copy(wipersOn = true, headlampsOn = true, unfamiliarRoute = true)
        val read = CarSignalMerger.merge(
            noisyMock,
            CarReadings(vehicle = PleosVehicleState(wipersOn = false, headlampsOn = true)),
            DerivedEvents(),
        )
        assertEquals(false, read.wipersOn)
        assertEquals(true, read.headlampsOn)
        assertTrue(read.unfamiliarRoute) // 초행길은 내비 연동 전이라 Mock

        val unknown = CarSignalMerger.merge(noisyMock, CarReadings(), DerivedEvents())
        assertNull(unknown.wipersOn)
        assertNull(unknown.headlampsOn)
    }
}

package com.drivelingo.core.driving

import com.drivelingo.core.vehicle.AudioFocus
import com.drivelingo.core.vehicle.DrivingSignals
import com.drivelingo.core.vehicle.Gear
import com.drivelingo.core.vehicle.TurnSignal
import com.drivelingo.core.vehicle.UxRestrictions
import com.drivelingo.core.vehicle.mock.DrivingPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SituationClassifierTest {

    private fun classify(signals: DrivingSignals, history: SpeedHistory = SpeedHistory()) =
        SituationClassifier.classify(signals, history)

    private fun preset(p: DrivingPreset) = p.applyTo(DrivingSignals())

    @Test
    fun `each preset maps to its planned situation`() {
        assertEquals(DrivingSituation.PARKED, classify(preset(DrivingPreset.PARKED)))
        assertEquals(DrivingSituation.STOPPED, classify(preset(DrivingPreset.STOPPED_AT_LIGHT)))
        assertEquals(DrivingSituation.CRUISING, classify(preset(DrivingPreset.CRUISING)))
        assertEquals(DrivingSituation.COMPLEX, classify(preset(DrivingPreset.COMPLEX)))
        assertEquals(DrivingSituation.DEPARTING, classify(preset(DrivingPreset.DEPARTING)))
        assertEquals(DrivingSituation.ARRIVING, classify(preset(DrivingPreset.ARRIVING)))
        assertEquals(DrivingSituation.UNKNOWN, classify(preset(DrivingPreset.UNKNOWN)))
        assertEquals(DrivingSituation.PARKED, classify(preset(DrivingPreset.IGNITION_OFF)))
    }

    @Test
    fun `stop and go is detected from speed history, not the instant speed`() {
        val history = SpeedHistory()
        var t = 0L
        repeat(3) {
            history.add(t, 0f); t += 2_000
            history.add(t, 20f); t += 3_000
        }
        val signals = preset(DrivingPreset.STOP_AND_GO).copy(speedKmh = 0f)
        assertEquals(DrivingSituation.STOP_AND_GO, classify(signals, history))
    }

    @Test
    fun `a single red light stop between cruising is not stop and go`() {
        val history = SpeedHistory()
        history.add(0, 0f)
        history.add(10_000, 82f)
        history.add(30_000, 0f)
        history.add(50_000, 82f)
        assertEquals(DrivingSituation.CRUISING, classify(preset(DrivingPreset.CRUISING), history))
    }

    @Test
    fun `unknown platform restrictions mean safe restricted mode`() {
        val signals = preset(DrivingPreset.PARKED).copy(uxRestrictions = null)
        val situation = classify(signals)
        assertEquals(DrivingSituation.UNKNOWN, situation)
        assertEquals(LearningMode.LISTEN_ONLY, situation.mode)
    }

    @Test
    fun `extra signals can only make the decision more conservative`() {
        // 플랫폼은 제한 없음이라고 하지만 속도가 5km/h 를 넘는다 → 주행으로 본다.
        val signals = preset(DrivingPreset.PARKED).copy(speedKmh = 40f, gear = Gear.DRIVE)
        assertEquals(DrivingSituation.CRUISING, classify(signals))
    }

    @Test
    fun `platform unrestricted with auto hold is stopped, not parked`() {
        val signals = preset(DrivingPreset.PARKED).copy(gear = Gear.DRIVE)
        assertEquals(DrivingSituation.STOPPED, classify(signals))
    }

    @Test
    fun `missing speed while restricted is unknown`() {
        val signals = preset(DrivingPreset.CRUISING).copy(speedKmh = null)
        assertEquals(DrivingSituation.UNKNOWN, classify(signals))
    }

    @Test
    fun `reversing is treated as a silent maneuver`() {
        val signals = preset(DrivingPreset.CRUISING).copy(gear = Gear.REVERSE, speedKmh = 3f)
        assertEquals(LearningMode.SILENT, classify(signals).mode)
    }

    @Test
    fun `stabilizer switches to stricter modes immediately and relaxes only after a stable period`() {
        val stabilizer = SituationStabilizer(minStableMs = 3_000)
        assertEquals(DrivingSituation.CRUISING, stabilizer.update(DrivingSituation.CRUISING, 0))
        assertEquals(DrivingSituation.COMPLEX, stabilizer.update(DrivingSituation.COMPLEX, 100))
        assertEquals(DrivingSituation.COMPLEX, stabilizer.update(DrivingSituation.CRUISING, 200))
        assertEquals(DrivingSituation.COMPLEX, stabilizer.update(DrivingSituation.CRUISING, 3_100))
        assertEquals(DrivingSituation.CRUISING, stabilizer.update(DrivingSituation.CRUISING, 3_200))
    }

    @Test
    fun `interruptions are detected from signals with a defined priority`() {
        val signals = preset(DrivingPreset.CRUISING).copy(
            turnSignal = TurnSignal.LEFT,
            navVoiceGuidanceActive = true,
            audioFocus = AudioFocus.LOST_TRANSIENT_CAN_DUCK,
            vehicleWarningActive = true,
        )
        val active = InterruptionDetector.detect(signals)
        assertTrue(Interruption.TURN_SIGNAL in active)
        assertTrue(Interruption.AUDIO_FOCUS_TRANSIENT in active)
        assertEquals(Interruption.VEHICLE_WARNING, Interruption.primary(active))
        assertTrue(InterruptionDetector.detect(preset(DrivingPreset.CRUISING)).isEmpty())
    }

    @Test
    fun `restrictiveness order matches the safety intent`() {
        val ordered = listOf(
            DrivingSituation.PARKED, DrivingSituation.STOPPED, DrivingSituation.CRUISING,
            DrivingSituation.STOP_AND_GO, DrivingSituation.COMPLEX, DrivingSituation.DEPARTING,
        ).map { it.mode.restrictiveness }
        assertEquals(ordered.sorted(), ordered)
        assertEquals(UxRestrictions.FULLY_RESTRICTED, UxRestrictions.DRIVING.activeRestrictions)
    }
}

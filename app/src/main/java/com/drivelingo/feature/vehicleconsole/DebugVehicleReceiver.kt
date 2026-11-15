package com.drivelingo.feature.vehicleconsole

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.drivelingo.app.AppContainer
import com.drivelingo.core.speech.NextAnswer
import com.drivelingo.core.vehicle.AudioFocus
import com.drivelingo.core.vehicle.TurnSignal
import com.drivelingo.core.vehicle.mock.DrivingPreset

/**
 * 디버그 빌드 전용 adb 조작 수단. 예)
 * ```
 * adb shell am broadcast -a com.drivelingo.debug.VEHICLE --es preset CRUISING
 * adb shell am broadcast -a com.drivelingo.debug.VEHICLE --es turn left
 * adb shell am broadcast -a com.drivelingo.debug.VEHICLE --es network off
 * ```
 * 전체 목록은 docs/testing.md.
 */
class DebugVehicleReceiver(private val container: AppContainer) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val mock = container.mockVehicle
        val sim = container.voiceSimulation
        intent.getStringExtra("preset")?.let { name ->
            DrivingPreset.fromName(name)?.let(mock::applyPreset) ?: Log.w(TAG, "unknown preset $name")
        }
        intent.getStringExtra("turn")?.let { v ->
            val signal = when (v.lowercase()) {
                "left" -> TurnSignal.LEFT
                "right" -> TurnSignal.RIGHT
                "hazard" -> TurnSignal.HAZARD
                else -> TurnSignal.OFF
            }
            mock.update { it.copy(turnSignal = signal) }
        }
        intent.getStringExtra("nav")?.let { v ->
            if (v == "pulse") mock.pulse(4_000, { it.copy(navVoiceGuidanceActive = true) }, { it.copy(navVoiceGuidanceActive = false) })
            else mock.update { it.copy(navVoiceGuidanceActive = v.isOn()) }
        }
        intent.getStringExtra("warning")?.let { v -> mock.update { it.copy(vehicleWarningActive = v.isOn()) } }
        intent.getStringExtra("brake")?.let {
            mock.pulse(1_000, { s -> s.copy(suddenBraking = true) }, { s -> s.copy(suddenBraking = false) })
        }
        intent.getStringExtra("call")?.let { v -> mock.update { it.copy(phoneCallActive = v.isOn()) } }
        intent.getStringExtra("focus")?.let { v ->
            val focus = when (v.lowercase()) {
                "transient" -> AudioFocus.LOST_TRANSIENT
                "duck" -> AudioFocus.LOST_TRANSIENT_CAN_DUCK
                "lost" -> AudioFocus.LOST
                else -> AudioFocus.GAINED
            }
            mock.update { it.copy(audioFocus = focus) }
        }
        intent.getStringExtra("network")?.let { v -> mock.update { it.copy(networkAvailable = v.isOn()) } }
        intent.getStringExtra("answer")?.let { v ->
            sim.nextAnswer.value = when (v.lowercase()) {
                "fail" -> NextAnswer.NOT_RECOGNIZED
                "silent" -> NextAnswer.NO_SPEECH
                else -> NextAnswer.AUTO
            }
        }
        intent.getStringExtra("slowcoach")?.let { v -> sim.slowCoach.value = v.isOn() }
        Log.i(TAG, "applied ${intent.extras?.keySet()?.joinToString()} → ${container.drivingMonitor.status.value.situation}")
    }

    private fun String.isOn() = lowercase() in setOf("on", "true", "1")

    companion object {
        const val ACTION = "com.drivelingo.debug.VEHICLE"
        private const val TAG = "DebugVehicle"
    }
}

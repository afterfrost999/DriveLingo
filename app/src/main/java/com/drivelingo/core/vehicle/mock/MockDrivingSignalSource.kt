package com.drivelingo.core.vehicle.mock

import com.drivelingo.core.vehicle.AudioFocus
import com.drivelingo.core.vehicle.AudioFocusRequester
import com.drivelingo.core.vehicle.DrivingSignalSource
import com.drivelingo.core.vehicle.DrivingSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 수동으로 조작하는 Mock 차량 신호. 테스트 콘솔(feature/vehicleconsole)과
 * adb 브로드캐스트(DebugVehicleReceiver)가 이 클래스를 조작한다.
 */
class MockDrivingSignalSource(
    private val scope: CoroutineScope,
    initialPreset: DrivingPreset = DrivingPreset.PARKED,
) : DrivingSignalSource, AudioFocusRequester {

    private val _signals = MutableStateFlow(initialPreset.applyTo(DrivingSignals()))
    override val signals: StateFlow<DrivingSignals> = _signals.asStateFlow()

    private val _preset = MutableStateFlow(initialPreset)
    val preset: StateFlow<DrivingPreset> = _preset.asStateFlow()

    private var speedPatternJob: Job? = null

    fun applyPreset(preset: DrivingPreset) {
        speedPatternJob?.cancel()
        _preset.value = preset
        _signals.update { preset.applyTo(it) }
        if (preset == DrivingPreset.STOP_AND_GO) startStopAndGoPattern()
    }

    fun update(transform: (DrivingSignals) -> DrivingSignals) = _signals.update(transform)

    /** 급제동·내비 안내처럼 잠깐 켜졌다 꺼지는 신호. */
    fun pulse(durationMs: Long, on: (DrivingSignals) -> DrivingSignals, off: (DrivingSignals) -> DrivingSignals) {
        _signals.update(on)
        scope.launch {
            delay(durationMs)
            _signals.update(off)
        }
    }

    override fun requestAudioFocus(): Boolean {
        _signals.update { it.copy(audioFocus = AudioFocus.GAINED) }
        return true
    }

    override fun abandonAudioFocus() = Unit

    /** 실제 정체 구간처럼 0 ↔ 20km/h 를 반복한다. 판단은 SpeedHistory 가 속도 이력으로 한다. */
    private fun startStopAndGoPattern() {
        speedPatternJob = scope.launch {
            while (isActive) {
                _signals.update { it.copy(speedKmh = 0f) }
                delay(2_000)
                _signals.update { it.copy(speedKmh = 20f) }
                delay(3_000)
            }
        }
    }
}

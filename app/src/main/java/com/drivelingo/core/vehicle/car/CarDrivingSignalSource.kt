package com.drivelingo.core.vehicle.car

import com.drivelingo.core.driving.NewWarningGate
import com.drivelingo.core.vehicle.DrivingSignalSource
import com.drivelingo.core.vehicle.DrivingSignals
import com.drivelingo.core.vehicle.TurnSignal
import com.drivelingo.core.vehicle.UxRestrictions
import com.drivelingo.core.vehicle.mock.MockDrivingSignalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 실제 차량에서 읽은 값 묶음. 각 소스가 못 읽은 값은 null(알 수 없음)이다. */
data class CarReadings(
    /** AAOS CarUxRestrictionsManager. */
    val ux: UxRestrictions? = null,
    /** Pleos Vehicle SDK. */
    val vehicle: PleosVehicleState = PleosVehicleState(),
)

/** 실제 값에서 만든 순간 이벤트. */
data class DerivedEvents(
    val vehicleWarning: Boolean = false,
)

/**
 * 실제 값과 Mock 값을 합치는 규칙. 순수 함수라 단위 테스트한다.
 *
 * - 실제 소스가 있는 신호는 **실제 값만** 쓴다. 못 읽으면 Mock 으로 대신하지 않고 안전한 기본값을 쓴다.
 *   (Mock 으로 채우면 연동 실패가 가려진다.)
 *   - 주행 규제를 모름 → null → 안전 제한 모드
 *   - 방향지시등을 모름 → OFF. 모른다고 수업을 계속 멈출 수는 없으므로, 이 차량은 지시등 중단 없이 동작한다.
 * - 실제 소스가 아직 없는 신호(시동, 네트워크, 속도·기어, 오디오, 내비 경로)는 Mock 값을 쓴다.
 */
object CarSignalMerger {
    /** 차량 시뮬레이터 화면에 "실제 값" 으로 표시하는 신호 이름. */
    val REAL_SIGNALS = listOf("CarUx", "방향지시등", "경고등")

    fun merge(mock: DrivingSignals, real: CarReadings, events: DerivedEvents): DrivingSignals = mock.copy(
        uxRestrictions = real.ux,
        turnSignal = real.vehicle.turnSignal ?: TurnSignal.OFF,
        vehicleWarningActive = events.vehicleWarning,
    )
}

/**
 * 차량 모드 신호 소스 (`-Pdrivelingo.vehicleSource=car`).
 *
 * 실제 값([CarReadings])과 그 값에서 만든 순간 이벤트, 아직 실제 소스가 없는 신호의 Mock 값을 합친다.
 * 무엇이 실제이고 무엇이 Mock 인지는 [CarSignalMerger] 와 docs/pleos-sdk-status.md 표에 있다.
 */
class CarDrivingSignalSource(
    readings: Flow<CarReadings>,
    private val fallback: MockDrivingSignalSource,
    scope: CoroutineScope,
    private val clock: () -> Long,
) : DrivingSignalSource {

    private val warningGate = NewWarningGate()
    private var latest = CarReadings()

    private val _signals = MutableStateFlow(compute())
    override val signals: StateFlow<DrivingSignals> = _signals.asStateFlow()

    init {
        scope.launch { readings.collect(::onReadings) }
        scope.launch { fallback.signals.collect { recompute() } }
        // 순간 이벤트(경고 유지 시간 등)는 새 값이 오지 않아도 시간이 지나면 풀려야 하므로 주기적으로 다시 계산한다.
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                recompute()
            }
        }
    }

    @Synchronized
    private fun onReadings(readings: CarReadings) {
        warningGate.update(readings.vehicle.warningTelltales, clock())
        latest = readings
        recompute()
    }

    @Synchronized
    private fun recompute() {
        _signals.value = compute()
    }

    private fun compute(): DrivingSignals {
        val now = clock()
        return CarSignalMerger.merge(
            mock = fallback.signals.value,
            real = latest,
            events = DerivedEvents(vehicleWarning = warningGate.isActive(now)),
        )
    }

    private companion object {
        const val TICK_MS = 500L
    }
}

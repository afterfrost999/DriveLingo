package com.drivelingo.core.vehicle.car

import com.drivelingo.core.vehicle.DrivingSignalSource
import com.drivelingo.core.vehicle.DrivingSignals
import com.drivelingo.core.vehicle.mock.MockDrivingSignalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * 차량 모드 신호 소스 (`-Pdrivelingo.vehicleSource=car`).
 *
 * - uxRestrictions: AAOS CarUxRestrictionsManager **실연동**
 * - 나머지(기어·속도·방향지시등·경고·전화·내비): 아직 Mock. 연동 대상 API 는 docs/pleos-sdk-status.md 참고.
 *
 * Mock 값과 실제 값이 섞여 있다는 사실을 숨기지 않기 위해 이름을 "Car + Mock" 조합으로 둔다.
 */
class CarDrivingSignalSource(
    uxReader: CarUxRestrictionsReader,
    private val fallback: MockDrivingSignalSource,
    scope: CoroutineScope,
) : DrivingSignalSource {

    override val signals: StateFlow<DrivingSignals> =
        combine(fallback.signals, uxReader.restrictions) { mock, ux -> mock.copy(uxRestrictions = ux) }
            .stateIn(scope, SharingStarted.Eagerly, fallback.signals.value.copy(uxRestrictions = null))
}

package com.drivelingo.core.vehicle.car

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.util.Log
import com.drivelingo.core.vehicle.UxRestrictions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * AAOS 표준 CarUxRestrictionsManager 실연동.
 *
 * Pleos 주행 규제 개발 가이드: "앱은 CarUxRestrictionsManager 에 registerListener() 를 등록하고
 * onUxRestrictionsChanged() 로 수신한 CarUxRestrictions 를 기준으로 UX 제한을 적용한다."
 *
 * 차량 이미지가 아니거나 Car 서비스 연결에 실패하면 null(알 수 없음)을 보낸다.
 * null 은 SituationClassifier 에서 안전 제한 모드로 해석된다.
 */
class CarUxRestrictionsReader(private val connection: CarConnection) {

    @OptIn(ExperimentalCoroutinesApi::class)
    val restrictions: Flow<UxRestrictions?> = connection.car.flatMapLatest { car ->
        if (car == null) flowOf(null) else listen(car)
    }

    private fun listen(car: Car): Flow<UxRestrictions?> = callbackFlow {
        val manager = runCatching { car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager }
            .onFailure { Log.w(TAG, "CarUxRestrictionsManager unavailable", it) }
            .getOrNull()
        if (manager == null) {
            publish(null, "no manager")
            awaitClose { }
            return@callbackFlow
        }
        publish(manager.currentCarUxRestrictions?.toModel(), "initial")
        manager.registerListener { restrictions -> publish(restrictions?.toModel(), "changed") }
        awaitClose { runCatching { manager.unregisterListener() } }
    }

    /** 받은 값을 로그로 남긴다. scripts/check-car-ux.sh 가 이 로그로 실제 수신 여부를 확인한다. */
    private fun ProducerScope<UxRestrictions?>.publish(value: UxRestrictions?, reason: String) {
        Log.i(
            TAG,
            "$reason: " + (value?.let {
                "requiresDO=${it.requiresDistractionOptimization} active=${it.activeRestrictions} " +
                    UxRestrictions.describe(it.activeRestrictions)
            } ?: "unknown"),
        )
        trySend(value)
    }

    private fun CarUxRestrictions.toModel() = UxRestrictions(
        requiresDistractionOptimization = isRequiresDistractionOptimization,
        activeRestrictions = activeRestrictions,
    )

    private companion object {
        const val TAG = "CarUxRestrictions"
    }
}

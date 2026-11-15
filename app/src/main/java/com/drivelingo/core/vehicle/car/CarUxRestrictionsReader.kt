package com.drivelingo.core.vehicle.car

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.drivelingo.core.vehicle.UxRestrictions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * AAOS 표준 CarUxRestrictionsManager 실연동.
 *
 * Pleos 주행 규제 개발 가이드: "앱은 CarUxRestrictionsManager 에 registerListener() 를 등록하고
 * onUxRestrictionsChanged() 로 수신한 CarUxRestrictions 를 기준으로 UX 제한을 적용한다."
 *
 * 차량 이미지가 아니거나 Car 서비스 연결에 실패하면 null(알 수 없음)을 보낸다.
 * null 은 SituationClassifier 에서 안전 제한 모드로 해석된다.
 */
class CarUxRestrictionsReader(private val context: Context) {

    val restrictions: Flow<UxRestrictions?> = callbackFlow {
        if (!isCarApiAvailable(context)) {
            Log.w(TAG, "Car API unavailable on this device; UX restrictions unknown")
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        var manager: CarUxRestrictionsManager? = null
        val car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { car, ready ->
            if (!ready) {
                manager?.unregisterListener()
                manager = null
                trySend(null)
                return@createCar
            }
            val uxManager = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            manager = uxManager
            if (uxManager == null) {
                trySend(null)
                return@createCar
            }
            trySend(uxManager.currentCarUxRestrictions?.toModel())
            uxManager.registerListener { restrictions -> trySend(restrictions?.toModel()) }
        }

        awaitClose {
            manager?.unregisterListener()
            car.disconnect()
        }
    }

    private fun CarUxRestrictions.toModel() = UxRestrictions(
        requiresDistractionOptimization = isRequiresDistractionOptimization,
        activeRestrictions = activeRestrictions,
    )

    companion object {
        private const val TAG = "CarUxRestrictions"

        fun isCarApiAvailable(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return false
            return runCatching { Class.forName("android.car.Car") }.isSuccess
        }
    }
}

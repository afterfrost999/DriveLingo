package com.drivelingo.core.vehicle.car

import android.car.Car
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/**
 * AAOS Car 서비스 연결 하나를 여러 리더(CarUxRestrictions, CarPropertyManager)가 같이 쓴다.
 *
 * [car] 는 연결되면 Car, 끊기거나 차량 이미지가 아니면 null 이다. Car 서비스가 재시작되면
 * 다시 연결된 Car 를 내보내므로, 리더는 이 값이 바뀔 때마다 리스너를 새로 등록하면 된다.
 */
class CarConnection(context: Context, scope: CoroutineScope) {

    private val appContext = context.applicationContext

    val car: StateFlow<Car?> = callbackFlow {
        if (!isCarApiAvailable(appContext)) {
            Log.w(TAG, "Car API unavailable on this device")
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val car = Car.createCar(appContext, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { car, ready ->
            Log.i(TAG, "car service ready=$ready")
            trySend(if (ready) car else null)
        }
        awaitClose { car.disconnect() }
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    companion object {
        private const val TAG = "CarConnection"
        private const val STOP_TIMEOUT_MS = 5_000L

        fun isCarApiAvailable(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return false
            return runCatching { Class.forName("android.car.Car") }.isSuccess
        }
    }
}

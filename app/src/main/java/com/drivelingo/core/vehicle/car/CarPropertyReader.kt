package com.drivelingo.core.vehicle.car

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import com.drivelingo.core.vehicle.Gear
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlin.math.abs

/**
 * CarPropertyManager 에서 읽은 주행 값. null = 아직 못 읽었거나 권한·지원이 없음.
 *
 * @property speedAtMs 마지막 속도 이벤트를 받은 시각(elapsedRealtime). 급제동 판단에서 감속도를 계산할 때 쓴다.
 */
data class CarMotion(
    val speedKmh: Float? = null,
    val speedAtMs: Long? = null,
    val gear: Gear? = null,
)

/**
 * AAOS 표준 CarPropertyManager 실연동 (Pleos 정책: 표준 차량 데이터는 AAOS Car API 로 읽는다).
 *
 * | 신호 | 속성 | 권한 |
 * |---|---|---|
 * | 속도 | `VehiclePropertyIds.PERF_VEHICLE_SPEED` (m/s) | `android.car.permission.CAR_SPEED` (런타임 권한) |
 * | 기어 | `VehiclePropertyIds.GEAR_SELECTION` | `android.car.permission.CAR_POWERTRAIN` |
 *
 * 속도 권한은 런타임 권한이라 처음에는 없을 수 있다. 권한을 받은 뒤 [permissionChanges] 가 바뀌면 다시 구독한다.
 */
class CarPropertyReader(
    private val context: Context,
    private val connection: CarConnection,
    private val permissionChanges: StateFlow<Int>,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    val motion: Flow<CarMotion> = combine(connection.car, permissionChanges) { car, _ -> car }
        .flatMapLatest { car -> if (car == null) flowOf(CarMotion()) else listen(car) }

    private fun listen(car: Car): Flow<CarMotion> = callbackFlow {
        val manager = runCatching { car.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager }
            .onFailure { Log.w(TAG, "CarPropertyManager unavailable", it) }
            .getOrNull()
        if (manager == null) {
            trySend(CarMotion())
            awaitClose { }
            return@callbackFlow
        }

        val lock = Any()
        var current = CarMotion()
        fun update(transform: (CarMotion) -> CarMotion) {
            trySend(synchronized(lock) { transform(current).also { current = it } })
        }
        trySend(current)

        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                val available = value.status == CarPropertyValue.STATUS_AVAILABLE
                when (value.propertyId) {
                    VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                        val metersPerSecond = (value.value as? Float).takeIf { available }
                        // 후진 중에는 음수가 올 수 있다. 판단에는 크기만 쓴다.
                        update { it.copy(speedKmh = metersPerSecond?.let { mps -> abs(mps) * MPS_TO_KMH }, speedAtMs = SystemClock.elapsedRealtime()) }
                    }
                    VehiclePropertyIds.GEAR_SELECTION -> {
                        val gear = (value.value as? Int).takeIf { available }
                        update { it.copy(gear = gear?.let(::gearFromVehicleGear)) }
                    }
                }
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                Log.w(TAG, "property error ${VehiclePropertyIds.toString(propertyId)}")
                when (propertyId) {
                    VehiclePropertyIds.PERF_VEHICLE_SPEED -> update { it.copy(speedKmh = null, speedAtMs = null) }
                    VehiclePropertyIds.GEAR_SELECTION -> update { it.copy(gear = null) }
                }
            }
        }

        // registerCallback 은 API 35 Car 라이브러리에서 deprecated 지만, 대체 API(subscribePropertyEvents)는
        // Pleos Connect v2.1(Android 14) 에 없을 수 있어 둘 다 되는 쪽을 쓴다.
        @Suppress("DEPRECATION")
        fun subscribe(propertyId: Int, permission: String, rateHz: Float) {
            val name = VehiclePropertyIds.toString(propertyId)
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "$name: $permission not granted; treated as unknown")
                return
            }
            val ok = runCatching { manager.registerCallback(callback, propertyId, rateHz) }
                .onFailure { Log.w(TAG, "$name subscribe failed", it) }
                .getOrDefault(false)
            Log.i(TAG, "$name subscribed=$ok")
        }
        subscribe(VehiclePropertyIds.PERF_VEHICLE_SPEED, PERMISSION_CAR_SPEED, CarPropertyManager.SENSOR_RATE_UI)
        subscribe(VehiclePropertyIds.GEAR_SELECTION, PERMISSION_CAR_POWERTRAIN, CarPropertyManager.SENSOR_RATE_ONCHANGE)

        @Suppress("DEPRECATION")
        awaitClose { runCatching { manager.unregisterCallback(callback) } }
    }

    companion object {
        private const val TAG = "CarProperty"
        private const val MPS_TO_KMH = 3.6f

        /** 런타임 권한(dangerous). MainActivity 가 car 모드에서 요청한다. */
        const val PERMISSION_CAR_SPEED = "android.car.permission.CAR_SPEED"
        const val PERMISSION_CAR_POWERTRAIN = "android.car.permission.CAR_POWERTRAIN"

        /** android.car.VehicleGear → 앱 모델. 수동 1~9단은 전진 주행으로 본다. */
        fun gearFromVehicleGear(value: Int): Gear = when (value) {
            VehicleGear.GEAR_PARK -> Gear.PARK
            VehicleGear.GEAR_REVERSE -> Gear.REVERSE
            VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
            VehicleGear.GEAR_DRIVE -> Gear.DRIVE
            VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
            else -> if (value >= VehicleGear.GEAR_FIRST) Gear.DRIVE else Gear.UNKNOWN
        }
    }
}

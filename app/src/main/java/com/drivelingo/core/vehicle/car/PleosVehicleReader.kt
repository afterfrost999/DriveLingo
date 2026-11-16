package com.drivelingo.core.vehicle.car

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.util.Log
import com.drivelingo.core.vehicle.TurnSignal
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Pleos Vehicle SDK 에서 읽은 값. null = 아직 못 읽었거나 이 차량에서 지원하지 않음.
 *
 * @property warningTelltales 지금 켜져 있는 "수업을 멈출 만한" 경고등 이름. 계속 켜져 있는 경고가
 *   수업을 무기한 막지 않도록, 새로 켜진 것만 중단 사유로 보는 판단은 [com.drivelingo.core.driving.NewWarningGate] 가 한다.
 */
data class PleosVehicleState(
    val turnSignal: TurnSignal? = null,
    val brakePedalPressed: Boolean? = null,
    val warningTelltales: Set<String> = emptySet(),
    /** 앞 와이퍼가 움직이는 중(비 보정). */
    val wipersOn: Boolean? = null,
    /** 로우빔 전조등 켜짐(야간 보정). 주간주행등은 false. */
    val headlampsOn: Boolean? = null,
)

/**
 * Pleos Vehicle SDK 실연동 (`ai.pleos.playground:Vehicle`).
 *
 * | 신호 | 도메인 API | 퍼미션 |
 * |---|---|---|
 * | 방향지시등 | `TurnSignal.registerTurnSignalState` | `pleos.car.permission.CAR_LIGHTS` |
 * | 브레이크 페달 | `Brake.registerBrakePedalStatus` | `pleos.car.permission.CAR_DRIVE` |
 * | 경고등 | `Safety.registerTelltales` | `pleos.car.permission.CAR_SAFETY` + `PRIVATE_CAR_SAFETY` |
 * | 앞 와이퍼 | `Wiper.registerWiperState(FRONT_WINDSHIELD)` | `pleos.car.permission.CAR_WINDOWS` |
 * | 전조등(로우빔) | `Light.registerLowBeamLightState` | `pleos.car.permission.CAR_LIGHTS` |
 *
 * 차종마다 지원 범위가 다르므로 등록 전에 `checkXxxCapabilities` 로 읽기 가능 여부를 확인한다.
 * 지원하지 않거나 실패한 신호는 null 로 두고, 판단 로직이 안전한 쪽으로 해석한다.
 */
class PleosVehicleReader(private val context: Context) {

    val state: Flow<PleosVehicleState> = callbackFlow {
        val lock = Any()
        var current = PleosVehicleState()
        fun update(transform: (PleosVehicleState) -> PleosVehicleState) {
            val next = synchronized(lock) { transform(current).also { current = it } }
            trySend(next)
        }
        trySend(current)

        val vehicle = Vehicle(context.applicationContext)
        val initialized = runCatching { vehicle.initialize() }
            .onFailure { Log.w(TAG, "Vehicle SDK initialize failed; Pleos signals unknown", it) }
            .isSuccess
        if (!initialized) {
            awaitClose { }
            return@callbackFlow
        }

        // ---- 방향지시등 ----
        val turnSignal = vehicle.getTurnSignal()
        val turnListener = object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(value: TurnSignalValue?) {
                update { it.copy(turnSignal = value?.toModel()) }
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "turn signal failed", e)
                update { it.copy(turnSignal = null) }
            }
        }
        whenReadable("turn signal", turnSignal::checkTurnSignalStateCapabilities) {
            turnSignal.getCurrentTurnSignalState(
                { value -> update { it.copy(turnSignal = value?.toModel()) } },
                { e -> Log.w(TAG, "turn signal initial read failed", e) },
            )
            turnSignal.registerTurnSignalState(turnListener)
        }

        // ---- 브레이크 페달 ----
        val brake = vehicle.getBrake()
        val brakeListener = object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
                update { it.copy(brakePedalPressed = pressed) }
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "brake pedal failed", e)
                update { it.copy(brakePedalPressed = null) }
            }
        }
        whenReadable("brake pedal", brake::checkBrakePedalStatusCapabilities) {
            brake.getCurrentBrakePedalStatus(
                { pressed -> update { it.copy(brakePedalPressed = pressed) } },
                { e -> Log.w(TAG, "brake pedal initial read failed", e) },
            )
            brake.registerBrakePedalStatus(brakeListener)
        }

        // ---- 경고등 ----
        val safety = vehicle.getSafety()
        val telltaleListener = object : TelltaleInfoListener {
            override fun onTelltaleInfoUpdated(info: Telltales?) {
                update { it.copy(warningTelltales = interruptingTelltales(info)) }
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "telltales failed", e)
                update { it.copy(warningTelltales = emptySet()) }
            }
        }
        whenReadable("telltales", safety::checkTelltalesCapabilities) {
            safety.getTelltalesInfo(
                { info -> update { it.copy(warningTelltales = interruptingTelltales(info)) } },
                { e -> Log.w(TAG, "telltales initial read failed", e) },
            )
            safety.registerTelltales(telltaleListener)
        }

        // ---- 앞 와이퍼 (비 보정) ----
        val wiper = vehicle.getWiper()
        val front = WindowArea(WindowValue.FRONT_WINDSHIELD)
        val wiperListener = object : WiperStateListener {
            override fun onWiperStateUpdated(state: WiperState?) {
                update { it.copy(wipersOn = state?.toWipersOn()) }
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "wiper failed", e)
                update { it.copy(wipersOn = null) }
            }
        }
        whenReadable("front wiper", { ok, fail -> wiper.checkWiperStateCapabilities({ config, _ -> ok(config) }, fail) }) {
            wiper.getCurrentWiperState(
                front,
                { state -> update { it.copy(wipersOn = state?.toWipersOn()) } },
                { e -> Log.w(TAG, "wiper initial read failed", e) },
            )
            wiper.registerWiperState(front, wiperListener)
        }

        // ---- 전조등 로우빔 (야간 보정) ----
        val light = vehicle.getLight()
        val lowBeamListener = object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) {
                update { it.copy(headlampsOn = state?.let { s -> s == LightState.ON }) }
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "low beam failed", e)
                update { it.copy(headlampsOn = null) }
            }
        }
        whenReadable("low beam", { ok, fail -> light.checkLowBeamLightStateCapabilities({ config, _ -> ok(config) }, fail) }) {
            light.getCurrentLowBeamLightState(
                { state -> update { it.copy(headlampsOn = state?.let { s -> s == LightState.ON }) } },
                { e -> Log.w(TAG, "low beam initial read failed", e) },
            )
            light.registerLowBeamLightState(lowBeamListener)
        }

        awaitClose {
            runCatching { wiper.unregisterWiperState(front, wiperListener) }
            runCatching { light.unregisterLowBeamLightState(lowBeamListener) }
            runCatching { turnSignal.unregisterTurnSignalState(turnListener) }
            runCatching { brake.unregisterBrakePedalStatus(brakeListener) }
            runCatching { safety.unregisterTelltales(telltaleListener) }
            runCatching { vehicle.release() }
        }
    }

    /** checkXxxCapabilities 결과가 읽기 가능일 때만 [register] 를 실행한다. */
    private inline fun whenReadable(
        name: String,
        check: ((CapabilityConfig) -> Unit, (Exception) -> Unit) -> Unit,
        crossinline register: () -> Unit,
    ) {
        check(
            { config ->
                if (config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE) {
                    Log.i(TAG, "$name supported (${config.accessType})")
                    runCatching { register() }.onFailure { Log.w(TAG, "$name register failed", it) }
                } else {
                    Log.w(TAG, "$name not readable on this vehicle (${config.accessType}); treated as unknown")
                }
            },
            { e -> Log.w(TAG, "$name capability check failed; treated as unknown", e) },
        )
    }

    /** ON = 움직이는 중. OFF·정비 위치는 비가 아니다. 그 밖(OTHER)은 모름. */
    private fun WiperState.toWipersOn(): Boolean? = when (this) {
        WiperState.ON -> true
        WiperState.OFF, WiperState.SERVICE -> false
        else -> null
    }

    private fun TurnSignalValue.toModel(): TurnSignal = when (this) {
        TurnSignalValue.LEFT -> TurnSignal.LEFT
        TurnSignalValue.RIGHT -> TurnSignal.RIGHT
        else -> TurnSignal.OFF
    }

    companion object {
        private const val TAG = "PleosVehicle"

        /**
         * 운전자 주의가 필요한 경고등. 켜지는 순간 수업을 잠깐 멈춘다.
         * 차체 상태 표시(전조등, 주행 모드 등)와 운전 보조 상태 표시는 제외한다.
         * 어떤 경고를 중단 사유로 볼지는 Pleos 와 협의해 조정해야 한다(docs/pleos-sdk-status.md).
         */
        val INTERRUPTING_TELLTALES: Set<TelltaleType> = setOf(
            TelltaleType.MASTER_WARNING,
            TelltaleType.FCA,
            TelltaleType.DRIVER_ATTENTION,
            TelltaleType.AIRBAG,
            TelltaleType.ABS,
            TelltaleType.ENGINE_OIL_PRESSURE,
            TelltaleType.ENGINE_TEMPERATURE,
            TelltaleType.EV_SYSTEM,
            TelltaleType.HEV_SYSTEM,
            TelltaleType.FCEV_SYSTEM,
            TelltaleType.EREV_SYSTEM,
            TelltaleType.POWER_DOWN,
            TelltaleType.H2_LEAK,
        )

        /** ESC 는 켜져 있는 것만으로는 경고가 아니고, 깜빡일 때(자세 제어 개입 중)만 중단 사유로 본다. */
        fun interruptingTelltales(info: Telltales?): Set<String> = info?.telltales.orEmpty().filter { data ->
            when {
                data.type == TelltaleType.ESC -> data.state == TelltaleState.BLINK
                data.type in INTERRUPTING_TELLTALES -> data.state != TelltaleState.OFF
                else -> false
            }
        }.map { it.type.name }.toSet()
    }
}

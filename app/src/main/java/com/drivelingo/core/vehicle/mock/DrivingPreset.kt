package com.drivelingo.core.vehicle.mock

import com.drivelingo.core.vehicle.DrivingSignals
import com.drivelingo.core.vehicle.Gear
import com.drivelingo.core.vehicle.UxRestrictions

/**
 * 테스트 콘솔과 adb 명령에서 쓰는 주행 상황 프리셋.
 * 프리셋은 "판단 결과"가 아니라 "원시 신호"를 설정한다. 판단은 SituationClassifier 가 한다.
 */
enum class DrivingPreset(val label: String) {
    PARKED("주차"),
    STOPPED_AT_LIGHT("정차 · 신호 대기"),
    CRUISING("안정 주행"),
    STOP_AND_GO("가다 서다"),
    COMPLEX("복잡 주행"),
    DEPARTING("출발 직후"),
    ARRIVING("도착 직전"),
    UNKNOWN("상태 불명"),
    IGNITION_OFF("시동 끔");

    /** 현재 신호에서 주행 관련 필드만 바꾼다. 네트워크·전화 같은 값은 유지한다. */
    fun applyTo(current: DrivingSignals): DrivingSignals {
        val base = current.copy(
            ignitionOn = true,
            upcomingManeuverMeters = null,
            minutesSinceDeparture = 12,
            minutesToArrival = 18,
        )
        return when (this) {
            PARKED -> base.copy(
                uxRestrictions = UxRestrictions.UNRESTRICTED, gear = Gear.PARK, speedKmh = 0f,
                minutesSinceDeparture = null, minutesToArrival = 25,
            )
            STOPPED_AT_LIGHT -> base.copy(uxRestrictions = UxRestrictions.DRIVING, gear = Gear.DRIVE, speedKmh = 0f)
            CRUISING -> base.copy(uxRestrictions = UxRestrictions.DRIVING, gear = Gear.DRIVE, speedKmh = 82f)
            STOP_AND_GO -> base.copy(uxRestrictions = UxRestrictions.DRIVING, gear = Gear.DRIVE, speedKmh = 18f)
            COMPLEX -> base.copy(
                uxRestrictions = UxRestrictions.DRIVING, gear = Gear.DRIVE, speedKmh = 46f,
                upcomingManeuverMeters = 180,
            )
            DEPARTING -> base.copy(
                uxRestrictions = UxRestrictions.DRIVING, gear = Gear.DRIVE, speedKmh = 15f,
                minutesSinceDeparture = 0, minutesToArrival = 25,
            )
            ARRIVING -> base.copy(
                uxRestrictions = UxRestrictions.DRIVING, gear = Gear.DRIVE, speedKmh = 30f,
                minutesToArrival = 1,
            )
            UNKNOWN -> base.copy(uxRestrictions = null, gear = Gear.UNKNOWN, speedKmh = null)
            IGNITION_OFF -> base.copy(
                ignitionOn = false, uxRestrictions = UxRestrictions.UNRESTRICTED, gear = Gear.PARK,
                speedKmh = 0f, minutesSinceDeparture = null, minutesToArrival = null,
            )
        }
    }

    companion object {
        fun fromName(name: String): DrivingPreset? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

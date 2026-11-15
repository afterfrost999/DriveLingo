package com.drivelingo.core.driving

import com.drivelingo.core.vehicle.DrivingSignals
import com.drivelingo.core.vehicle.Gear

/**
 * 차량 신호 → 운전 상황 판단. 순수 함수.
 *
 * 원칙
 * 1. CarUxRestrictions 를 기본 제한으로 쓴다. 플랫폼이 "제한 없음"이라고 해도 추가 신호가
 *    주행 중을 가리키면(속도 > 5km/h) 주행으로 본다. 추가 신호는 더 보수적인 쪽으로만 작용한다.
 * 2. CarUxRestrictions 를 모르거나 주행 중 속도를 모르면 UNKNOWN(안전 제한 모드).
 */
object SituationClassifier {

    private const val PLATFORM_STATIONARY_KMH = 5f
    private const val STOPPED_KMH = 1f
    private const val COMPLEX_MANEUVER_METERS = 300
    private const val ARRIVING_MINUTES = 2

    fun classify(signals: DrivingSignals, history: SpeedHistory): DrivingSituation {
        val ux = signals.uxRestrictions ?: return DrivingSituation.UNKNOWN
        if (!signals.ignitionOn) return DrivingSituation.PARKED

        val speed = signals.speedKmh
        val platformUnrestricted = !ux.requiresDistractionOptimization
        val inconsistentWithPlatform = speed != null && speed > PLATFORM_STATIONARY_KMH

        if (platformUnrestricted && !inconsistentWithPlatform) {
            return if (signals.gear == Gear.PARK) DrivingSituation.PARKED else DrivingSituation.STOPPED
        }

        // 여기부터는 주행 규제 상태(또는 플랫폼과 신호가 어긋나 보수적으로 주행으로 본 상태)
        if (signals.gear == Gear.REVERSE) return DrivingSituation.DEPARTING
        if (speed == null) return DrivingSituation.UNKNOWN

        val sinceDeparture = signals.minutesSinceDeparture
        if (sinceDeparture != null && sinceDeparture < 1) return DrivingSituation.DEPARTING

        val toArrival = signals.minutesToArrival
        if (toArrival != null && toArrival <= ARRIVING_MINUTES) return DrivingSituation.ARRIVING

        val maneuver = signals.upcomingManeuverMeters
        if (maneuver != null && maneuver < COMPLEX_MANEUVER_METERS) return DrivingSituation.COMPLEX

        if (history.isStopAndGo()) return DrivingSituation.STOP_AND_GO
        if (speed < STOPPED_KMH) return DrivingSituation.STOPPED
        return DrivingSituation.CRUISING
    }
}

/**
 * 상황이 잠깐씩 흔들릴 때 모드가 왔다 갔다 하지 않도록 한다.
 * 더 보수적인 모드로는 즉시 바꾸고, 덜 보수적인 모드로는 [minStableMs] 동안 유지될 때만 바꾼다.
 */
class SituationStabilizer(private val minStableMs: Long = 3_000) {

    private var current: DrivingSituation? = null
    private var candidate: DrivingSituation? = null
    private var candidateSinceMs = 0L

    fun update(raw: DrivingSituation, nowMs: Long): DrivingSituation {
        val cur = current
        if (cur == null || raw == cur) {
            current = raw
            candidate = null
            return raw
        }
        if (!cur.mode.isMoreRestrictiveThan(raw.mode)) {
            current = raw
            candidate = null
            return raw
        }
        if (candidate != raw) {
            candidate = raw
            candidateSinceMs = nowMs
        }
        if (nowMs - candidateSinceMs >= minStableMs) {
            current = raw
            candidate = null
        }
        return current!!
    }
}

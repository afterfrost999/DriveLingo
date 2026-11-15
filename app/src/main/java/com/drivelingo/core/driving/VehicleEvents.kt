package com.drivelingo.core.driving

import com.drivelingo.core.vehicle.Gear

/**
 * 실제 차량 신호에서 "순간 이벤트"를 만드는 판단기들. 순수 Kotlin 이라 단위 테스트한다.
 * Mock 에서는 콘솔이 이벤트를 펄스로 직접 넣지만, 실제 차량은 원시 상태만 주므로 여기서 이벤트로 바꾼다.
 */

/**
 * 새로 켜진 경고등만 잠깐 중단 사유로 본다.
 *
 * 경고등은 고장이 고쳐질 때까지 계속 켜져 있을 수 있다(TPMS, 엔진 경고 등). 켜져 있는 동안 내내 멈추면
 * 수업을 다시 할 수 없으므로, 경고가 새로 켜진 순간부터 [holdMs] 동안만 중단 사유로 낸다.
 */
class NewWarningGate(private val holdMs: Long = 5_000) {
    private var previous: Set<String> = emptySet()
    private var activeUntilMs = Long.MIN_VALUE

    fun update(active: Set<String>, nowMs: Long) {
        if ((active - previous).isNotEmpty()) activeUntilMs = nowMs + holdMs
        previous = active
    }

    fun isActive(nowMs: Long): Boolean = nowMs < activeUntilMs
}

/**
 * 급제동 판단. Pleos 문서에 급제동 전용 API 가 없어서 속도 변화율(감속도)로 추정한다.
 *
 * 두 속도 표본 사이 감속도가 [thresholdKmhPerSec] 이상이고 브레이크 페달이 "안 밟힘"이 아니면(밟힘 또는 모름)
 * 급제동으로 보고 [holdMs] 동안 중단 사유로 낸다. 표본 간격이 [maxGapMs] 보다 길면 오래된 값이라 계산하지 않는다.
 *
 * 기본 임계값 13km/h/s ≈ 3.6m/s² (≈0.37g). 일반적인 "급감속" 기준(0.3~0.4g) 안의 초깃값이고 실제 주행 데이터로 조정해야 한다.
 */
class SuddenBrakingDetector(
    private val thresholdKmhPerSec: Float = 13f,
    private val holdMs: Long = 1_000,
    private val maxGapMs: Long = 1_500,
) {
    private var lastAtMs: Long? = null
    private var lastKmh = 0f
    private var activeUntilMs = Long.MIN_VALUE

    fun onSpeed(atMs: Long, speedKmh: Float, brakePedalPressed: Boolean?) {
        val prevAt = lastAtMs
        val prevKmh = lastKmh
        lastAtMs = atMs
        lastKmh = speedKmh
        if (prevAt == null) return
        val dtMs = atMs - prevAt
        if (dtMs <= 0 || dtMs > maxGapMs) return
        val decel = (prevKmh - speedKmh) / (dtMs / 1_000f)
        if (decel >= thresholdKmhPerSec && brakePedalPressed != false) activeUntilMs = atMs + holdMs
    }

    fun isActive(nowMs: Long): Boolean = nowMs < activeUntilMs
}

/**
 * 출발(P 해제) 후 경과 시간. "출발 직후" 상황 판단에 쓴다.
 * P 에서 다른 기어로 바뀐 순간을 출발로 본다. 앱을 주행 중에 켜서 P 를 본 적이 없으면 모른다(null).
 * 기어를 잠깐 못 읽는 경우(null·UNKNOWN)는 바뀐 것으로 보지 않는다.
 */
class DepartureTracker {
    private var previous: Gear? = null
    private var departedAtMs: Long? = null

    fun onGear(gear: Gear?, nowMs: Long) {
        if (gear == null || gear == Gear.UNKNOWN || gear == previous) return
        when {
            gear == Gear.PARK -> departedAtMs = null
            previous == Gear.PARK -> departedAtMs = nowMs
        }
        previous = gear
    }

    fun minutesSinceDeparture(nowMs: Long): Int? = departedAtMs?.let { ((nowMs - it) / 60_000).toInt() }
}

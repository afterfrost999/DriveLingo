package com.drivelingo.core.driving

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

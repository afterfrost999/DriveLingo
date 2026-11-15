package com.drivelingo.core.driving

import com.drivelingo.core.vehicle.AudioFocus
import com.drivelingo.core.vehicle.DrivingSignals
import com.drivelingo.core.vehicle.TurnSignal

/**
 * 수업을 즉시 멈추는 이벤트. [priority] 가 작을수록 우선순위가 높다(화면에 표시할 사유 선택에 사용).
 *
 * @param resumeDelayMs 이벤트가 끝난 뒤 재개까지 기다리는 시간. 운전자가 상황을 정리할 여유를 준다.
 * @param needsUserAction true 면 자동 재개하지 않는다(오디오 포커스 영구 상실, 사용자 일시정지).
 *   Pleos Music/Audio 가이드: 자동 재생 금지, 사용자의 명시적 동작 또는 시스템 재개 요청으로만 재생.
 */
enum class Interruption(
    val label: String,
    val priority: Int,
    val resumeDelayMs: Long,
    val needsUserAction: Boolean,
) {
    VEHICLE_WARNING("차량 경고", 1, 3_000, false),
    SUDDEN_BRAKING("급제동", 2, 5_000, false),
    PHONE_CALL("전화", 3, 2_000, false),
    AUDIO_FOCUS_LOST("다른 앱 소리", 4, 0, true),
    AUDIO_FOCUS_TRANSIENT("다른 앱 소리", 5, 1_000, false),
    NAV_GUIDANCE("내비 안내", 6, 1_500, false),
    TURN_SIGNAL("방향지시등", 7, 2_000, false),
    USER_PAUSE("일시정지", 8, 0, true);

    companion object {
        fun primary(active: Set<Interruption>): Interruption? = active.minByOrNull { it.priority }
    }
}

/** 차량 신호 → 현재 활성화된 중단 사유. 순수 함수라 단위 테스트한다. */
object InterruptionDetector {
    fun detect(signals: DrivingSignals): Set<Interruption> = buildSet {
        if (signals.vehicleWarningActive) add(Interruption.VEHICLE_WARNING)
        if (signals.suddenBraking) add(Interruption.SUDDEN_BRAKING)
        if (signals.phoneCallActive) add(Interruption.PHONE_CALL)
        when (signals.audioFocus) {
            AudioFocus.LOST -> add(Interruption.AUDIO_FOCUS_LOST)
            // 말하기 수업은 덕킹(볼륨 감쇄)된 상태로 겹쳐 들리면 오히려 혼란스럽다 → CAN_DUCK 도 일시정지.
            AudioFocus.LOST_TRANSIENT, AudioFocus.LOST_TRANSIENT_CAN_DUCK -> add(Interruption.AUDIO_FOCUS_TRANSIENT)
            AudioFocus.GAINED -> Unit
        }
        if (signals.navVoiceGuidanceActive) add(Interruption.NAV_GUIDANCE)
        if (signals.turnSignal != TurnSignal.OFF) add(Interruption.TURN_SIGNAL)
    }
}

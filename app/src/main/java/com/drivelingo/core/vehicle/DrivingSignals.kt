package com.drivelingo.core.vehicle

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

enum class TurnSignal { OFF, LEFT, RIGHT, HAZARD }

/** Android AudioManager.AUDIOFOCUS_* 를 단순화한 값. */
enum class AudioFocus { GAINED, LOST_TRANSIENT, LOST_TRANSIENT_CAN_DUCK, LOST }

/**
 * 학습 모드 판단과 즉시 중단에 쓰는 차량·환경 신호 한 묶음.
 *
 * `null` 은 "알 수 없음"이다. 판단 로직은 알 수 없는 값을 안전한 쪽으로 해석한다.
 * 각 필드의 실제 출처는 docs/pleos-sdk-status.md 의 표를 참고한다.
 */
data class DrivingSignals(
    /** 플랫폼 주행 규제. null = CarUxRestrictions 를 읽지 못함 → 안전 제한 모드. */
    val uxRestrictions: UxRestrictions? = null,
    val ignitionOn: Boolean = true,
    val gear: Gear = Gear.UNKNOWN,
    val speedKmh: Float? = null,
    val turnSignal: TurnSignal = TurnSignal.OFF,
    val vehicleWarningActive: Boolean = false,
    val suddenBraking: Boolean = false,
    val navVoiceGuidanceActive: Boolean = false,
    val phoneCallActive: Boolean = false,
    val audioFocus: AudioFocus = AudioFocus.GAINED,
    val networkAvailable: Boolean = true,
    /** 출발(P 해제) 후 경과 분. null = 운행 중 아님 또는 알 수 없음. */
    val minutesSinceDeparture: Int? = null,
    /** 내비 기준 도착까지 남은 분. null = 경로 안내 없음. */
    val minutesToArrival: Int? = null,
    /** 다음 회전·합류 지점까지 남은 거리(m). null = 정보 없음. */
    val upcomingManeuverMeters: Int? = null,
    /** 앞 와이퍼가 움직이는 중(비). null = 모름 → 보정하지 않음. */
    val wipersOn: Boolean? = null,
    /** 전조등(로우빔)이 켜짐(야간·터널). 주간주행등은 false. null = 모름. */
    val headlampsOn: Boolean? = null,
    /** 처음 가는 길. 내비 연동 전이라 Mock 으로만 받는다. */
    val unfamiliarRoute: Boolean = false,
)

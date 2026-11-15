package com.drivelingo.core.vehicle

import kotlinx.coroutines.flow.StateFlow

/**
 * 차량·환경 신호의 입력 경계. 실제 차량 연동과 Mock 은 이 인터페이스만 구현한다.
 *
 * - [com.drivelingo.core.vehicle.mock.MockDrivingSignalSource]: 테스트 콘솔/adb 로 조작
 * - [com.drivelingo.core.vehicle.car.CarDrivingSignalSource]: CarUxRestrictions 실연동 + 나머지 Mock
 */
interface DrivingSignalSource {
    val signals: StateFlow<DrivingSignals>
}

/**
 * 오디오 포커스 요청. Pleos 오디오 포커스 정책상 재생 전에 반드시 요청해야 하고,
 * AUDIOFOCUS_LOSS 이후에는 사용자의 명시적 재개 동작이 있을 때만 다시 요청한다.
 */
interface AudioFocusRequester {
    fun requestAudioFocus(): Boolean
    fun abandonAudioFocus()
}

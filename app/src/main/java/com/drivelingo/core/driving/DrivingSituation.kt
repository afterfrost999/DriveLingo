package com.drivelingo.core.driving

/**
 * 기획안의 6가지 운전 상황 + 판단 불가(UNKNOWN).
 * 출발 직후/도착 직전은 같은 학습 모드(SILENT)를 쓰지만 안내 문구가 달라 따로 둔다.
 */
enum class DrivingSituation(val label: String, val mode: LearningMode) {
    PARKED("주차", LearningMode.DETAILED_REVIEW),
    STOPPED("정차", LearningMode.LONG_ANSWER),
    CRUISING("안정 주행", LearningMode.SHORT_ANSWER),
    STOP_AND_GO("가다 서다", LearningMode.REPEAT_AFTER),
    COMPLEX("복잡 주행", LearningMode.LISTEN_ONLY),
    DEPARTING("출발 직후", LearningMode.SILENT),
    ARRIVING("도착 직전", LearningMode.SILENT),

    /** 신호를 신뢰할 수 없음 → 안전 제한 모드(듣기만). */
    UNKNOWN("상태 확인 중", LearningMode.LISTEN_ONLY),
}

/**
 * 상황별 학습 활동. [restrictiveness] 가 클수록 운전자에게 요구하는 것이 적다(더 보수적).
 */
enum class LearningMode(val label: String, val restrictiveness: Int, val usesMicrophone: Boolean) {
    DETAILED_REVIEW("복습 · 상세 결과", 0, true),
    LONG_ANSWER("긴 대답", 1, true),
    SHORT_ANSWER("짧은 대답", 2, true),
    REPEAT_AFTER("따라 말하기", 3, true),
    LISTEN_ONLY("듣기", 4, false),
    SILENT("쉬어가기", 5, false);

    fun isMoreRestrictiveThan(other: LearningMode) = restrictiveness > other.restrictiveness
}

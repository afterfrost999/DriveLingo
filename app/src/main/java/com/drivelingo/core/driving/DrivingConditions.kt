package com.drivelingo.core.driving

import com.drivelingo.core.vehicle.DrivingSignals

/** 활동 상한을 한 단계 낮추는 도로·날씨 조건(기획서 "비, 야간, 처음 가는 길에서는 상한을 한 단계 낮춥니다"). */
enum class DrivingCondition(val label: String) {
    RAIN("비"),
    NIGHT("야간"),
    UNFAMILIAR_ROUTE("초행길"),
}

/**
 * 조건 판단과 상한 보정. 순수 함수(DrivingConditionsTest).
 *
 * - 비: 앞 와이퍼가 움직이는 중. 야간: 전조등(로우빔)이 켜짐. 주간주행등은 야간이 아니다.
 *   모르는 값(null)은 조건 없음으로 본다. 지원하지 않는 차량에서는 보정 없이 동작한다.
 * - 보정: 조건이 하나라도 있으면 한 단계만 낮춘다(여러 개가 겹쳐도 한 단계).
 *   주차(화면 복습)는 운전 중이 아니라서 바꾸지 않는다. 침묵은 더 낮출 곳이 없다.
 */
object DrivingConditions {

    fun detect(signals: DrivingSignals): Set<DrivingCondition> = buildSet {
        if (signals.wipersOn == true) add(DrivingCondition.RAIN)
        if (signals.headlampsOn == true) add(DrivingCondition.NIGHT)
        if (signals.unfamiliarRoute) add(DrivingCondition.UNFAMILIAR_ROUTE)
    }

    fun adjust(situation: DrivingSituation, conditions: Set<DrivingCondition>): LearningMode {
        if (conditions.isEmpty() || situation == DrivingSituation.PARKED) return situation.mode
        return situation.mode.oneStepSafer()
    }

    fun LearningMode.oneStepSafer(): LearningMode = when (this) {
        LearningMode.DETAILED_REVIEW -> LearningMode.LONG_ANSWER
        LearningMode.LONG_ANSWER -> LearningMode.SHORT_ANSWER
        LearningMode.SHORT_ANSWER -> LearningMode.REPEAT_AFTER
        LearningMode.REPEAT_AFTER -> LearningMode.LISTEN_ONLY
        LearningMode.LISTEN_ONLY -> LearningMode.SILENT
        LearningMode.SILENT -> LearningMode.SILENT
    }
}

/**
 * 조건이 잠깐 꺼졌다 켜질 때(간헐 와이퍼, 터널을 지나며 자동 전조등) 모드가 왔다 갔다 하지 않도록,
 * 마지막으로 본 뒤 [holdMs] 동안은 계속 있는 것으로 본다. 켜지는 쪽은 바로 반영한다(보수적인 방향).
 */
class ConditionLatch(private val holdMs: Map<DrivingCondition, Long> = DEFAULT_HOLD_MS) {

    private val lastSeen = mutableMapOf<DrivingCondition, Long>()

    fun update(current: Set<DrivingCondition>, nowMs: Long): Set<DrivingCondition> {
        current.forEach { lastSeen[it] = nowMs }
        return lastSeen.filter { (condition, seen) -> nowMs - seen <= (holdMs[condition] ?: 0L) }.keys
    }

    companion object {
        val DEFAULT_HOLD_MS = mapOf(
            DrivingCondition.RAIN to 120_000L,
            DrivingCondition.NIGHT to 60_000L,
            DrivingCondition.UNFAMILIAR_ROUTE to 0L,
        )
    }
}

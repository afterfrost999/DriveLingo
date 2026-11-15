package com.drivelingo.core.driving

import com.drivelingo.core.vehicle.DrivingSignalSource
import com.drivelingo.core.vehicle.DrivingSignals
import com.drivelingo.core.vehicle.UxRestrictions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 화면과 수업이 함께 보는 "지금 운전 상황".
 *
 * @property uiRestricted 화면 조작 제한 여부. CarUxRestrictions 를 그대로 따르고, 모르면 제한으로 본다.
 */
data class DrivingStatus(
    val situation: DrivingSituation,
    val rawSituation: DrivingSituation,
    val ux: UxRestrictions?,
    val signals: DrivingSignals,
) {
    val mode: LearningMode get() = situation.mode
    val uiRestricted: Boolean get() = ux?.requiresDistractionOptimization ?: true
    val isParked: Boolean get() = !uiRestricted && situation == DrivingSituation.PARKED
    fun restricts(flag: Int): Boolean = ux?.has(flag) ?: true
}

/** 신호 소스를 구독해 상황을 판단하고 앱 전체에 [status] 로 공유한다. */
class DrivingMonitor(
    private val source: DrivingSignalSource,
    scope: CoroutineScope,
    private val clock: () -> Long,
) {
    private val history = SpeedHistory()
    private val stabilizer = SituationStabilizer()

    private val _status = MutableStateFlow(evaluate(source.signals.value))
    val status: StateFlow<DrivingStatus> = _status.asStateFlow()

    init {
        scope.launch { source.signals.collect { _status.value = evaluate(it) } }
        // 신호 변화가 없어도 안정화 시간이 지나면 모드를 갱신해야 하므로 주기적으로 다시 평가한다.
        scope.launch {
            while (isActive) {
                delay(1_000)
                _status.value = evaluate(source.signals.value)
            }
        }
    }

    @Synchronized
    private fun evaluate(signals: DrivingSignals): DrivingStatus {
        val now = clock()
        history.add(now, signals.speedKmh)
        val raw = SituationClassifier.classify(signals, history)
        val stable = stabilizer.update(raw, now)
        return DrivingStatus(stable, raw, signals.uxRestrictions, signals)
    }
}

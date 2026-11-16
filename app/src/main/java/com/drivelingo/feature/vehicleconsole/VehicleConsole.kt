package com.drivelingo.feature.vehicleconsole

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivelingo.app.LocalAppContainer
import com.drivelingo.core.designsystem.MalchaColors
import com.drivelingo.core.driving.InterruptionDetector
import com.drivelingo.core.speech.NextAnswer
import com.drivelingo.core.vehicle.AudioFocus
import com.drivelingo.core.vehicle.TurnSignal
import com.drivelingo.core.vehicle.UxRestrictions
import com.drivelingo.core.vehicle.car.CarSignalMerger
import com.drivelingo.core.vehicle.mock.DrivingPreset

/**
 * 디버그 빌드 전용 "차량 시뮬레이터". 에뮬레이터에서 주행 상태·중단 이벤트·오류를 손으로 재현한다.
 * release 빌드에는 표시되지 않는다(BuildConfig.SHOW_VEHICLE_CONSOLE).
 */
@Composable
fun VehicleConsoleOverlay() {
    var open by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        if (open) {
            VehicleConsolePanel(onClose = { open = false }, modifier = Modifier.align(Alignment.CenterEnd))
        } else {
            SmallFloatingActionButton(
                onClick = { open = true },
                modifier = Modifier.align(Alignment.CenterEnd).padding(4.dp),
                containerColor = MalchaColors.SurfaceRaised,
                contentColor = MalchaColors.Amber,
            ) { Icon(Icons.Outlined.DirectionsCar, contentDescription = "차량 시뮬레이터 열기") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VehicleConsolePanel(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val mock = container.mockVehicle
    val status by container.drivingMonitor.status.collectAsStateWithLifecycle()
    val preset by mock.preset.collectAsStateWithLifecycle()
    val nextAnswer by container.voiceSimulation.nextAnswer.collectAsStateWithLifecycle()
    val slowCoach by container.voiceSimulation.slowCoach.collectAsStateWithLifecycle()
    val speaking by container.voiceSimulation.nowSpeaking.collectAsStateWithLifecycle()
    val heard by container.voiceSimulation.lastHeard.collectAsStateWithLifecycle()
    val earcon by container.voiceSimulation.lastEarcon.collectAsStateWithLifecycle()
    val s = status.signals

    BoxWithConstraints(modifier.fillMaxHeight()) {
        val panelWidth = if (maxWidth < 420.dp) maxWidth else 380.dp
        Column(
            Modifier
                .width(panelWidth)
                .fillMaxHeight()
                .padding(8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MalchaColors.Surface.copy(alpha = 0.97f))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (container.usesMockVehicle) "차량 시뮬레이터 (Mock)" else "차량 시뮬레이터 (car)", style = MaterialTheme.typography.titleMedium, color = MalchaColors.Amber, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "닫기") }
            }

            // 현재 판단 결과
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MalchaColors.Background).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Info("상황", "${status.situation.label} (원시: ${status.rawSituation.label})")
                Info("학습 모드", status.mode.label)
                Info(
                    "CarUx",
                    s.uxRestrictions?.let { ux ->
                        if (!ux.requiresDistractionOptimization) "제한 없음"
                        else "DO 필요 · " + UxRestrictions.describe(ux.activeRestrictions).size + "개 플래그"
                    } ?: "알 수 없음 → 안전 제한",
                )
                Info("속도·기어", "${s.speedKmh?.let { "%.0f".format(it) } ?: "?"} km/h · ${s.gear}")
                Info("중단 사유", InterruptionDetector.detect(s).joinToString { it.label }.ifEmpty { "없음" })
                Info("음성", if (container.usesPleosVoice) "Pleos STT/TTS (실제 음성)" else "시뮬레이션 (소리 없음)")
                Info("TTS", speaking ?: "-")
                Info("STT 마지막", heard ?: "-")
                Info("효과음 마지막", earcon ?: "-")
                if (!container.usesMockVehicle) Info("car 모드", "실제: " + CarSignalMerger.REAL_SIGNALS.joinToString() + " · 나머지 Mock")
            }

            Section("주행 상황 프리셋") {
                DrivingPreset.entries.forEach { p ->
                    Chip(p.label, selected = p == preset) { mock.applyPreset(p) }
                }
            }

            Section("즉시 중단 이벤트") {
                Chip("방향지시등 ←", selected = s.turnSignal == TurnSignal.LEFT) {
                    mock.update { it.copy(turnSignal = if (it.turnSignal == TurnSignal.LEFT) TurnSignal.OFF else TurnSignal.LEFT) }
                }
                Chip("내비 안내 (4초)", selected = s.navVoiceGuidanceActive) {
                    mock.pulse(4_000, { it.copy(navVoiceGuidanceActive = true) }, { it.copy(navVoiceGuidanceActive = false) })
                }
                Chip("차량 경고", selected = s.vehicleWarningActive) {
                    mock.update { it.copy(vehicleWarningActive = !it.vehicleWarningActive) }
                }
                Chip("급제동 (1초)", selected = s.suddenBraking) {
                    mock.pulse(1_000, { it.copy(suddenBraking = true) }, { it.copy(suddenBraking = false) })
                }
                Chip("전화", selected = s.phoneCallActive) {
                    mock.update { it.copy(phoneCallActive = !it.phoneCallActive) }
                }
                Chip("포커스 일시 상실", selected = s.audioFocus == AudioFocus.LOST_TRANSIENT) {
                    mock.update {
                        it.copy(audioFocus = if (it.audioFocus == AudioFocus.LOST_TRANSIENT) AudioFocus.GAINED else AudioFocus.LOST_TRANSIENT)
                    }
                }
                Chip("포커스 영구 상실", selected = s.audioFocus == AudioFocus.LOST) {
                    mock.update { it.copy(audioFocus = AudioFocus.LOST) }
                }
            }

            Section("네트워크 · AI") {
                Chip("네트워크 끊김", selected = !s.networkAvailable) {
                    mock.update { it.copy(networkAvailable = !it.networkAvailable) }
                }
                Chip("AI 응답 지연(타임아웃)", selected = slowCoach) {
                    container.voiceSimulation.slowCoach.value = !slowCoach
                }
            }

            // 실제 음성(pleos) 모드에서는 인식 결과를 강제로 정할 수 없다.
            if (!container.usesPleosVoice) {
                Section("다음 음성 인식 (1회)") {
                    NextAnswer.entries.forEach { a ->
                        Chip(a.label, selected = a == nextAnswer) { container.voiceSimulation.nextAnswer.value = a }
                    }
                }
            }

            Text(
                "adb 로도 조작할 수 있어요: docs/testing.md",
                style = MaterialTheme.typography.labelSmall, color = MalchaColors.TextTertiary,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MalchaColors.TextTertiary, modifier = Modifier.width(76.dp))
        Text(value, style = MaterialTheme.typography.labelSmall, color = MalchaColors.TextPrimary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MalchaColors.TextSecondary)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MalchaColors.MintContainer,
            selectedLabelColor = MalchaColors.MintStrong,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected,
            borderColor = MalchaColors.Outline, selectedBorderColor = MalchaColors.Mint,
        ),
    )
}

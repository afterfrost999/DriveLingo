package com.drivelingo.feature.lesson

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivelingo.app.LocalAppContainer
import com.drivelingo.core.designsystem.MalchaColors
import com.drivelingo.core.designsystem.components.ControlButton
import com.drivelingo.core.designsystem.components.MalchaCard
import com.drivelingo.core.designsystem.components.Pill
import com.drivelingo.core.designsystem.components.VoiceRing
import com.drivelingo.core.designsystem.components.VoiceTone

@Composable
fun LessonScreen(onFinished: () -> Unit, onExit: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: LessonViewModel = viewModel { LessonViewModel(container) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    LaunchedEffect(state.stopped) { if (state.stopped) onExit() }
    BackHandler {
        viewModel.leave()
        onExit()
    }

    LessonContent(
        state = state,
        onTogglePause = viewModel::togglePause,
        onRepeat = viewModel::repeat,
        onBack = {
            viewModel.leave()
            onExit()
        },
    )
}

@Composable
private fun LessonContent(state: LessonUiState, onTogglePause: () -> Unit, onRepeat: () -> Unit, onBack: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight && maxWidth >= 720.dp
        val ringSize = (if (wide) maxHeight * 0.55f else minOf(maxWidth * 0.62f, maxHeight * 0.38f))
            .coerceIn(160.dp, 340.dp)

        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "수업 나가기", tint = MalchaColors.TextSecondary)
                }
                Text(
                    state.topic, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge, color = MalchaColors.TextSecondary,
                )
                Spacer(Modifier.widthIn(min = 48.dp))
            }

            if (wide) {
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
                ) {
                    Ring(state, ringSize)
                    Column(Modifier.widthIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                        StatusText(state, Alignment.Start)
                        Spacer(Modifier.height(28.dp))
                        Controls(state, onTogglePause, onRepeat)
                        state.detail?.let {
                            Spacer(Modifier.height(20.dp))
                            DetailCard(it)
                        }
                    }
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Spacer(Modifier.height(12.dp))
                    Ring(state, ringSize)
                    Spacer(Modifier.height(28.dp))
                    StatusText(state, Alignment.CenterHorizontally)
                    state.detail?.let {
                        Spacer(Modifier.height(20.dp))
                        DetailCard(it, Modifier.widthIn(max = 560.dp))
                    }
                }
                Controls(state, onTogglePause, onRepeat, Modifier.widthIn(max = 640.dp).align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun Ring(state: LessonUiState, size: Dp) {
    val (icon, tone) = when (state.visual) {
        VoiceVisual.SPEAK_TURN -> Icons.Outlined.Mic to VoiceTone.MINT
        VoiceVisual.LONG_SPEAK_TURN -> Icons.Outlined.Mic to VoiceTone.AMBER
        VoiceVisual.LISTENING, VoiceVisual.THINKING -> Icons.Outlined.Headphones to VoiceTone.BLUE
        VoiceVisual.PAUSED -> Icons.Filled.Pause to VoiceTone.NEUTRAL
        VoiceVisual.QUIET -> Icons.Outlined.Spa to VoiceTone.NEUTRAL
        VoiceVisual.FINISHED -> Icons.Filled.Check to VoiceTone.MINT
    }
    val timer = state.listenTimer
    if (timer?.limitMs != null) {
        // 말할 차례: 마이크가 열린 순간부터 대답 시간 동안 링을 일정한 속도로 한 번 채운다(반복하지 않음).
        val fill = remember(timer.token) { Animatable(0f) }
        LaunchedEffect(timer.token) {
            fill.animateTo(1f, tween(durationMillis = timer.limitMs.toInt(), easing = LinearEasing))
        }
        VoiceRing(icon = icon, tone = tone, progress = fill.value, size = size, smooth = false)
    } else {
        VoiceRing(icon = icon, tone = tone, progress = state.progress, size = size, showProgress = state.visual != VoiceVisual.PAUSED)
    }
}

@Composable
private fun StatusText(state: LessonUiState, alignment: Alignment.Horizontal) {
    val textAlign = if (alignment == Alignment.Start) TextAlign.Start else TextAlign.Center
    Column(horizontalAlignment = alignment) {
        if (state.offline) {
            Pill("오프라인 · 저장된 복습 카드로 이어가요", icon = Icons.Outlined.CloudOff, color = MalchaColors.Blue, container = MalchaColors.BlueContainer)
            Spacer(Modifier.height(14.dp))
        }
        state.badge?.let {
            Pill(it, icon = Icons.Outlined.ChatBubbleOutline)
            Spacer(Modifier.height(14.dp))
        }
        Text(state.headline, style = MaterialTheme.typography.displaySmall, textAlign = textAlign)
        state.caption?.let {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.isPaused) {
                    Icon(Icons.Outlined.AcUnit, null, tint = MalchaColors.TextSecondary, modifier = Modifier.padding(end = 6.dp).heightIn(max = 16.dp))
                }
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MalchaColors.TextSecondary, textAlign = textAlign)
            }
        }
    }
}

@Composable
private fun Controls(state: LessonUiState, onTogglePause: () -> Unit, onRepeat: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ControlButton(
            icon = if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
            contentDescription = if (state.isPaused) "이어서 하기" else "일시정지",
            onClick = onTogglePause,
            enabled = state.canTogglePause,
            modifier = Modifier.weight(1f),
        )
        ControlButton(
            icon = Icons.Filled.Repeat,
            contentDescription = "다시 듣기",
            onClick = onRepeat,
            enabled = state.canRepeat,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 주차 중에만 보이는 표현 카드. */
@Composable
private fun DetailCard(detail: LessonDetail, modifier: Modifier = Modifier) {
    MalchaCard(modifier.fillMaxWidth()) {
        DetailLine("오늘의 표현", detail.expression)
        Text(detail.meaning, style = MaterialTheme.typography.bodyMedium, color = MalchaColors.TextSecondary)
        Spacer(Modifier.height(12.dp))
        DetailLine("코치", detail.prompt)
        detail.lastAnswer?.let { Spacer(Modifier.height(10.dp)); DetailLine("내 말", it) }
        detail.feedback?.let { Spacer(Modifier.height(10.dp)); DetailLine("피드백", it, MalchaColors.Mint) }
    }
}

@Composable
private fun ColumnScope.DetailLine(label: String, text: String, color: androidx.compose.ui.graphics.Color = MalchaColors.TextPrimary) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MalchaColors.TextTertiary)
    Spacer(Modifier.height(2.dp))
    Text(text, style = MaterialTheme.typography.titleMedium, color = color)
}

package com.drivelingo.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drivelingo.core.designsystem.MalchaColors

enum class VoiceTone(val accent: Color, val container: Color) {
    MINT(MalchaColors.Mint, MalchaColors.MintContainer),
    BLUE(MalchaColors.Blue, MalchaColors.BlueContainer),
    AMBER(MalchaColors.Amber, MalchaColors.AmberContainer),
    NEUTRAL(MalchaColors.TextSecondary, Color.Transparent),
}

/**
 * 중앙 원형 음성 상태 표시. 바깥 링은 진행률(수업 진행률 또는 대답 시간), 안쪽 원은 현재 상태(색·아이콘).
 * 주행 중 시선을 끌지 않도록 반복 애니메이션은 쓰지 않고, 진행률 변화만 부드럽게 보간한다.
 * [smooth] 가 false 면 호출한 쪽이 이미 매 프레임 값을 주는 것이라(대답 시간 링) 다시 보간하지 않는다.
 */
@Composable
fun VoiceRing(
    icon: ImageVector,
    tone: VoiceTone,
    progress: Float,
    size: Dp,
    modifier: Modifier = Modifier,
    showProgress: Boolean = true,
    smooth: Boolean = true,
) {
    val eased by animateFloatAsState(progress, animationSpec = tween(600), label = "ring")
    val animated = if (smooth) eased else progress
    val stroke = (size.value * 0.04f).dp
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (showProgress) {
            Canvas(Modifier.fillMaxSize()) {
                val width = stroke.toPx()
                val inset = width / 2
                val arcSize = androidx.compose.ui.geometry.Size(this.size.width - width, this.size.height - width)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(MalchaColors.RingTrack, 0f, 360f, false, topLeft, arcSize, style = Stroke(width))
                drawArc(
                    MalchaColors.Mint, -90f, 360f * animated.coerceIn(0.02f, 1f), false, topLeft, arcSize,
                    style = Stroke(width, cap = StrokeCap.Round),
                )
            }
        }
        val inner = size * 0.74f
        val circle = Modifier
            .size(inner)
            .clip(CircleShape)
            .background(tone.container)
        Box(
            if (tone == VoiceTone.NEUTRAL) circle.border(3.dp, MalchaColors.TextTertiary, CircleShape) else circle,
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tone.accent, modifier = Modifier.padding(inner * 0.3f).fillMaxSize())
        }
    }
}

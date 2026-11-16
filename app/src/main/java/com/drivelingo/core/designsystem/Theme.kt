package com.drivelingo.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 시안에서 뽑은 색. 차량 야간 시인성을 위해 고대비 다크 테마 하나만 둔다. */
object MalchaColors {
    val Background = Color(0xFF0B1220)
    val Surface = Color(0xFF151F2E)
    val SurfaceRaised = Color(0xFF1C2738)
    val Outline = Color(0xFF263347)

    val Mint = Color(0xFF5FD3A1)
    val MintStrong = Color(0xFF6EE0AE)
    val MintContainer = Color(0xFF1E3D35)
    val OnMint = Color(0xFF0B1A16)

    val Amber = Color(0xFFF2B544)
    val AmberContainer = Color(0xFF3A2F17)
    val Blue = Color(0xFF7FA9FF)
    val BlueContainer = Color(0xFF1E2C47)

    val TextPrimary = Color(0xFFF1F4F8)
    val TextSecondary = Color(0xFF9AA6B8)
    val TextTertiary = Color(0xFF66738A)
    val RingTrack = Color(0xFF1F2B3D)
}

private val colorScheme = darkColorScheme(
    primary = MalchaColors.Mint,
    onPrimary = MalchaColors.OnMint,
    primaryContainer = MalchaColors.MintContainer,
    onPrimaryContainer = MalchaColors.MintStrong,
    secondary = MalchaColors.Blue,
    tertiary = MalchaColors.Amber,
    background = MalchaColors.Background,
    onBackground = MalchaColors.TextPrimary,
    surface = MalchaColors.Surface,
    onSurface = MalchaColors.TextPrimary,
    surfaceVariant = MalchaColors.SurfaceRaised,
    onSurfaceVariant = MalchaColors.TextSecondary,
    outline = MalchaColors.Outline,
)

/** 주행 중 한눈에 읽히도록 큰 글자·굵은 제목 위주. */
private val typography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun MalchaTheme(content: @Composable () -> Unit) {
    // 큰 차량 디스플레이에서는 dp·sp 를 함께 키운다(DisplayScale). 사용자 글자 크기 설정(fontScale)은 그대로 둔다.
    val base = LocalDensity.current
    val scale = DisplayScale.forSmallestWidth(LocalConfiguration.current.smallestScreenWidthDp)
    val density = remember(base, scale) { Density(base.density * scale, base.fontScale) }
    CompositionLocalProvider(LocalDensity provides density) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
    }
}

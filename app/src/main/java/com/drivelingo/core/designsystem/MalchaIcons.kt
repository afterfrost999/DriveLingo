package com.drivelingo.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 머티리얼 아이콘에 없는 앱 전용 아이콘. */
object MalchaIcons {

    /** 말차 로고: 나뭇잎 안에 음성 파형 막대. */
    val Logo: ImageVector by lazy {
        ImageVector.Builder("MalchaLogo", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(MalchaColors.Mint)) {
                moveTo(3f, 21f)
                curveTo(3f, 10f, 9f, 3f, 21f, 3f)
                curveTo(21f, 14f, 15f, 21f, 3f, 21f)
                close()
            }
            path(
                stroke = SolidColor(MalchaColors.Background), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(8.5f, 11.5f); lineTo(8.5f, 16.5f)
                moveTo(11.5f, 8.5f); lineTo(11.5f, 17f)
                moveTo(14.5f, 7.5f); lineTo(14.5f, 14.5f)
                moveTo(17.2f, 8f); lineTo(17.2f, 11f)
            }
        }.build()
    }

    /** 구간 마스터 왕관. */
    val Crown: ImageVector by lazy {
        ImageVector.Builder("Crown", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.White), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 17f)
                lineTo(3f, 7.5f)
                lineTo(8f, 11f)
                lineTo(12f, 5f)
                lineTo(16f, 11f)
                lineTo(21f, 7.5f)
                lineTo(20f, 17f)
                close()
                moveTo(5f, 20f)
                lineTo(19f, 20f)
            }
        }.build()
    }
}

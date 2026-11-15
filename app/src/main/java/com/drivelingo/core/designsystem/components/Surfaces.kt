package com.drivelingo.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drivelingo.core.designsystem.MalchaColors
import com.drivelingo.core.designsystem.MalchaIcons

val CardShape = RoundedCornerShape(20.dp)

@Composable
fun MalchaCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MalchaColors.Surface,
    border: BorderStroke? = null,
    contentPadding: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var m = modifier.clip(CardShape).background(containerColor)
    if (border != null) m = m.border(border, CardShape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(contentPadding), content = content)
}

@Composable
fun AppTopBar(
    streakDays: Int,
    modifier: Modifier = Modifier,
    onSettings: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(MalchaIcons.Logo, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(10.dp))
        Text("말차", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (onSettings != null) {
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "설정", tint = MalchaColors.TextSecondary)
            }
            Spacer(Modifier.width(4.dp))
        }
        StreakChip(streakDays)
    }
}

@Composable
fun StreakChip(days: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MalchaColors.SurfaceRaised)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Eco, contentDescription = null, tint = MalchaColors.Mint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("${days}일 연속", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = MalchaColors.Amber,
    container: Color = MalchaColors.AmberContainer,
) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MalchaColors.TextPrimary) {
    MalchaCard(modifier = modifier, contentPadding = 18.dp) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MalchaColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = valueColor)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.labelLarge, color = MalchaColors.TextSecondary)
}

/**
 * 주행 규제 중 막는 화면. CarUxRestrictions(NO_SETUP / LIMIT_CONTENT)에 해당하는 기능에 쓴다.
 */
@Composable
fun RestrictedWhileDriving(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.LocalParking, contentDescription = null, tint = MalchaColors.Mint,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(50))
                .background(MalchaColors.MintContainer)
                .padding(16.dp),
        )
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MalchaColors.TextSecondary, textAlign = TextAlign.Center)
    }
}

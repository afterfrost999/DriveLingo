package com.drivelingo.feature.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivelingo.app.LocalAppContainer
import com.drivelingo.core.designsystem.MalchaColors
import com.drivelingo.core.designsystem.MalchaIcons
import com.drivelingo.core.designsystem.components.MalchaCard
import com.drivelingo.core.designsystem.components.PrimaryButton
import com.drivelingo.core.designsystem.components.SectionLabel
import com.drivelingo.core.designsystem.components.StatTile
import com.drivelingo.core.progress.Correction
import com.drivelingo.core.progress.DayMark

@Composable
fun ReportScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: ReportViewModel = viewModel { ReportViewModel(container) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight && maxWidth >= 840.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (maxWidth >= 600.dp) 32.dp else 20.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "홈으로", tint = MalchaColors.TextSecondary)
            }
            when {
                !state.hasResult -> Text("아직 완료한 수업이 없어요", style = MaterialTheme.typography.headlineSmall)
                state.uiRestricted -> RestrictedReport(state, onBack)
                wide -> Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1.4f)) { MainColumn(state, viewModel::play) }
                    Column(Modifier.weight(1f)) { SideColumn(state) }
                }
                else -> {
                    MainColumn(state, viewModel::play)
                    Spacer(Modifier.height(20.dp))
                    SideColumn(state)
                }
            }
        }
    }
}

/** 주행 중: 큰 숫자 두 개와 안내만. */
@Composable
private fun RestrictedReport(state: ReportUiState, onBack: () -> Unit) {
    Header(state)
    Spacer(Modifier.height(24.dp))
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatTile("말한 단어", state.words.toString(), Modifier.weight(1f).fillMaxHeight(), MalchaColors.Mint)
        StatTile("최고 콤보", "${state.maxCombo}연속", Modifier.weight(1f).fillMaxHeight())
    }
    Spacer(Modifier.height(24.dp))
    Text(
        "주차하면 자세한 리포트를 보여드릴게요", modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyLarge, color = MalchaColors.TextSecondary, textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    PrimaryButton("홈으로", onClick = onBack, icon = null)
}

@Composable
private fun Header(state: ReportUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(MalchaColors.Mint), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.SentimentSatisfied, null, tint = MalchaColors.OnMint, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(state.title, style = MaterialTheme.typography.headlineSmall)
            Text(state.subtitle, style = MaterialTheme.typography.bodyMedium, color = MalchaColors.TextSecondary)
        }
    }
}

@Composable
private fun MainColumn(state: ReportUiState, onPlay: (String) -> Unit) {
    Header(state)
    Spacer(Modifier.height(20.dp))
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile("말한 단어", state.words.toString(), Modifier.weight(1f).fillMaxHeight(), MalchaColors.Mint)
        StatTile("최고 콤보", "${state.maxCombo}연속", Modifier.weight(1f).fillMaxHeight())
        StatTile("정차 중 말한 단어", state.wordsWhileStopped.toString(), Modifier.weight(1f).fillMaxHeight(), MalchaColors.Amber)
    }
    Spacer(Modifier.height(14.dp))
    MalchaCard(
        Modifier.fillMaxWidth(), containerColor = MalchaColors.Background,
        border = BorderStroke(1.dp, MalchaColors.Outline), contentPadding = 16.dp,
    ) {
        NarrativeLine(Icons.AutoMirrored.Outlined.ShowChart, state.drivingLine)
        state.pauseLine?.let {
            Spacer(Modifier.height(8.dp))
            NarrativeLine(Icons.Outlined.AcUnit, it)
        }
    }
    if (state.corrections.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        SectionLabel("오늘 다듬은 표현")
        Spacer(Modifier.height(10.dp))
        state.corrections.forEach {
            CorrectionCard(it, playing = state.playingText == it.corrected, onPlay = onPlay)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun NarrativeLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MalchaColors.TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CorrectionCard(correction: Correction, playing: Boolean, onPlay: (String) -> Unit) {
    MalchaCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("내 말 · ${correction.original}", style = MaterialTheme.typography.labelMedium, color = MalchaColors.TextSecondary)
                Spacer(Modifier.height(4.dp))
                Text(correction.corrected, style = MaterialTheme.typography.titleLarge)
            }
            IconButton(
                onClick = { onPlay(correction.corrected) },
                modifier = Modifier.size(52.dp).clip(CircleShape).background(MalchaColors.MintContainer),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = "다시 듣기",
                    tint = if (playing) MalchaColors.MintStrong else MalchaColors.Mint,
                )
            }
        }
    }
}

@Composable
private fun SideColumn(state: ReportUiState) {
    MalchaCard(Modifier.fillMaxWidth(), containerColor = MalchaColors.MintContainer, contentPadding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.nextLabel, style = MaterialTheme.typography.labelMedium, color = MalchaColors.TextSecondary)
                Spacer(Modifier.height(4.dp))
                Text(state.nextText, style = MaterialTheme.typography.titleMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MalchaColors.TextSecondary)
        }
    }
    Spacer(Modifier.height(14.dp))
    MalchaCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Eco, null, tint = MalchaColors.Mint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("통근 스트릭 ${state.streakDays}일", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(14.dp))
        WeekRow(state.weekMarks)
        Spacer(Modifier.height(10.dp))
        Text("재택이나 휴가로 차를 안 탄 날은 자동으로 유지돼요", style = MaterialTheme.typography.labelSmall, color = MalchaColors.TextTertiary)
    }
    Spacer(Modifier.height(14.dp))
    RankingCard(state)
    Spacer(Modifier.height(20.dp))
    Text(
        "말한 양과 꾸준함만 기록해요. 거리·속도·주행 시간은 기록하지 않아요.",
        style = MaterialTheme.typography.labelSmall, color = MalchaColors.TextTertiary,
    )
}

@Composable
private fun WeekRow(marks: List<DayMark>) {
    val labels = listOf("월", "화", "수", "목", "금", "토", "일")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        marks.forEachIndexed { i, mark ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val base = Modifier.size(34.dp).clip(CircleShape)
                val circle = when (mark) {
                    DayMark.DONE -> base.background(MalchaColors.Mint)
                    DayMark.TODAY_DONE -> base.background(MalchaColors.Mint).border(3.dp, MalchaColors.MintStrong, CircleShape)
                    DayMark.TODAY -> base.border(2.dp, MalchaColors.Mint, CircleShape)
                    DayMark.SKIPPED, DayMark.UPCOMING -> base.border(1.dp, MalchaColors.Outline, CircleShape)
                }
                Box(circle)
                Spacer(Modifier.height(6.dp))
                Text(
                    labels[i], style = MaterialTheme.typography.labelSmall,
                    color = if (mark == DayMark.TODAY || mark == DayMark.TODAY_DONE) MalchaColors.TextPrimary else MalchaColors.TextTertiary,
                    fontWeight = if (mark == DayMark.TODAY || mark == DayMark.TODAY_DONE) FontWeight.Bold else null,
                )
            }
        }
    }
}

@Composable
private fun RankingCard(state: ReportUiState) {
    MalchaCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MalchaIcons.Crown, null, tint = MalchaColors.Mint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(state.ranking?.segment ?: "구간 랭킹", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("이번 주 구간 순위", style = MaterialTheme.typography.labelSmall, color = MalchaColors.TextTertiary)
        }
        Spacer(Modifier.height(12.dp))
        val board = state.ranking
        if (board == null) {
            Text("설정에서 랭킹에 참여하면 같은 구간 학습자와 비교할 수 있어요", style = MaterialTheme.typography.bodyMedium, color = MalchaColors.TextSecondary)
        } else {
            board.entries.forEach { entry ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    val color = if (entry.isMe) MalchaColors.Mint else MalchaColors.TextPrimary
                    Text("${entry.rank}. ${entry.name}", color = color, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text("${entry.words}단어", color = color, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (board.wordsToMaster > 0) "마스터까지 ${board.wordsToMaster}단어 · 내일 출근길이면 충분해요" else "이번 주 구간 마스터예요!",
                style = MaterialTheme.typography.labelMedium, color = MalchaColors.TextSecondary,
            )
        }
    }
}

package com.drivelingo.feature.course

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivelingo.app.LocalAppContainer
import com.drivelingo.core.curriculum.Level
import com.drivelingo.core.designsystem.MalchaColors
import com.drivelingo.core.designsystem.components.MalchaCard
import com.drivelingo.core.designsystem.components.RestrictedWhileDriving
import com.drivelingo.core.designsystem.components.SectionLabel

@Composable
fun CourseScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: CourseViewModel = viewModel { CourseViewModel(container) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingLevel by remember { mutableStateOf<Level?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = MalchaColors.TextPrimary)
            }
            Text("내 코스", style = MaterialTheme.typography.headlineSmall)
        }
        if (state.uiRestricted) {
            // 코스 탐색·레벨 변경은 설정 성격(NO_SETUP)이라 주행 중 막는다.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                RestrictedWhileDriving("주차 후 볼 수 있어요", "운전 중에는 코스 지도를 열지 않아요.\n수업은 홈에서 바로 시작할 수 있어요.")
            }
            return@Column
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight && maxWidth >= 840.dp
            if (wide) {
                Row(Modifier.fillMaxSize().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(0.8f).verticalScroll(rememberScrollState())) { LevelColumn(state) { pendingLevel = it } }
                    Column(Modifier.weight(1.2f).verticalScroll(rememberScrollState())) { WeeksColumn(state) }
                    Column(Modifier.weight(1.3f).verticalScroll(rememberScrollState())) { WeekPlanColumn(state) }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
                    LevelColumn(state) { pendingLevel = it }
                    Spacer(Modifier.height(24.dp))
                    WeeksColumn(state)
                    Spacer(Modifier.height(24.dp))
                    WeekPlanColumn(state)
                }
            }
        }
    }

    pendingLevel?.let { level ->
        AlertDialog(
            onDismissRequest = { pendingLevel = null },
            title = { Text("${level.code} ${level.title} 코스로 바꿀까요?") },
            text = { Text("${level.cefr} · ${level.theme} 코스 1주차부터 시작해요. 스트릭과 기록은 그대로예요.") },
            confirmButton = {
                TextButton(onClick = { viewModel.changeLevel(level); pendingLevel = null }) { Text("바꾸기") }
            },
            dismissButton = { TextButton(onClick = { pendingLevel = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun LevelColumn(state: CourseUiState, onPick: (Level) -> Unit) {
    SectionLabel("직장 영어 트랙 · 레벨")
    Spacer(Modifier.height(12.dp))
    Level.entries.forEach { level ->
        val current = level == state.currentLevel
        MalchaCard(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            containerColor = if (current) MalchaColors.MintContainer else MalchaColors.Surface,
            border = if (current) BorderStroke(1.5.dp, MalchaColors.Mint) else null,
            contentPadding = 16.dp,
            onClick = if (current) null else ({ onPick(level) }),
        ) {
            Text(
                "${level.code} ${level.title}" + if (current) " · 지금" else "",
                style = MaterialTheme.typography.titleMedium,
            )
            Text("${level.cefr} · ${level.theme}", style = MaterialTheme.typography.labelMedium, color = MalchaColors.TextSecondary)
        }
    }
    Spacer(Modifier.height(8.dp))
    MalchaCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Text("다른 트랙", style = MaterialTheme.typography.labelMedium, color = MalchaColors.TextSecondary)
        Text("여행 · 면접 · 말하기 시험 유형 (준비 중)", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WeeksColumn(state: CourseUiState) {
    Text(
        "${state.currentLevel.code} ${state.currentLevel.title} · 4주 코스",
        style = MaterialTheme.typography.labelMedium, color = MalchaColors.Mint,
    )
    Text(state.course.title, style = MaterialTheme.typography.headlineMedium)
    Text(
        "주마다 주제 하나, 수업 10개",
        style = MaterialTheme.typography.bodyMedium, color = MalchaColors.TextSecondary,
    )
    Spacer(Modifier.height(16.dp))
    state.course.weeks.forEach { week ->
        val current = week.number == state.currentWeek
        MalchaCard(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            border = if (current) BorderStroke(1.5.dp, MalchaColors.Mint) else null,
            contentPadding = 16.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${week.number}주 · ${week.title}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (current) {
                    Text("수업 ${state.completedInWeek + 1}/${CourseViewModel.LESSONS_PER_WEEK}", style = MaterialTheme.typography.labelMedium, color = MalchaColors.Mint)
                } else if (week.number < state.currentWeek) {
                    Icon(Icons.Filled.Check, null, tint = MalchaColors.Mint, modifier = Modifier.size(18.dp))
                }
            }
            if (current) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { state.completedInWeek / CourseViewModel.LESSONS_PER_WEEK.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = MalchaColors.Mint, trackColor = MalchaColors.RingTrack,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(week.keyPhrase, style = MaterialTheme.typography.bodyMedium, color = MalchaColors.TextSecondary)
        }
    }
    MalchaCard(Modifier.fillMaxWidth(), containerColor = MalchaColors.Background, border = BorderStroke(1.dp, MalchaColors.Outline), contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Flag, null, tint = MalchaColors.TextSecondary)
            Spacer(Modifier.width(10.dp))
            Text("체크포인트 · 4주차 금요일 퇴근 수업", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("통과하면 다음 레벨", style = MaterialTheme.typography.labelSmall, color = MalchaColors.TextTertiary)
        }
    }
}

@Composable
private fun WeekPlanColumn(state: CourseUiState) {
    val week = state.course.weeks.getOrNull(state.currentWeek - 1)
    Text("이번 주 · ${week?.title ?: ""}", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Row {
        Spacer(Modifier.width(32.dp))
        Text("출근 · 새 표현 2개", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MalchaColors.TextSecondary)
        Text("퇴근 · 꺼내 쓰기", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MalchaColors.TextSecondary)
    }
    Spacer(Modifier.height(6.dp))
    state.days.forEach { slots ->
        Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(slots.day.dayLabel, Modifier.width(32.dp), style = MaterialTheme.typography.labelLarge, color = MalchaColors.TextSecondary)
            Slot(slots.day.morningTopic, slots.morning, highlight = false, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Slot(slots.day.eveningTopic, slots.evening, highlight = slots.day.isUnitRoleplay, modifier = Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(16.dp))
    MalchaCard(Modifier.fillMaxWidth(), containerColor = MalchaColors.Background, border = BorderStroke(1.dp, MalchaColors.Outline), contentPadding = 16.dp) {
        Text("복습은 알아서 들어가요", style = MaterialTheme.typography.labelMedium, color = MalchaColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(
            "배운 날 저녁 → 다음 날 아침 → 금요일 → 다음 주 → 체크포인트.\n틀린 표현은 바로 다음 수업에 한 번 더 나와요.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Slot(text: String, state: SlotState, highlight: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val (bg, fg) = when {
        state == SlotState.DONE -> MalchaColors.MintContainer to MalchaColors.MintStrong
        highlight -> MalchaColors.AmberContainer to MalchaColors.Amber
        else -> MalchaColors.Surface to MalchaColors.TextPrimary
    }
    var m = modifier.heightIn(min = 52.dp).clip(shape).background(bg)
    if (state == SlotState.CURRENT) m = m.border(1.5.dp, MalchaColors.Mint, shape)
    Row(m.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (state == SlotState.DONE) {
            Icon(Icons.Filled.Check, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = fg)
    }
}

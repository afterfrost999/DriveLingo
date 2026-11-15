package com.drivelingo.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivelingo.app.LocalAppContainer
import com.drivelingo.core.designsystem.MalchaColors
import com.drivelingo.core.designsystem.MalchaIcons
import com.drivelingo.core.designsystem.components.AppTopBar
import com.drivelingo.core.designsystem.components.MalchaCard
import com.drivelingo.core.designsystem.components.PrimaryButton

@Composable
fun HomeScreen(
    onStartLesson: () -> Unit,
    onOpenCourse: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel { HomeViewModel(container) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) { viewModel.autoStartEvents.collect { onStartLesson() } }

    HomeContent(state, onStartLesson, onOpenCourse, onOpenReport, onOpenSettings)
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onStartLesson: () -> Unit,
    onOpenCourse: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight && maxWidth >= 720.dp
        val padding = if (maxWidth >= 600.dp) 32.dp else 20.dp
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
        ) {
            AppTopBar(
                streakDays = state.streakDays,
                onSettings = if (state.uiRestricted) null else onOpenSettings,
            )
            Spacer(Modifier.height(24.dp))
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1.3f)) {
                        Greeting(state)
                        Spacer(Modifier.height(20.dp))
                        LessonCard(state, onStartLesson, onOpenCourse)
                    }
                    if (!state.uiRestricted) {
                        Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            RankCard(state, onOpenSettings)
                            if (state.hasReport) LastReportLink(onOpenReport)
                        }
                    }
                }
            } else {
                Column(Modifier.widthIn(max = 720.dp).align(Alignment.CenterHorizontally)) {
                    Greeting(state)
                    Spacer(Modifier.height(20.dp))
                    LessonCard(state, onStartLesson, onOpenCourse)
                    if (!state.uiRestricted) {
                        Spacer(Modifier.height(16.dp))
                        RankCard(state, onOpenSettings)
                        if (state.hasReport) {
                            Spacer(Modifier.height(12.dp))
                            LastReportLink(onOpenReport)
                        }
                    }
                }
            }
            Text(
                state.footnote,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MalchaColors.TextTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Greeting(state: HomeUiState) {
    if (!state.uiRestricted) {
        Text(state.subheader, style = MaterialTheme.typography.bodyLarge, color = MalchaColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
    }
    Text(state.headline, style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun LessonCard(state: HomeUiState, onStart: () -> Unit, onOpenCourse: () -> Unit) {
    MalchaCard(Modifier.fillMaxWidth(), contentPadding = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.courseLabel, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium, color = MalchaColors.Mint,
            )
            // 코스 탐색은 주행 중 숨긴다(CarUxRestrictions LIMIT_CONTENT / NO_SETUP 대상).
            if (!state.uiRestricted) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenCourse).padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("코스 보기", style = MaterialTheme.typography.labelMedium, color = MalchaColors.TextSecondary)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MalchaColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(state.lessonTitle, style = MaterialTheme.typography.titleLarge)
        if (state.reviewExpressions.isNotEmpty() && !state.uiRestricted) {
            Spacer(Modifier.height(14.dp))
            state.reviewExpressions.forEach { expression ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MalchaColors.Background)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) { Text(expression, style = MaterialTheme.typography.bodyLarge) }
            }
        }
        if (!state.uiRestricted) {
            Spacer(Modifier.height(6.dp))
            Text(state.lessonSummary, style = MaterialTheme.typography.bodyMedium, color = MalchaColors.TextSecondary)
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = if (state.canResume) "이어서 하기" else "시작", onClick = onStart)
    }
}

@Composable
private fun RankCard(state: HomeUiState, onOpenSettings: () -> Unit) {
    MalchaCard(
        Modifier.fillMaxWidth(),
        containerColor = MalchaColors.Background,
        border = BorderStroke(1.dp, MalchaColors.Outline),
        contentPadding = 18.dp,
        onClick = if (state.rankingOptIn) null else onOpenSettings,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MalchaColors.MintContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(MalchaIcons.Crown, null, tint = MalchaColors.Mint, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(state.segment, style = MaterialTheme.typography.labelMedium, color = MalchaColors.TextSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (state.rankingOptIn) state.rankLine else "구간 랭킹에 참여하면 같은 길 학습자와 비교해요",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MalchaColors.TextSecondary)
        }
    }
}

@Composable
private fun LastReportLink(onOpenReport: () -> Unit) {
    MalchaCard(Modifier.fillMaxWidth(), contentPadding = 18.dp, onClick = onOpenReport) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("지난 수업 리포트 보기", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MalchaColors.TextSecondary)
        }
    }
}

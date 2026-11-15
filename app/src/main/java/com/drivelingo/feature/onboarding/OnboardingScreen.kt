package com.drivelingo.feature.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivelingo.app.LocalAppContainer
import com.drivelingo.core.curriculum.Level
import com.drivelingo.core.designsystem.MalchaColors
import com.drivelingo.core.designsystem.MalchaIcons
import com.drivelingo.core.designsystem.components.MalchaCard
import com.drivelingo.core.designsystem.components.PrimaryButton
import com.drivelingo.core.designsystem.components.RestrictedWhileDriving
import com.drivelingo.core.designsystem.components.SecondaryButton
import com.drivelingo.core.designsystem.components.VoiceRing
import com.drivelingo.core.designsystem.components.VoiceTone

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: OnboardingViewModel = viewModel { OnboardingViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onMicPermissionResult(it)
    }

    if (state.restricted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RestrictedWhileDriving("주차 후 이어서 할게요", "레벨 테스트와 처음 설정은\n주차 중에만 할 수 있어요.")
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            Text("나에게 맞춘 코스", style = MaterialTheme.typography.headlineSmall, color = MalchaColors.Mint)
            Spacer(Modifier.height(20.dp))
            when (state.step) {
                OnboardingStep.MIC -> MicStep(
                    onAllow = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) viewModel.onMicPermissionResult(true) else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onSkip = { viewModel.onMicPermissionResult(false) },
                )
                OnboardingStep.VOICE_TEST -> VoiceTestStep(state, viewModel::startVoiceTest, viewModel::toSelfAssessment)
                OnboardingStep.SELF_ASSESS, OnboardingStep.REPEAT, OnboardingStep.INTERVIEW ->
                    LevelTestStep(state, viewModel::chooseSelfLevel, viewModel::playCurrent, viewModel::pickLevelManually)
                OnboardingStep.PICK_LEVEL -> PickLevelStep(state.recommended, viewModel::selectLevel)
                OnboardingStep.RESULT -> ResultStep(
                    state, viewModel::setRankingOptIn, viewModel::pickLevelManually,
                    onStart = { viewModel.finish(); onFinished() },
                )
            }
        }
    }
}

@Composable
private fun MicStep(onAllow: () -> Unit, onSkip: () -> Unit) {
    MalchaCard(Modifier.fillMaxWidth(), contentPadding = 28.dp) {
        Icon(MalchaIcons.Logo, null, tint = Color.Unspecified, modifier = Modifier.size(72.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(20.dp))
        Text("출퇴근길 10분,\n차 안의 영어 스피킹", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text(
            "말하기 수업을 하려면 마이크가 필요해요.\n녹음 원본은 저장하지 않고, 말한 단어 수만 기록해요.",
            style = MaterialTheme.typography.bodyLarge, color = MalchaColors.TextSecondary,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton("마이크 허용하기", onClick = onAllow, icon = Icons.Outlined.Mic)
        Spacer(Modifier.height(12.dp))
        SecondaryButton("마이크 없이 레벨만 고르기", onClick = onSkip, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun VoiceTestStep(state: OnboardingUiState, onTest: () -> Unit, onNext: () -> Unit) {
    MalchaCard(Modifier.fillMaxWidth(), contentPadding = 28.dp) {
        Text("음성 테스트", style = MaterialTheme.typography.labelLarge, color = MalchaColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        Text("이렇게 말해 보세요", style = MaterialTheme.typography.headlineSmall)
        Text("\"${LevelTestContent.VOICE_TEST_SENTENCE}\"", style = MaterialTheme.typography.titleLarge, color = MalchaColors.Mint)
        Spacer(Modifier.height(24.dp))
        VoiceStatus(state.voicePhase, Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))
        when (state.voicePhase) {
            VoicePhase.DONE -> {
                Text("잘 들려요 · \"${state.heard}\"", style = MaterialTheme.typography.bodyLarge, color = MalchaColors.Mint, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(20.dp))
                PrimaryButton("다음", onClick = onNext, icon = null)
            }
            VoicePhase.FAILED -> {
                Text("잘 안 들렸어요. 조금 크게 다시 말해 볼까요?", color = MalchaColors.Amber, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(20.dp))
                PrimaryButton("다시 하기", onClick = onTest, icon = Icons.Filled.Repeat)
            }
            VoicePhase.IDLE -> PrimaryButton("테스트 시작", onClick = onTest, icon = Icons.Outlined.Mic)
            else -> Unit
        }
    }
}

@Composable
private fun VoiceStatus(phase: VoicePhase, modifier: Modifier = Modifier) {
    val listening = phase == VoicePhase.LISTENING
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        VoiceRing(
            icon = if (phase == VoicePhase.PLAYING) Icons.Outlined.Headphones else Icons.Outlined.Mic,
            tone = when (phase) {
                VoicePhase.PLAYING -> VoiceTone.BLUE
                VoicePhase.FAILED -> VoiceTone.AMBER
                else -> VoiceTone.MINT
            },
            progress = 0f, size = 180.dp, showProgress = false,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            when (phase) {
                VoicePhase.PLAYING -> "듣는 중"
                VoicePhase.LISTENING -> "말할 차례"
                VoicePhase.DONE -> "확인했어요"
                VoicePhase.FAILED -> "다시 해 볼까요"
                VoicePhase.IDLE -> "준비됐어요"
            },
            style = MaterialTheme.typography.titleLarge,
            color = if (listening) MalchaColors.TextPrimary else MalchaColors.TextSecondary,
        )
    }
}

@Composable
private fun LevelTestStep(
    state: OnboardingUiState,
    onSelf: (Int) -> Unit,
    onReplay: () -> Unit,
    onPickManually: () -> Unit,
) {
    MalchaCard(Modifier.fillMaxWidth(), contentPadding = 24.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("레벨 테스트 · 약 3분", style = MaterialTheme.typography.labelLarge, color = MalchaColors.TextSecondary, modifier = Modifier.weight(1f))
            Text("${state.testPart} / 3", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..3).forEach { part ->
                Box(
                    Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (part <= state.testPart) MalchaColors.Mint else MalchaColors.RingTrack),
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        when (state.step) {
            OnboardingStep.SELF_ASSESS -> {
                Text("지금 영어로 어디까지\n말할 수 있나요?", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                LevelTestContent.selfAssessment.forEachIndexed { i, label ->
                    MalchaCard(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        containerColor = MalchaColors.SurfaceRaised, contentPadding = 18.dp,
                        onClick = { onSelf(i) },
                    ) { Text(label, style = MaterialTheme.typography.titleMedium) }
                }
            }
            OnboardingStep.REPEAT -> {
                val sentence = LevelTestContent.repeatSentences[state.repeatIndex]
                Text("들은 문장을\n그대로 따라 말해 보세요", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${ORDINALS[state.repeatIndex]} 문장 · ${sentence.split(" ").size}단어",
                    style = MaterialTheme.typography.bodyMedium, color = MalchaColors.TextSecondary,
                )
                Spacer(Modifier.height(20.dp))
                VoiceStatus(state.voicePhase, Modifier.align(Alignment.CenterHorizontally))
            }
            OnboardingStep.INTERVIEW -> {
                Text("짧게 대답해 보세요", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "질문 ${state.interviewIndex + 1}/2 · ${LevelTestContent.interviewQuestions[state.interviewIndex]}",
                    style = MaterialTheme.typography.bodyMedium, color = MalchaColors.TextSecondary,
                )
                Spacer(Modifier.height(20.dp))
                VoiceStatus(state.voicePhase, Modifier.align(Alignment.CenterHorizontally))
            }
            else -> Unit
        }

        Spacer(Modifier.height(20.dp))
        TestChecklist(state.testPart)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.step != OnboardingStep.SELF_ASSESS) {
                SecondaryButton(
                    "다시 듣기", onClick = onReplay, icon = Icons.Filled.Repeat, modifier = Modifier.weight(1f),
                    enabled = state.voicePhase == VoicePhase.IDLE || state.voicePhase == VoicePhase.LISTENING,
                )
            }
            SecondaryButton("레벨 직접 고르기", onClick = onPickManually, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ColumnScope.TestChecklist(currentPart: Int) {
    val items = listOf(
        "1. 지금 영어로 어디까지 말할 수 있나요? (자기 평가)",
        "2. 듣고 따라 말하기 · 4단어 → 8단어 → 14단어",
        "3. 짧은 인터뷰 2개 · 하는 일, 일하다 생긴 문제",
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MalchaColors.Background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEachIndexed { i, text ->
            val part = i + 1
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) = when {
                    part < currentPart -> Icons.Filled.Check to MalchaColors.Mint
                    part == currentPart -> Icons.Outlined.RadioButtonChecked to MalchaColors.Mint
                    else -> Icons.Outlined.RadioButtonUnchecked to MalchaColors.TextTertiary
                }
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text, style = MaterialTheme.typography.bodyMedium,
                    color = if (part == currentPart) MalchaColors.TextPrimary else MalchaColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun PickLevelStep(recommended: Level?, onSelect: (Level) -> Unit) {
    Text("레벨을 골라 주세요", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))
    Level.entries.forEach { level ->
        MalchaCard(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            border = if (level == recommended) BorderStroke(1.5.dp, MalchaColors.Mint) else null,
            contentPadding = 18.dp,
            onClick = { onSelect(level) },
        ) {
            Text("${level.code} ${level.title}" + if (level == recommended) " · 추천" else "", style = MaterialTheme.typography.titleLarge)
            Text("${level.cefr} · ${level.theme}", style = MaterialTheme.typography.bodyMedium, color = MalchaColors.TextSecondary)
        }
    }
}

@Composable
private fun ResultStep(
    state: OnboardingUiState,
    onRankingChange: (Boolean) -> Unit,
    onPickOther: () -> Unit,
    onStart: () -> Unit,
) {
    val container = LocalAppContainer.current
    val course = container.curriculum.course(state.chosen)
    Text(if (state.chosen == state.recommended) "추천 레벨이에요" else "선택한 레벨이에요", style = MaterialTheme.typography.bodyLarge, color = MalchaColors.TextSecondary)
    Spacer(Modifier.height(8.dp))
    MalchaCard(Modifier.fillMaxWidth(), border = BorderStroke(1.5.dp, MalchaColors.Mint), contentPadding = 22.dp) {
        Text("${state.chosen.code} ${state.chosen.title} · ${state.chosen.cefr}", style = MaterialTheme.typography.labelLarge, color = MalchaColors.Mint)
        Text(course.title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        course.weeks.forEach { week ->
            Text("${week.number}주 · ${week.title}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 3.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text("출근엔 새 표현 2개, 퇴근엔 아침 표현 복습 · 매 수업 10분", style = MaterialTheme.typography.bodyMedium, color = MalchaColors.TextSecondary)
    }
    Spacer(Modifier.height(16.dp))
    MalchaCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("구간 랭킹에 참여할까요?", style = MaterialTheme.typography.titleMedium)
                Text(
                    "구간 이름과 주간 단어 수만 보내요. 위치 원본·음성은 보내지 않아요. 언제든 끌 수 있어요.",
                    style = MaterialTheme.typography.bodyMedium, color = MalchaColors.TextSecondary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = state.rankingOptIn, onCheckedChange = onRankingChange,
                colors = SwitchDefaults.colors(checkedTrackColor = MalchaColors.Mint),
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    PrimaryButton("이 코스로 시작하기", onClick = onStart, icon = null)
    Spacer(Modifier.height(12.dp))
    SecondaryButton("다른 레벨 고르기", onClick = onPickOther, modifier = Modifier.fillMaxWidth())
}

private val ORDINALS = listOf("첫 번째", "두 번째", "세 번째")

package com.drivelingo.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivelingo.app.LocalAppContainer
import com.drivelingo.core.designsystem.MalchaColors
import com.drivelingo.core.designsystem.components.MalchaCard
import com.drivelingo.core.designsystem.components.RestrictedWhileDriving
import com.drivelingo.core.designsystem.components.SecondaryButton

/** 설정은 상태가 단순해서 별도 ViewModel 없이 저장소를 직접 쓴다. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onRetakeLevelTest: () -> Unit) {
    val container = LocalAppContainer.current
    val settings by container.progress.settings.collectAsStateWithLifecycle()
    val driving by container.drivingMonitor.status.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    // 마이크는 저장값이 아니라 실제 권한을 보여준다. 시스템 설정에서 바꾸고 돌아와도 맞도록 화면에 올 때마다 다시 읽는다.
    val context = LocalContext.current
    var micGranted by remember { mutableStateOf(false) }
    LifecycleResumeEffect(context) {
        micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = MalchaColors.TextPrimary)
            }
            Text("설정", style = MaterialTheme.typography.headlineSmall)
        }
        if (driving.uiRestricted) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                RestrictedWhileDriving("주차 후 바꿀 수 있어요", "운전 중에는 설정을 열지 않아요.")
            }
            return@Column
        }
        Column(
            Modifier.fillMaxWidth().widthIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ToggleCard(
                title = "출발하면 자동으로 시작",
                description = "기본은 꺼짐이에요. 시동만 걸었을 때는 소리를 내지 않고, 켜 두면 출발할 때 수업을 시작해요. " +
                    "(Pleos 자동 재생 정책 검토가 필요한 기능 · docs/policy-review.md)",
                checked = settings.autoStartOnDeparture,
                onChange = { on -> container.progress.updateSettings { it.copy(autoStartOnDeparture = on) } },
            )
            ToggleCard(
                title = "구간 랭킹 참여",
                description = "구간 이름과 주간 단어 수만 보내요. 위치 원본과 음성은 보내지 않아요.",
                checked = settings.rankingOptIn,
                onChange = { on -> container.progress.updateSettings { it.copy(rankingOptIn = on) } },
            )
            MalchaCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
                Text("마이크", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (micGranted) "허용됨 · 녹음 원본은 저장하지 않아요" else "허용 안 됨 · 듣기 수업만 할 수 있어요",
                    style = MaterialTheme.typography.bodyMedium, color = MalchaColors.TextSecondary,
                )
            }
            SecondaryButton("레벨 테스트 다시 하기", onClick = onRetakeLevelTest, modifier = Modifier.fillMaxWidth())
            SecondaryButton("프로토타입 데이터 초기화", onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                "차량 신호: ${if (container.usesMockVehicle) "Mock (테스트 콘솔)" else "Pleos 차량 실연동 (내비·시동·네트워크는 Mock)"}",
                style = MaterialTheme.typography.labelSmall, color = MalchaColors.TextTertiary,
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("데이터를 초기화할까요?") },
            text = { Text("설정과 학습 기록이 처음 상태(시연용 데이터)로 돌아가요. 앱을 다시 시작하면 처음 설정부터 시작해요.") },
            confirmButton = {
                TextButton(onClick = { container.progress.resetAll(); confirmReset = false; onRetakeLevelTest() }) { Text("초기화") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun ToggleCard(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    MalchaCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MalchaColors.TextSecondary)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = MalchaColors.Mint))
        }
    }
}

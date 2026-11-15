package com.drivelingo.app

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.staticCompositionLocalOf
import com.drivelingo.BuildConfig
import com.drivelingo.core.curriculum.CurriculumRepository
import com.drivelingo.core.curriculum.MockCurriculumRepository
import com.drivelingo.core.driving.DrivingMonitor
import com.drivelingo.core.progress.ProgressRepository
import com.drivelingo.core.progress.SharedPrefsStore
import com.drivelingo.core.speech.SimulatedVoiceInput
import com.drivelingo.core.speech.SimulatedVoiceOutput
import com.drivelingo.core.speech.VoiceInput
import com.drivelingo.core.speech.VoiceOutput
import com.drivelingo.core.speech.VoiceSimulation
import com.drivelingo.core.vehicle.AudioFocusRequester
import com.drivelingo.core.vehicle.DrivingSignalSource
import com.drivelingo.core.vehicle.car.CarDrivingSignalSource
import com.drivelingo.core.vehicle.car.CarUxRestrictionsReader
import com.drivelingo.core.vehicle.mock.MockDrivingSignalSource
import com.drivelingo.feature.lesson.coach.EnglishCoach
import com.drivelingo.feature.lesson.coach.MockEnglishCoach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 앱 전체 의존성. DI 프레임워크 없이 한 곳에서 조립한다.
 *
 * Mock ↔ 실제 구현 교체 지점은 이 파일뿐이다. 실제 Pleos SDK 구현을 추가하면 여기서 바꿔 끼운다.
 */
class AppContainer(context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val clock: () -> Long = { SystemClock.elapsedRealtime() }

    /** 테스트 콘솔이 조작하는 Mock 신호. car 모드에서도 CarUxRestrictions 외 신호는 여기서 온다. */
    val mockVehicle = MockDrivingSignalSource(appScope)

    val signalSource: DrivingSignalSource = when (BuildConfig.VEHICLE_SOURCE) {
        "car" -> CarDrivingSignalSource(CarUxRestrictionsReader(context), mockVehicle, appScope)
        else -> mockVehicle
    }

    val drivingMonitor = DrivingMonitor(signalSource, appScope, clock)

    // TODO(pleos): 실제 오디오 포커스는 AudioManager.requestAudioFocus(AudioFocusRequest) 로 구현한다.
    val audioFocus: AudioFocusRequester = mockVehicle

    val voiceSimulation = VoiceSimulation()
    // TODO(pleos): ai.pleos.playground.tts.TextToSpeech / ai.pleos.playground.stt.SpeechToText 로 교체
    val voiceOutput: VoiceOutput = SimulatedVoiceOutput(voiceSimulation)
    val voiceInput: VoiceInput = SimulatedVoiceInput(voiceSimulation)
    // TODO(backend): 백엔드 경유 LLM 코치로 교체. API 키는 앱에 넣지 않는다.
    val coach: EnglishCoach = MockEnglishCoach(voiceSimulation)

    val curriculum: CurriculumRepository = MockCurriculumRepository()
    val progress = ProgressRepository(SharedPrefsStore(context))

    val usesMockVehicle: Boolean get() = BuildConfig.VEHICLE_SOURCE != "car"

    init {
        // 운전한 날을 기록한다(스트릭: 차를 안 탄 날은 끊지 않기 위해). 위치·거리는 기록하지 않는다.
        appScope.launch {
            drivingMonitor.status.map { it.uiRestricted && it.signals.ignitionOn }.distinctUntilChanged()
                .collect { driving -> if (driving) progress.recordTripToday() }
        }
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

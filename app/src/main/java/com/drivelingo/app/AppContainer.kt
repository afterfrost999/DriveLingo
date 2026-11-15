package com.drivelingo.app

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.staticCompositionLocalOf
import com.drivelingo.BuildConfig
import com.drivelingo.core.audio.AudioFocusController
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
import com.drivelingo.core.speech.pleos.PleosCredentials
import com.drivelingo.core.speech.pleos.PleosVoiceInput
import com.drivelingo.core.speech.pleos.PleosVoiceOutput
import com.drivelingo.core.vehicle.AudioFocusRequester
import com.drivelingo.core.vehicle.DrivingSignalSource
import com.drivelingo.core.vehicle.car.CarConnection
import com.drivelingo.core.vehicle.car.CarDrivingSignalSource
import com.drivelingo.core.vehicle.car.CarPropertyReader
import com.drivelingo.core.vehicle.car.CarReadings
import com.drivelingo.core.vehicle.car.CarUxRestrictionsReader
import com.drivelingo.core.vehicle.car.PleosVehicleReader
import com.drivelingo.core.vehicle.mock.MockDrivingSignalSource
import com.drivelingo.feature.lesson.LessonController
import com.drivelingo.feature.lesson.coach.EnglishCoach
import com.drivelingo.feature.lesson.coach.MockEnglishCoach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
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

    /** 런타임 권한(차량 속도 등)을 새로 받으면 값을 올려 차량 리더가 다시 구독하게 한다. */
    private val carPermissionChanges = MutableStateFlow(0)

    fun onCarPermissionsChanged() = carPermissionChanges.update { it + 1 }

    /** 테스트 콘솔이 조작하는 Mock 신호. car 모드에서도 실제 소스가 없는 신호는 여기서 온다. */
    val mockVehicle = MockDrivingSignalSource(appScope)

    /** car 모드: 실제 오디오 포커스. 일반 에뮬레이터(mock)에서는 콘솔로 포커스 상실을 재현한다. */
    private val audioFocusController: AudioFocusController? =
        if (BuildConfig.VEHICLE_SOURCE == "car") AudioFocusController(context) else null

    val signalSource: DrivingSignalSource = when (BuildConfig.VEHICLE_SOURCE) {
        "car" -> CarDrivingSignalSource(carReadings(context), mockVehicle, appScope, clock)
        else -> mockVehicle
    }

    /** car 모드에서 쓰는 실제 차량 값. 소스마다 따로 연결하고 하나로 합친다. */
    private fun carReadings(context: Context): Flow<CarReadings> {
        val car = CarConnection(context, appScope)
        return combine(
            CarUxRestrictionsReader(car).restrictions,
            PleosVehicleReader(context).state,
            CarPropertyReader(context, car, carPermissionChanges).motion,
            checkNotNull(audioFocusController).environment,
        ) { ux, vehicle, motion, audio -> CarReadings(ux = ux, vehicle = vehicle, motion = motion, audio = audio) }
    }

    val drivingMonitor = DrivingMonitor(signalSource, appScope, clock)

    val audioFocus: AudioFocusRequester = audioFocusController ?: mockVehicle

    /** Mock 음성 조작 상태. pleos 모드에서도 시뮬레이터 화면의 "TTS·STT 마지막" 표시에 쓴다. */
    val voiceSimulation = VoiceSimulation()

    val usesPleosVoice: Boolean get() = BuildConfig.VOICE_SOURCE == "pleos"

    private val pleosCredentials = PleosCredentials.of(BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET)

    val voiceOutput: VoiceOutput =
        if (usesPleosVoice) PleosVoiceOutput(context, pleosCredentials, voiceSimulation) else SimulatedVoiceOutput(voiceSimulation)
    val voiceInput: VoiceInput =
        if (usesPleosVoice) PleosVoiceInput(context, pleosCredentials, voiceSimulation) else SimulatedVoiceInput(voiceSimulation)
    // TODO(backend): 백엔드 경유 LLM 코치로 교체. API 키는 앱에 넣지 않는다.
    val coach: EnglishCoach = MockEnglishCoach(voiceSimulation)

    val curriculum: CurriculumRepository = MockCurriculumRepository()
    val progress = ProgressRepository(SharedPrefsStore(context))

    /** 앱 전체에서 수업 하나를 소유한다. 수업 화면과 미디어 세션(운전대 버튼)이 같이 쓴다. */
    val lessons = LessonController(
        drivingMonitor = drivingMonitor,
        voiceOutput = voiceOutput,
        voiceInput = voiceInput,
        coach = coach,
        audioFocus = audioFocus,
        curriculum = curriculum,
        progress = progress,
        appScope = appScope,
        clock = clock,
    )

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

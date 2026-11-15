# Pleos SDK / API 연동 현황

조사 기준은 https://document.pleos.ai(2026-11-16 확인)와 Pleos Maven 저장소의 SDK 바이너리입니다.
클래스·메서드 이름과 시그니처는 공식 문서와 AAR의 공개 API를 대조해 옮겼습니다.
"문서에서 찾지 못함"인 항목은 가짜 API를 만들지 않고 다른 신호로 추정했으며, 이 문서에 따로 표시했습니다.

## 1. 사용하는 SDK

| 모듈 | 좌표 (저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/`) | 진입점 |
|---|---|---|
| Vehicle | `ai.pleos.playground:Vehicle:2.3.2` | `Vehicle(context).initialize()` → `getTurnSignal()` / `getBrake()` / `getSafety()` → `release()` |
| SpeechToText | `ai.pleos.playground:SpeechToText:3.3.1` | `SpeechToText(context, Mode)` → `initialize(OnInitializeListener)` → `addListener(ResultListener)` → `request()` / `stop()` |
| TextToSpeech | `ai.pleos.playground:TextToSpeech:3.3.1` | `TextToSpeech(context, Mode)` → `initialize(...)` → `addEventListener(EventListener)` → `speak(text, isFinal)` / `stop()` |
| AAOS Car API | 플랫폼 (`useLibrary("android.car")`) | `Car.createCar` → `CarUxRestrictionsManager`, `CarPropertyManager` |
| AndroidX Media | `androidx.media:media:1.7.0` | `MediaBrowserServiceCompat`, `MediaSessionCompat` |

- Pleos Sample App 기준 환경은 AGP 8.8.2, Kotlin 2.0.x, compileSdk/targetSdk 35, Pleos Connect v2.1 AVD(Android 14, API 34)입니다. 이 프로젝트도 같습니다.
- 배포 앱은 minSdk 28 이상이어야 합니다(signing key rotation). 이 프로젝트도 28입니다.
- Vehicle SDK 퍼미션(`pleos.car.permission.*`)은 Pleos App Market과 사전 협의해 **승인된 범위만** 쓸 수 있습니다.

## 2. 빌드 옵션

| gradle 속성 | 값 | 바뀌는 것 |
|---|---|---|
| `drivelingo.vehicleSource` | `mock`(기본) / `car` | 차량 신호와 오디오 포커스. `car`이면 아래 표의 "실연동" 신호를 실제 소스에서 읽습니다 |
| `drivelingo.voiceSource` | `simulated`(기본) / `pleos` | 음성. `pleos`이면 Pleos STT/TTS로 실제 소리를 내고 듣습니다 |
| `drivelingo.pleosClientId` / `drivelingo.pleosClientSecret` | 빈 값(기본) | 있으면 STT/TTS를 `HYBRID` + `registerApp()`으로, 없으면 `ON_DEVICE`로 씁니다 |

```bash
# Pleos Connect 에뮬레이터에서 실제 신호 + 실제 음성
AVD=Pleos_Connect_v2.1 scripts/run.sh -Pdrivelingo.vehicleSource=car -Pdrivelingo.voiceSource=pleos
```

앱 등록 정보는 저장소에 넣지 않습니다. 로컬에서는 `~/.gradle/gradle.properties`에 두거나 `-P`로 넘깁니다.
BuildConfig에 들어가므로 **배포 빌드에서는 쓰지 말고** 백엔드에서 받아 오는 방식으로 바꿔야 합니다.

## 3. 신호별 출처와 현재 상태

`car` 모드 기준입니다. `mock` 모드에서는 모든 신호를 차량 시뮬레이터와 adb로 넣습니다.

| 앱이 쓰는 신호 | 실제 출처 | 필요 권한 | 상태 | 코드 |
|---|---|---|---|---|
| 주행 규제 | `CarUxRestrictionsManager.registerListener` | 없음 | **실연동** | `core/vehicle/car/CarUxRestrictionsReader.kt` |
| 속도 | `CarPropertyManager` `PERF_VEHICLE_SPEED` (m/s → km/h) | `android.car.permission.CAR_SPEED` (**런타임 권한**, 앱이 요청) | **실연동** | `core/vehicle/car/CarPropertyReader.kt` |
| 기어 | `CarPropertyManager` `GEAR_SELECTION` | `android.car.permission.CAR_POWERTRAIN` | **실연동** | 같음 |
| 방향지시등 | Vehicle SDK `TurnSignal.registerTurnSignalState` | `pleos.car.permission.CAR_LIGHTS` | **실연동** | `core/vehicle/car/PleosVehicleReader.kt` |
| 브레이크 페달 | Vehicle SDK `Brake.registerBrakePedalStatus` | `pleos.car.permission.CAR_DRIVE` | **실연동** (급제동 판단에 사용) | 같음 |
| 차량 경고 | Vehicle SDK `Safety.registerTelltales` | `pleos.car.permission.CAR_SAFETY` | **실연동** | 같음 |
| 급제동 | **전용 API를 문서에서 찾지 못함.** 감속도 ≥ 13km/h/s + 브레이크 페달(모르면 밟음으로 봄) | (속도·CAR_DRIVE) | **추정** | `core/driving/VehicleEvents.kt` `SuddenBrakingDetector` |
| 출발 후 경과 | 기어가 P에서 바뀐 시각 | (기어) | **추정** | `DepartureTracker` |
| 오디오 포커스 | `AudioManager.requestAudioFocus(AudioFocusRequest)` | 없음 | **실연동** | `core/audio/AudioFocusController.kt` |
| 내비 음성 안내 | **"안내 시작/끝" 이벤트를 문서에서 찾지 못함.** 재생 중인 소리 중 `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`가 있는지 봅니다(`AudioPlaybackCallback`) | 없음 | **실연동(추정)** | 같음 |
| 전화 | **Pleos 전용 API를 문서에서 찾지 못함.** 오디오 모드(`RINGTONE`/`IN_CALL`/`IN_COMMUNICATION`)와 통화 재생 용도로 봅니다 | 없음 | **실연동(추정)** | 같음 |
| STT | SpeechToText SDK (en-US) | `pleos.car.permission.STT_SERVICE` | **실연동** (`voiceSource=pleos`) | `core/speech/pleos/PleosVoiceInput.kt` |
| TTS | TextToSpeech SDK | `pleos.car.permission.TTS_SERVICE` | **실연동** (`voiceSource=pleos`) | `core/speech/pleos/PleosVoiceOutput.kt` |
| 스티어링 휠 키·미디어 화면 | `MediaSessionCompat` + `MediaBrowserServiceCompat` | 없음 | **구현** | `feature/media/` |
| 시동 | `CarPropertyManager` `IGNITION_STATE`로 가능 | `CAR_POWERTRAIN` | Mock | |
| 도착까지 남은 시간·다음 회전 거리 | NaviHelper `onRouteStateInfo`, `onTBTInfo` | NaviHelper 권한 | Mock | |
| 네트워크 | Android `ConnectivityManager` | `ACCESS_NETWORK_STATE` | Mock (콘솔 토글) | |
| LLM 코치 | 차량 앱 → **자체 백엔드** → LLM API. 키는 앱에 넣지 않습니다 | – | `MockEnglishCoach` (규칙 기반) | |

### 못 읽은 값은 어떻게 다루나

실제 소스가 있는 신호는 읽지 못해도 **Mock으로 채우지 않습니다**. Mock으로 채우면 연동 실패가 가려지기 때문입니다.
대신 안전한 기본값을 씁니다(`CarSignalMerger`, 단위 테스트 있음).

| 못 읽은 신호 | 기본값 | 결과 |
|---|---|---|
| 주행 규제 | null | 안전 제한 모드 |
| 속도 | null | 주행 규제 중이면 안전 제한 모드(UNKNOWN) |
| 기어 | UNKNOWN | 주차로 보지 않음 |
| 방향지시등·경고등 | 꺼짐 | 이 차량에서는 해당 중단이 일어나지 않음. 모른다고 수업을 계속 멈출 수는 없기 때문입니다 |

Vehicle SDK는 차종마다 지원 범위가 다릅니다. 그래서 등록 전에 `checkXxxCapabilities`로 읽기 가능 여부(`AccessType.READ`/`READ_WRITE`)를 확인합니다.
지원하지 않는 차량에서는 CarUxRestrictions와 속도만으로 동작합니다(`adb logcat -s PleosVehicle`로 확인).

### 경고등 → 중단 사유

경고등이 고칠 때까지 계속 켜져 있으면 수업을 다시 할 수 없습니다. 그래서 **새로 켜진 순간부터 5초만** 중단 사유로 봅니다(`NewWarningGate`).
중단 사유로 보는 경고등은 `MASTER_WARNING`, `FCA`, `DRIVER_ATTENTION`, `AIRBAG`, `ABS`, `ENGINE_OIL_PRESSURE`, `ENGINE_TEMPERATURE`, `EV/HEV/FCEV/EREV_SYSTEM`, `POWER_DOWN`, `H2_LEAK`입니다.
`ESC`는 깜빡일 때(자세 제어 개입 중)만 봅니다. 목록은 Pleos와 협의해 조정해야 합니다.

## 4. 검증이 필요한 것 (Pleos Connect 에뮬레이터·실차)

이번 작업에서는 일반 JVM 단위 테스트와 빌드만 확인했습니다. 아래 항목은 Pleos Connect 에뮬레이터에서 아직 돌려 보지 않았습니다.

1. **Pleos TTS 재생이 앱의 오디오 포커스를 가져가는지.** TTS는 Pleos 음성 서비스(CAAS) 프로세스가 재생합니다. 그 서비스가 따로 포커스를 요청하면 앱이 `LOSS_TRANSIENT`를 받아 자기 말에 스스로 멈출 수 있습니다. 그렇다면 `TextToSpeech.setAudioAttributes()`로 용도를 맞추거나, TTS 재생 중의 포커스 상실은 무시하도록 바꿔야 합니다.
2. ON_DEVICE 모드 STT/TTS의 영어(en-US) 지원 여부. `UNAVAILABLE_LANGUAGE`가 오면 앱 등록(HYBRID)이 필요합니다.
3. STT `onUpdated(text, completed=true)`가 누적 문장으로 오는지 구간별로 오는지. 두 경우 모두 처리하도록 짰습니다.
4. TTS 속도: SDK에 속도 API가 없어서 퇴근 복습 감속(`speechRate`)은 실제 음성에 아직 적용하지 않았습니다.
5. 다른 앱의 재생 용도(`AudioPlaybackConfiguration.getAudioAttributes()`)가 Pleos 내비 안내에서 `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`로 보이는지.
6. 급제동 임계값(13km/h/s), 경고 유지 시간(5초)은 실제 주행 데이터로 조정해야 합니다.

## 5. 교체 지점

모든 교체는 `app/AppContainer.kt`에서 합니다.

```kotlin
val signalSource = when (BuildConfig.VEHICLE_SOURCE) {
    "car" -> CarDrivingSignalSource(carReadings(context), mockVehicle, appScope, clock)  // UX + Vehicle SDK + CarProperty + AudioManager
    else  -> mockVehicle
}
val audioFocus = audioFocusController ?: mockVehicle                                    // car: AudioFocusController
val voiceOutput = if (usesPleosVoice) PleosVoiceOutput(...) else SimulatedVoiceOutput(...)
val voiceInput  = if (usesPleosVoice) PleosVoiceInput(...)  else SimulatedVoiceInput(...)
val coach: EnglishCoach = MockEnglishCoach(voiceSimulation)                             // → RemoteEnglishCoach(백엔드)
```

다음 연동 순서로는 NaviHelper(도착 시간·다음 회전), 시동(`IGNITION_STATE`), 네트워크(`ConnectivityManager`), 백엔드 LLM 코치를 권장합니다.

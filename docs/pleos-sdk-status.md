# Pleos SDK / API 연동 현황

조사 기준: https://document.pleos.ai (2026-11-15 확인). 아래 클래스·메서드 이름은 공식 문서에 적힌 것만 옮겼습니다.
"문서에서 찾지 못함"은 가짜로 구현하지 않고 Mock으로 둔 뒤 이 문서에 따로 표시했습니다.

## 1. 확인한 SDK

| 모듈 | 좌표 (저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/`) | 진입점 |
|---|---|---|
| Vehicle | `ai.pleos.playground:Vehicle:2.3.2` | `Vehicle(context).initialize()` → `getXxx()` 도메인 객체 → `release()` |
| SpeechToText | `ai.pleos.playground:SpeechToText:3.3.1` | `ai.pleos.playground.stt.SpeechToText(context, Mode.HYBRID)` |
| TextToSpeech | `ai.pleos.playground:TextToSpeech` (문서 기준 v3.3.1) | `ai.pleos.playground.tts.TextToSpeech(context, Mode.HYBRID)` |
| NaviHelper | 문서 기준 v3.0.5 | `NaviHelper` + `NaviHelperEventListener` |

- Pleos Sample App 기준 환경은 AGP 8.8.2, Kotlin 2.0.0, compileSdk/targetSdk 35, Pleos_Connect_v2.1 AVD(Android 14)입니다. 이 프로젝트는 같은 AGP에 Kotlin 2.0.21을 씁니다.
- 배포 앱은 minSdk 28 이상이어야 합니다(signing key rotation). 이 프로젝트도 28입니다.
- Vehicle SDK 퍼미션(`pleos.car.permission.*`)은 Pleos App Market과 사전 협의해 **승인된 범위만** 쓸 수 있습니다. 승인 없이 선언하면 심사를 통과하지 못합니다.

## 2. 신호별 출처와 현재 상태

| 앱이 쓰는 신호 | 실제 출처 (문서 근거) | 필요 권한 | 현재 구현 |
|---|---|---|---|
| 주행 규제 | AAOS `CarUxRestrictionsManager.registerListener` / `onUxRestrictionsChanged` (Pleos 주행 규제 개발 가이드) | 없음 | ✅ **실연동** `core/vehicle/car/CarUxRestrictionsReader.kt` (`-Pdrivelingo.vehicleSource=car`) |
| 속도·기어 | AAOS `CarPropertyManager` + `VehiclePropertyIds` (Pleos "표준 데이터는 AAOS Car API" 정책) | `android.car.permission.CAR_SPEED`, `CAR_POWERTRAIN` | ⚠️ Mock |
| 방향지시등 | Vehicle SDK `TurnSignal`: `registerTurnSignalState` / `getCurrentTurnSignalState` / `checkTurnSignalStateCapabilities` | `pleos.car.permission.CAR_LIGHTS` | ⚠️ Mock |
| 브레이크 페달 | Vehicle SDK `Brake`: `registerBrakePedalStatus` / `getCurrentBrakePedalStatus` | `pleos.car.permission.CAR_DRIVE` | ⚠️ Mock |
| 급제동 | **전용 API를 문서에서 찾지 못함.** 속도 변화율(감속도)로 추정해야 합니다 | (속도 권한) | ⚠️ Mock (펄스 이벤트) |
| 차량 경고 | Vehicle SDK `Safety`: `registerTelltales` / `getTelltalesInfo` 등 | `pleos.car.permission.CAR_SAFETY` | ⚠️ Mock. 어떤 경고등을 "중단 사유"로 볼지는 검토가 필요합니다 |
| 내비 음성 안내 | **전용 "음성 안내 시작/끝" 이벤트를 문서에서 찾지 못함.** NaviHelper에는 `onTBTInfo`, `onRouteStateInfo` 등만 있습니다. 실제로는 내비가 `NAVIGATION` 컨텍스트로 포커스를 요청할 때 오는 `AUDIOFOCUS_LOSS_TRANSIENT(_CAN_DUCK)`로 감지합니다 | 없음 | ⚠️ Mock |
| 전화 | **Pleos 전용 API를 문서에서 찾지 못함.** `CALL` 컨텍스트가 포커스를 가져갈 때 오는 오디오 포커스 상실로 감지합니다 | 없음 | ⚠️ Mock |
| 오디오 포커스 | Android `AudioManager.requestAudioFocus(AudioFocusRequest)` + Pleos Interaction Matrix | 없음 | ⚠️ Mock (`AudioFocusRequester` 인터페이스만 있음) |
| 도착까지 남은 시간·다음 회전 거리 | NaviHelper `onRouteStateInfo`, `onTBTInfo` (정확한 필드는 API 레퍼런스에서 확인 필요) | NaviHelper 권한 | ⚠️ Mock |
| 네트워크 | Android `ConnectivityManager` | `ACCESS_NETWORK_STATE` | ⚠️ Mock (콘솔 토글) |
| STT | SpeechToText SDK: `initialize()` → `addListener(ResultListener)` → `request()` / `stop()` | `pleos.car.permission.STT_SERVICE` | ⚠️ `SimulatedVoiceInput` |
| TTS | TextToSpeech SDK: `speak()` + `EventListener.onDone()` / `onStop()` / `onError()` | TTS 권한 | ⚠️ `SimulatedVoiceOutput` (소리 없음) |
| LLM 코치 | 차량 앱 → **자체 백엔드** → LLM API. 키는 앱에 넣지 않습니다 | – | ⚠️ `MockEnglishCoach` (규칙 기반). 백엔드는 이번 범위가 아닙니다 |

참고: STT/TTS의 `HYBRID` 모드는 서버를 먼저 쓰고, 네트워크가 끊기면 On-device로 자동 전환합니다. 서버 모드를 쓰려면 My Project의 Client ID/Secret으로 `registerApp()`을 해야 합니다. 이 Secret은 앱에 하드코딩하지 말고 백엔드에서 내려받는 방식을 검토해야 합니다.

## 3. 교체 방법

모든 교체는 `app/AppContainer.kt`에서 합니다.

```kotlin
val signalSource: DrivingSignalSource = when (BuildConfig.VEHICLE_SOURCE) {
    "car" -> CarDrivingSignalSource(CarUxRestrictionsReader(context), mockVehicle, appScope)
    else  -> mockVehicle
}
val voiceOutput: VoiceOutput = SimulatedVoiceOutput(voiceSimulation)   // → PleosTtsVoiceOutput
val voiceInput: VoiceInput = SimulatedVoiceInput(voiceSimulation)      // → PleosSttVoiceInput
val coach: EnglishCoach = MockEnglishCoach(voiceSimulation)            // → RemoteEnglishCoach(백엔드)
```

실제 구현을 추가하는 순서는 다음을 권장합니다.
1. `PleosVehicleSignalSource`: `Vehicle` → `getTurnSignal()`, `getBrake()`, `getSafety()`로 등록하고 `DrivingSignals`에 합칩니다. 등록 전에 `checkXxxCapabilities`로 지원 여부를 확인합니다. 지원하지 않는 신호는 `null`/기본값으로 두고, 판단 로직이 안전한 쪽으로 해석하게 합니다.
2. `AudioFocusController`: `AudioFocusRequest`의 포커스 변화를 `DrivingSignals.audioFocus`로 넣습니다. 내비 안내와 전화도 여기서 감지합니다.
3. Pleos STT/TTS 구현: `VoiceInput`/`VoiceOutput` 인터페이스를 그대로 구현합니다.

# 테스트와 수동 재현

## 단위 테스트

```bash
./gradlew :app:testDebugUnitTest
```

| 테스트 | 검증하는 것 |
|---|---|
| `LessonEngineTest` (22개) | 상태 전환, 에코 가드, 늦게 온 콜백 무시, 중단·재개 지연, 재개 시 모드 재확인, 긴 대답 끊지 않기, AI 타임아웃·오프라인 대체, 인식 실패 재시도, 포커스 영구 상실 시 수동 재개, 중단 지점 저장·복원, 종료 |
| `SituationClassifierTest` (10개) | 6가지 상황 판단, CarUx 모름 → 안전 모드, 보수적 판단, 안정화, 중단 사유 우선순위 |
| `LessonUiMapperTest` | 상태별 화면 문구·색, 주차 중에만 텍스트 표시 |
| `LessonControllerTest` | 시작/재생 전에는 수업이 시작되지 않음, 화면과 미디어 버튼이 같은 수업을 조작, 시동 꺼짐 → 중단 |
| `VehicleEventsTest` | 새 경고만 5초 중단, 급제동(감속도·브레이크·오래된 표본), 출발 시점 |
| `CarSignalMergerTest` | car 모드에서 실제 값 우선, 못 읽으면 Mock 대신 안전한 기본값, 기어 코드 변환 |
| `AudioEnvironmentRulesTest` | 포커스 변화·요청 결과 변환, 재생 용도로 내비 안내, 오디오 모드로 전화 감지 |
| `LessonPlaybackMapperTest` | 미디어 세션 재생 상태·가능한 동작, 부제에 학습 문장·숫자 없음 |
| `ProgressTest` | 스트릭(차 안 탄 날 유지), 진도·주차 넘김, 저장소 재시작 후 복원, 랭킹 |
| `ReportNarratorTest`, `LevelEstimatorTest` | 리포트 문장(조사 포함), 레벨 추천 |
| `VoiceCommandParserTest` | 음성 명령 10가지의 영어·한국어·로마자 표현 약 190개, 부정("don't stop", "멈추지 마"), 명령으로 오인하면 안 되는 대답, 기대 답 우선, 긴 발화 |
| `LessonCommandTest` | 다시·다음·천천히/빠르게·한국어로·모르겠어(시범)·오늘은 그만·듣기만·잠깐·도움말 처리, 운전대 "다음", 명령 반복 시 다음으로 |
| `LessonEarconTest`, `EarconSynthTest` | 효과음이 나는 시점(말할 차례·콤보·멈춤·재개·띵동·완료), 멈춤음을 내지 않는 사유, 0.5초 이내·클릭 없음·콤보 음 상승 |
| `DrivingConditionsTest` | 비·야간·초행길 감지, 상황별 한 단계 낮춤, 여러 조건도 한 단계, 주차 불변, 간헐 와이퍼·터널 유지 시간 |
| `SpeechChunkerTest` | 한·영 섞인 문장을 언어별 조각으로, 느린 속도의 쉼표 분리 |

## 앱에서 손으로 재현하기 — 차량 시뮬레이터

디버그 빌드 화면 오른쪽 가운데의 자동차 버튼을 누르면 시뮬레이터 패널이 열립니다. 패널 위쪽에서 현재 판단 결과(상황, 학습 모드, 비·야간 보정, CarUx, 중단 사유, TTS가 읽는 문장, STT 결과, 마지막 효과음)를 실시간으로 볼 수 있습니다.

| 재현할 것 | 조작 |
|---|---|
| 주차 → 출발 → 정차 | 프리셋 `주차` → `출발 직후` → `안정 주행` → `정차 · 신호 대기` |
| 정차 중 긴 대답 후 출발 | `정차` 상태에서 "길게 말해 보세요"가 나오면 `안정 주행`으로 바꿉니다. 대답이 끝날 때까지 계속 듣는지 확인합니다 |
| 가다 서다 | `가다 서다` (속도가 0↔20km/h를 오갑니다. 판단까지 몇 초 걸립니다) |
| 방향지시등 | `방향지시등 ←`를 켜면 즉시 멈춥니다. 끄면 2초 뒤에 이어서 합니다 |
| 내비 안내 / 급제동 | 각각 4초 / 1초 펄스입니다 |
| 전화 | `전화`를 켜고 끕니다 |
| 오디오 포커스 영구 상실 | `포커스 영구 상실` → 자동 재개되지 않고, 수업 화면의 재생 버튼을 눌러야 이어집니다 |
| 네트워크 실패 | `네트워크 끊김` → "오프라인 · 저장된 복습 카드" 배지가 뜨고 따라 말하기로 바뀝니다 |
| AI 응답 지연 | `AI 응답 지연` → 6초 뒤 로컬 피드백으로 넘어갑니다 |
| 음성 인식 실패 / 무응답 | `다음 음성 인식`에서 `인식 실패`·`무응답` (1회) → 한 번 다시 묻고, 그다음에는 모범 답안을 들려줍니다 |
| 상태를 알 수 없음 | `상태 불명` → 안전 제한 모드(듣기만) |
| 비·야간·초행길 | `날씨 · 도로`에서 `비 (와이퍼)`·`야간 (전조등)`·`초행길` → `보정`에 표시되고 학습 모드가 한 단계 낮아집니다(안정 주행: 짧은 대답 → 따라 말하기). 끈 뒤에도 비는 2분, 야간은 1분 유지됩니다 |
| 음성 명령 | `다음에 말할 음성 명령`에서 고르면 다음 말할 차례에 그 문장을 들은 것으로 합니다(잠깐·다시·다음·천천히·한국어로·모르겠어·듣기만·오늘은 그만·도움말). 다른 표현은 `VoiceCommandParserTest`에 있습니다 |
| 효과음 | 시뮬레이션 음성에서는 소리 없이 `효과음 마지막`에 표시됩니다. 말할 차례 '띵', 맞은 답마다 콤보 1~5단, 일시정지·방향지시등 '톡', 재개, 정차 질문 '띵동', 마무리 멜로디 |
| 운행 종료 | `시동 끔` → 수업이 중단 지점을 저장하고 홈으로 갑니다. 다시 시작하면 "이어서 하기"가 나옵니다 |

## adb로 재현하기 (디버그 빌드)

```bash
A="adb shell am broadcast -a com.drivelingo.debug.VEHICLE"
$A --es preset PARKED            # PARKED STOPPED_AT_LIGHT CRUISING STOP_AND_GO COMPLEX DEPARTING ARRIVING UNKNOWN IGNITION_OFF
$A --es turn left                # left | right | hazard | off
$A --es nav pulse                # pulse | on | off
$A --es brake pulse
$A --es warning on               # on | off
$A --es call on                  # on | off
$A --es focus lost               # gained | transient | duck | lost
$A --es network off              # on | off
$A --es answer fail              # auto | fail | silent (다음 1회)
$A --es slowcoach on             # on | off
$A --es say "Can you say that again?"   # 다음 말할 차례에 이 문장을 들은 것으로 함(음성 명령 시험)
$A --es wiper on                 # on | off  (비 → 한 단계 낮춤)
$A --es headlamp on              # on | off  (야간 → 한 단계 낮춤)
$A --es newroute on              # on | off  (초행길 → 한 단계 낮춤)
adb logcat -s DebugVehicle       # 적용 결과 확인
```

## 화면 회전·리사이즈

- 수업 세션은 `LessonViewModel`이 갖고 있어서, 회전하거나 창 크기가 바뀌어도 수업이 끊기지 않습니다.
- 확인 방법: 에뮬레이터 회전 버튼을 누르거나 `adb shell settings put system user_rotation 1`(가로) / `0`(세로)을 실행합니다. 리사이즈는 `adb shell wm size 1920x720`(차량 와이드 비율), 되돌릴 때는 `adb shell wm size reset`입니다.

## Pleos Connect 에뮬레이터에서 확인하기

### 1. 설치 (한 번만)

```bash
scripts/setup-pleos-emulator.sh        # VS Code: Tasks: Run Task → "말차: Pleos Connect 에뮬레이터 설치"
```

- 공식 절차(Android Studio → SDK Manager → SDK Update Sites에 Pleos 이미지 URL 추가 → Automotive / Pleos Connect v2.1.0 AVD 생성)를 터미널에서 똑같이 합니다.
  Mac(Apple Silicon)은 `crp-arm64-sys-img.xml`, Windows/Linux는 `crp-sys-img.xml` 이미지를 씁니다.
- 이미지는 약 4.7GB이고, **42dot SDK 라이선스 전문을 보여 준 뒤 동의해야** 설치합니다.
- AVD 설정은 공식 가이드대로 맞춥니다. **Cold boot 강제**(Quick boot이면 VHAL을 못 씁니다), RAM 4GB, 하드웨어 그래픽, 마이크 입력을 켭니다.
- 화면은 이미지에 들어 있는 기기 정의 `Pleos_Connect_v2_0`(2560×1440, mdpi)으로 만듭니다. 일반 Automotive 기기(1024×768)로 만들면 홈·클러스터·공조 바가 겹쳐 화면이 깨집니다.
- 내려받기가 멈추면 60초 뒤 끊고 이어 받습니다. 중간에 멈춰도 다시 실행하면 이어 받습니다.
- 공식 권장 사양은 외장 GPU입니다(최소 RTX 3060 Laptop 6GB급). Apple M2(16GB)에서도 약 20초 만에 부팅되고 정상 동작했습니다(2026-11-16).
- Android Studio로 설치해도 됩니다. 이때도 AVD 이름을 `Pleos_Connect_v2.1`로 하면 아래 명령을 그대로 쓸 수 있습니다.

화면 크기: Pleos 디스플레이는 2560×1440·160dpi라 1dp = 1px입니다. 앱은 디스플레이 짧은 변 기준으로 dp·sp를 키웁니다(`DisplayScale`, 1440dp → 1.8배).
그래서 본문 글자가 Pleos 시스템 UI(약 28~30px)와 비슷하게 나오고, 2:1 가로 창은 약 934×672dp 화면처럼 그려집니다. 일반 에뮬레이터·휴대폰은 1배입니다.

### 2. car 모드로 실행하고 CarUxRestrictions 확인

```bash
AVD=Pleos_Connect_v2.1 scripts/run.sh
scripts/check-car-ux.sh                # VS Code: "말차: Pleos 에뮬레이터에서 CarUx 확인"
```

`check-car-ux.sh`는 기어·속도를 바꿔 주차 → 정차 → 주행(60km/h) → 주차를 만들고, 단계마다 플랫폼 상태(`dumpsys car_service`)와 앱이 받은 값(`logcat -s CarUxRestrictions`)을 나란히 보여 줍니다.

- Pleos Connect 이미지는 **user 빌드**라 `cmd car_service inject-vhal-event`가 막혀 있습니다(`requires non-user build`, root도 안 됨).
  그래서 스크립트는 **Pleos Connect Virtual Console의 HTTP API**로 값을 넣습니다(`adb forward tcp:8088 tcp:8088` 후 `PUT /vehicle/gear?gear=Drive`, `PUT /vehicle/controls?speed=60`).
  콘솔은 브라우저 세션이 살아 있을 때만 입력을 받아서, 스크립트가 확인하는 동안 세션 신호(`POST /browser/session`)를 1초마다 보냅니다.
- 브라우저에서 http://127.0.0.1:8088/ 을 열어 손으로 바꿔도 결과는 같습니다. 일반 AAOS 에뮬레이터(userdebug)에서는 VHAL 주입을 씁니다.

Pleos Connect v2.1의 UX 제한 설정(`dumpsys car_service --services CarUxRestrictionsManagerService`)은 아래와 같습니다.
일반 AAOS 기본값(주행 중 `FULLY_RESTRICTED`=511)과 달리 **정차·주행 모두 `NO_VIDEO|NO_SETUP`(0x50=80)** 입니다.
앱은 개별 플래그가 아니라 `requiresDistractionOptimization`으로 화면을 제한하므로 결과는 같습니다.

| 단계 | 플랫폼 주행 상태 | 기대 결과 (2026-11-16 확인) |
|---|---|---|
| 주차 (P, 0km/h) | PARKED(0) | `requiresDO=false active=0`, 화면 텍스트·설정 표시 |
| 정차 (D, 0km/h) | IDLING(1) | `requiresDO=true active=80 [NO_VIDEO, NO_SETUP]`, "주차 후 이어서 할게요" 화면 |
| 주행 (D, 60km/h) | MOVING(2) | 정차와 같은 값이라 앱 로그는 새로 찍히지 않음(변화 없음) |
| 다시 주차 | PARKED(0) | `requiresDO=false active=0`, 제한 해제 |

로그가 `unknown`이면 Car 서비스에 연결하지 못한 것입니다.
이때 앱은 안전 제한 모드로 동작합니다. 일반 에뮬레이터이거나 cold boot가 아닌 경우를 의심하세요.

### 3. 실제 신호·음성·미디어 버튼 확인 (미검증 항목 체크리스트)

```bash
AVD=Pleos_Connect_v2.1 scripts/run.sh
adb logcat -s CarUxRestrictions CarProperty PleosVehicle AudioFocus PleosTts PleosStt LessonMedia
```

| 확인할 것 | 방법 | 기대 결과 |
|---|---|---|
| 속도 권한 | 첫 실행 | 차량 속도 권한 요청이 "위치 정보 액세스"로 뜹니다(AAOS에서 `CAR_SPEED`는 위치 권한 그룹). 허용하면 `CarProperty: PERF_VEHICLE_SPEED subscribed=true`. adb로 줄 때는 앱이 도는 사용자(보통 10)를 지정합니다: `adb shell pm grant --user $(adb shell am get-current-user) com.drivelingo android.car.permission.CAR_SPEED` |
| Vehicle SDK 지원 여부 | 실행 직후 로그 | `turn signal / brake pedal / telltales supported (READ...)`. 지원하지 않으면 "treated as unknown" |
| 방향지시등 | Virtual Console에서 지시등 켜기(`PUT /vehicle/turn-signal?mode=BlinkLeft`) | 수업이 즉시 멈추고, 끄면 이어서 함. 시뮬레이터 패널 `중단 사유`에 방향지시등(확인함). Pleos가 측후방 카메라 창을 함께 띄웁니다 |
| 급제동 | Virtual Console에서 속도를 빠르게 낮추며 브레이크 | 1초 중단 |
| 경고등 | Virtual Console에서 경고등 켜기(`PUT /vehicle/telltales/simulator?type=MasterWarning&state=On&color=Red`) | 5초 중단 후 켜져 있어도 재개. `중단 사유`에 차량 경고(확인함) |
| 속도·기어 | `scripts/check-car-ux.sh` 또는 Virtual Console | 시뮬레이터 패널 `속도·기어`가 실제 값으로 바뀜 |
| 실제 음성 | 주차 중 수업 시작 | TTS가 실제로 들리고(확인함), 말할 차례에 마이크가 열려 인식 결과가 `STT 마지막`에 표시 |
| 마이크 입력 | 에뮬레이터를 `-allow-host-audio`로 켜기(`scripts/run.sh`가 붙임) | 이 옵션이 없으면 에뮬레이터가 마이크 입력을 0으로 채워 STT가 아무것도 듣지 못합니다. macOS에서 에뮬레이터를 띄운 앱(터미널·VS Code)에 마이크 권한이 있어야 합니다(시스템 설정 → 개인정보 보호 및 보안 → 마이크) |
| TTS와 포커스 | 수업 중 `AudioFocus` 로그 | 코치가 말할 때 `focus change ... LOST_TRANSIENT`가 **나오지 않아야** 합니다(에뮬레이터 확인함). 나오면 TTS 재생 중의 포커스 상실을 무시하도록 바꿔야 합니다 |
| 내비 안내 | 내비 경로 안내 중 음성 안내 | 안내 동안 멈춤 |
| 운전대 키 | 아래 adb 명령 또는 에뮬레이터 미디어 키 | 일시정지·재개·다시 듣기 |

미디어 버튼은 일반 에뮬레이터에서도 확인할 수 있습니다.

```bash
adb shell cmd media_session dispatch play-pause   # 재생 중 → 일시정지, 일시정지 → 재개
adb shell cmd media_session dispatch play         # 수업이 없으면 새 수업 시작 (사용자 동작)
adb shell cmd media_session dispatch previous     # 마지막 문장 다시 듣기
adb shell cmd media_session dispatch stop         # 중단 지점 저장 후 멈춤
adb shell dumpsys media_session | grep -A5 LessonMedia   # 재생 상태·메타데이터 확인
```

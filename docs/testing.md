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
| `ProgressTest` | 스트릭(차 안 탄 날 유지), 진도·주차 넘김, 저장소 재시작 후 복원, 랭킹 |
| `ReportNarratorTest`, `LevelEstimatorTest` | 리포트 문장(조사 포함), 레벨 추천 |

## 앱에서 손으로 재현하기 — 차량 시뮬레이터

디버그 빌드 화면 오른쪽 가운데의 자동차 버튼을 누르면 시뮬레이터 패널이 열립니다. 패널 위쪽에서 현재 판단 결과(상황, 학습 모드, CarUx, 중단 사유, TTS가 읽는 문장, STT 결과)를 실시간으로 볼 수 있습니다.

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
adb logcat -s DebugVehicle       # 적용 결과 확인
```

## 화면 회전·리사이즈

- 수업 세션은 `LessonViewModel`이 갖고 있어서, 회전하거나 창 크기가 바뀌어도 수업이 끊기지 않습니다.
- 확인 방법: 에뮬레이터 회전 버튼을 누르거나 `adb shell settings put system user_rotation 1`(가로) / `0`(세로)을 실행합니다. 리사이즈는 `adb shell wm size 1920x720`(차량 와이드 비율), 되돌릴 때는 `adb shell wm size reset`입니다.

# 아키텍처

## 폴더 구조

기능별 코로케이션(feature-first)을 기본으로 하고, 여러 기능이 함께 쓰는 것만 `core/`에 둡니다.
모듈은 `:app` 하나입니다. 프로토타입 규모에서 멀티 모듈은 빌드 설정만 늘리기 때문입니다.
패키지 경계는 모듈을 나눌 때 그대로 쓸 수 있게 잡았습니다.

```
app/src/main/java/com/drivelingo/
├── app/                      앱 진입점과 조립
│   ├── DriveLingoApplication.kt
│   ├── AppContainer.kt       수동 DI. Mock ↔ 실제 구현을 바꿔 끼우는 곳은 여기 한 군데입니다
│   └── MainActivity.kt
├── navigation/AppNavHost.kt  화면 라우팅
│
├── core/                     여러 기능이 같이 쓰는 것
│   ├── designsystem/         색·타이포·공용 컴포넌트(카드, 큰 버튼, 음성 링)
│   ├── vehicle/              차량·환경 신호 "입력 경계"
│   │   ├── DrivingSignals.kt, UxRestrictions.kt, DrivingSignalSource.kt (인터페이스)
│   │   ├── mock/             테스트 콘솔·adb 로 조작하는 Mock 신호
│   │   └── car/              AAOS CarUxRestrictionsManager 실연동
│   ├── driving/              신호 → 운전 상황·학습 모드 판단 (순수 Kotlin)
│   │   ├── SituationClassifier.kt  6가지 상황 + UNKNOWN
│   │   ├── Interruption.kt         즉시 중단 사유·우선순위·재개 정책
│   │   └── DrivingMonitor.kt       앱 전체가 보는 StateFlow<DrivingStatus>
│   ├── speech/               STT/TTS 경계 + 시뮬레이터
│   ├── curriculum/           코스·수업 콘텐츠 (Mock 카탈로그)
│   └── progress/             설정·진도·스트릭·랭킹 저장
│
└── feature/                  화면 단위 기능. 한 기능의 UI·ViewModel·로직을 한 폴더에 둡니다
    ├── onboarding/           마이크 권한 → 음성 테스트 → 레벨 테스트(3분) → 코스
    ├── home/                 출근·퇴근 공용 홈
    ├── lesson/               수업 화면(상태별 UI 하나)
    │   ├── engine/           수업 상태 머신 (순수 Kotlin, 단위 테스트 대상)
    │   ├── coach/            AI 코치 경계 + Mock
    │   ├── LessonSession.kt  엔진 효과 실행기(TTS/STT/AI/저장 연결)
    │   ├── LessonViewModel.kt, LessonUiState.kt, LessonScreen.kt
    ├── report/               도착 리포트
    ├── course/               내 코스(레벨·주차·주간표)
    ├── settings/
    └── vehicleconsole/       디버그 전용 차량 시뮬레이터 + adb 리시버
```

### 역할 분리

| 층 | 하는 일 | Android 의존 |
|---|---|---|
| `core/vehicle` | 원시 신호를 모읍니다(실제 또는 Mock) | 실연동 구현만 |
| `core/driving` | 신호로 상황·모드·중단 사유를 판단합니다 | 없음 |
| `feature/lesson/engine` | 수업 진행 규칙: `(상태, 이벤트) → (상태, 효과)` | 없음 |
| `feature/lesson/LessonSession` | 효과를 실행하고 결과를 이벤트로 되돌립니다 | 코루틴 |
| `*ViewModel` | 화면 상태를 만들고 입력을 넘깁니다 | lifecycle |
| `*Screen` | 그리기만 합니다 | Compose |

ViewModel에는 규칙을 두지 않았습니다. 수업 규칙은 엔진에, 운전 판단은 `core/driving`에 있어서 JVM 단위 테스트로 검증합니다.

## 화면 구조

```
[첫 실행] Onboarding ──> Home ──> Lesson ──> Report
                          │  ^        │
                          │  └────────┘ (뒤로 가기·시동 끔: 중단 지점 저장)
                          ├──> Course   (주행 중 차단)
                          └──> Settings (주행 중 차단)
```

- **Home**: 출근·퇴근이 같은 화면입니다. 다음 수업 번호가 홀수면 출근, 짝수면 퇴근 문구를 씁니다.
- **Lesson**: '말할 차례 / 듣는 중 / 잠시 멈춤 / 길게 말하기'는 따로 만든 화면이 아닙니다. `LessonUiMapper`가 엔진 상태를 보고 한 화면의 내용을 바꿉니다.
- 모든 화면은 `BoxWithConstraints`로 폭과 비율을 보고 세로(1열)와 가로(2~3열) 배치를 고릅니다. 고정 픽셀은 쓰지 않습니다.

## 운전 상황 → 학습 모드

| 상황 | 판단 근거 | 학습 모드 | 화면 |
|---|---|---|---|
| 주차 | CarUx 제한 없음 + 기어 P (또는 시동 꺼짐) | 상세 복습: 짧은 대답 + 글자 카드 | 전체 |
| 정차 | CarUx 제한 없음 + P 아님(오토홀드), 또는 제한 중 속도 < 1 | 긴 대답(시간 제한 없음) | 제한 |
| 안정 주행 | 제한 중, 아래 조건 모두 아님 | 짧은 대답(12초) | 제한 |
| 가다 서다 | 60초 안에 멈춤→출발 2회 이상 | 따라 말하기(8초) | 제한 |
| 복잡 주행 | 다음 회전·합류가 300m 이내 | 듣기만 | 제한 |
| 출발 직후 / 도착 직전 / 후진 | 출발 1분 이내 / 도착 2분 이내 / 기어 R | 침묵(QUIET) | 제한 |
| 알 수 없음 | CarUx 를 못 읽음, 또는 제한 중 속도 모름 | 안전 제한: 듣기만 | 제한 |

- CarUxRestrictions가 기본 제한입니다. 화면 조작 제한(`uiRestricted`)은 이 값만 따릅니다.
- 다른 신호는 판단을 **더 보수적인 쪽으로만** 바꿀 수 있습니다. 예를 들어 플랫폼이 제한 없음이라고 해도 속도가 5km/h를 넘으면 주행으로 봅니다.
- `SituationStabilizer`: 더 보수적인 모드로는 바로 바꾸고, 덜 보수적인 모드로는 3초 동안 유지될 때만 바꿉니다.

## 수업 상태 전환표 (`LessonEngine`)

| 현재 | 이벤트 | 다음 | 효과 |
|---|---|---|---|
| READY | Start | PROMPT (중단 중이면 PAUSED) | 오디오 포커스 요청, Speak |
| PROMPT | SpeechFinished (듣기 턴) | 다음 아이템 | SaveCheckpoint, Speak |
| PROMPT | SpeechFinished (대답 턴) | ANSWER | Listen(300ms 에코 가드) |
| ANSWER | AnswerRecognized (따라 말하기 또는 오프라인) | FEEDBACK | 기기에서 채점, Speak |
| ANSWER | AnswerRecognized (열린 대답) | THINKING | RequestFeedback |
| ANSWER | AnswerFailed (첫 번째) | PROMPT | "No rush…" 하고 다시 질문 |
| ANSWER | AnswerFailed (두 번째) | FEEDBACK | 모범 답안. 콤보 유지 |
| THINKING | CoachReplied | FEEDBACK | 콤보·단어 수·교정 기록 |
| THINKING | CoachFailed (6초 타임아웃·오류) / 네트워크 끊김 | FEEDBACK | 로컬 피드백 |
| FEEDBACK | SpeechFinished | 다음 아이템 (끝이면 CLOSING) | SaveCheckpoint |
| CLOSING | SpeechFinished | FINISHED | Finish(결과 저장) |
| 진행 중 아무 단계 | 중단 사유 발생 | PAUSED | StopAudio. 진행·콤보 유지 |
| PAUSED | 사유가 모두 풀림 + 대기 시간 지남 (Tick) | 현재 모드로 턴을 다시 짬 / QUIET | Speak("Okay, let's continue…") |
| QUIET | 모드가 SILENT 아님 | PROMPT | Speak |
| 아무 단계 | Stop (뒤로 가기·시동 끔) | STOPPED | StopAudio, SaveCheckpoint |

공통 규칙은 다음과 같습니다.
- **모드 변경은 턴 경계에서만 적용합니다.** 긴 대답 도중 신호가 바뀌어 출발해도 대답이 끝날 때까지 듣습니다(시안 문구: "출발해도 끝까지 들을게요").
- **늦게 도착한 콜백은 버립니다.** 음성 요청마다 `audioToken`이 1씩 늘어납니다. 멈춘 뒤 들어온 이전 TTS 완료, STT 결과, AI 응답은 토큰이 맞지 않아 무시됩니다.
- **TTS와 STT는 겹치지 않습니다.** 세션은 음성 작업을 한 번에 하나만 돌리고(`audioJob`), 마이크는 TTS가 끝나고 300ms 뒤에 엽니다. 그래서 STT가 코치 음성을 다시 인식하지 않습니다.

## 즉시 중단 우선순위와 재개 정책 (`Interruption`)

| 순위 | 사유 | 끝난 뒤 대기 | 자동 재개 |
|---|---|---|---|
| 1 | 차량 경고 | 3초 | 예 |
| 2 | 급제동 | 5초 | 예 |
| 3 | 전화 | 2초 | 예 |
| 4 | 오디오 포커스 영구 상실 | 0초 | 아니오 (사용자가 재생 버튼을 누르면 포커스 재요청) |
| 5 | 오디오 포커스 일시 상실 / 덕킹 | 1초 | 예 |
| 6 | 내비 음성 안내 | 1.5초 | 예 |
| 7 | 방향지시등 | 2초 | 예 |
| 8 | 사용자 일시정지 | 0초 | 아니오 |

- 여러 사유가 겹치면 화면에는 순위가 가장 높은 사유를 보여 줍니다. 재개는 모든 사유가 풀리고, 각 사유의 대기 시간이 지난 뒤에만 합니다.
- 재개하기 전에 **현재 학습 모드를 다시 확인합니다.** 멈춘 사이 출발 직후 구간이 되었다면 말하지 않고 QUIET로 갑니다. 운전 상황이 바뀌었다면 턴을 새 모드로 다시 짭니다.
- 덕킹(`LOSS_TRANSIENT_CAN_DUCK`)에도 멈춥니다. 말하기 수업은 내비 안내 위에 작은 소리로 겹치면 오히려 혼란스럽기 때문입니다.

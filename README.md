# 말차 (DriveLingo) — 차량용 AI 영어 스피킹 프로토타입

출퇴근길 10분 동안 차 안에서 하는 영어 말하기 수업입니다.

Jetpack Compose로 만든 프로토타입입니다. 기본 빌드는 일반 Android 기기·에뮬레이터에서 Mock 차량 신호와 소리 없는 음성 시뮬레이션으로 동작합니다.
빌드 옵션을 켜면 Pleos Connect(AAOS)에서 실제 차량 신호(CarUxRestrictions, 속도·기어, 방향지시등·브레이크·경고등, 오디오 포커스)와
Pleos STT/TTS 실제 음성을 씁니다. 수업은 MediaSession으로 노출되어 운전대 재생·일시정지 버튼으로도 조작할 수 있습니다.
**실연동 코드는 빌드와 단위 테스트까지만 확인했고, Pleos Connect 에뮬레이터에서는 아직 실행해 보지 않았습니다**([검증 필요](#검증-필요)).
백엔드와 AI 코치는 아직 Mock입니다.

## 실행

요구 환경: JDK 17 이상(21에서 검증), Android SDK 35.

```bash
./gradlew :app:testDebugUnitTest     # 단위 테스트 78개
./gradlew assembleDebug              # app/build/outputs/apk/debug/app-debug.apk (차량 시뮬레이터 포함)
./gradlew assembleRelease            # app/build/outputs/apk/release/app-release.apk (시뮬레이터 없음, debug 키 서명)
./gradlew installDebug               # 실행 중인 에뮬레이터·기기에 설치
```

- **VS Code**: `Cmd+Shift+B`(기본 빌드 작업 "말차: 에뮬레이터에서 실행")를 누르면 에뮬레이터 켜기 → 설치 → 실행을 한 번에 합니다.
  다른 작업(차량 모드 실행, 단위 테스트, 로그 보기)은 `Cmd+Shift+P` → `Tasks: Run Task`에서 고릅니다. 터미널에서는 `scripts/run.sh`로 실행합니다.
- `./gradlew installDebug`는 이미 켜져 있는 에뮬레이터나 기기에 **설치만** 합니다. 에뮬레이터가 없으면 `No connected devices!`로 실패합니다.
- 차량 신호는 앱 안의 차량 시뮬레이터(디버그 빌드 화면 오른쪽의 자동차 버튼)나 adb로 바꿉니다(`docs/testing.md`).
- **Pleos Connect 실연동** (미검증, `docs/pleos-sdk-status.md`):
  ```bash
  scripts/setup-pleos-emulator.sh      # Pleos Connect v2.1 AVD 설치 (라이선스 동의 필요, 약 4.7GB)
  AVD=Pleos_Connect_v2.1 scripts/run.sh -Pdrivelingo.vehicleSource=car -Pdrivelingo.voiceSource=pleos
  scripts/check-car-ux.sh              # 주차 ↔ 주행에 따라 CarUxRestrictions 가 바뀌는지 확인
  ```
  `vehicleSource=car`는 차량 신호와 오디오 포커스를, `voiceSource=pleos`는 STT/TTS를 실제로 바꿉니다. 둘은 따로 켤 수 있습니다.
  도착 시간·다음 회전(NaviHelper), 시동, 네트워크는 car 모드에서도 시뮬레이터에서 넣습니다.

## 문서

| 문서 | 내용 |
|---|---|
| [docs/architecture.md](docs/architecture.md) | 폴더 구조, 계층별 역할, 화면 구조, **운전 상황 → 학습 모드 표**, **수업 상태 전환표**, 중단 우선순위 |
| [docs/pleos-sdk-status.md](docs/pleos-sdk-status.md) | Pleos SDK 조사 결과, 신호별 출처, **Mock과 실제 구현 구분**, 교체 방법 |
| [docs/policy-review.md](docs/policy-review.md) | Music/Audio 카테고리 적합성, 자동 재생 정책, 주행 중 화면 제한, 개인정보. **미승인 항목 표시** |
| [docs/testing.md](docs/testing.md) | 단위 테스트, 앱/adb로 방향지시등·정차·주행·전화·네트워크 실패 재현, 회전·리사이즈 |

## 폴더 구조 (요약)

```
app/src/main/java/com/drivelingo/
├── app/            Application, AppContainer(Mock↔실제 교체 지점), MainActivity
├── navigation/     라우팅
├── core/           여러 기능이 같이 쓰는 것
│   ├── designsystem/  색·타이포·공용 컴포넌트
│   ├── vehicle/       차량 신호 입력 경계 (mock/, car/: CarUx·CarProperty·Pleos Vehicle SDK)
│   ├── audio/         오디오 포커스·내비 안내·전화 감지 (AudioManager)
│   ├── driving/       신호 → 상황·모드·중단 판단 (순수 Kotlin)
│   ├── speech/        STT/TTS 경계 + 시뮬레이터 (pleos/: Pleos STT/TTS)
│   ├── curriculum/    코스·수업 콘텐츠 (Mock)
│   └── progress/      설정·진도·스트릭·랭킹 저장
└── feature/        화면 단위. UI·ViewModel·로직을 한곳에
    ├── onboarding/ home/ lesson/(engine/, coach/) report/ course/ settings/
    ├── media/            MediaSession·MediaBrowserService (운전대 키, 차량 미디어 화면)
    └── vehicleconsole/   디버그 전용 차량 시뮬레이터
```

## 구현 현황

### 구현 완료 (Mock 기반, 일반 Android 에뮬레이터에서 동작 확인)
- 시안 기반 화면: 레벨 테스트, 홈(출근·퇴근 공용), 수업(상태별 UI 하나), 도착 리포트, 내 코스, 설정
- 세로/가로와 차량 와이드 화면(1920x720)에 맞춘 레이아웃. 회전해도 수업이 유지됩니다
- 최초 실행: 마이크 런타임 권한 → 음성 테스트 → 3단계 레벨 테스트(자기 평가 / 4→8→14단어 따라 말하기 / 인터뷰 2개) → 추천 레벨 → L1~L4 선택
- 수업 상태 머신: 6가지 운전 상황별 활동, 턴 경계에서만 모드 변경, 긴 대답 끊지 않기, 에코 가드
- 즉시 중단 7종 + 사용자 일시정지: 우선순위, 사유별 재개 대기, 재개 전 모드 재확인, 포커스 영구 상실 시 수동 재개
- AI 응답 지연(6초 타임아웃)·인식 실패(1회 재시도)·오프라인(기기 채점 복습 카드로 전환) 처리
- 출근은 새 표현 2개, 퇴근은 복습(느린 속도, 아침에 교정된 표현 다시 연습)
- 중단 지점 저장·이어서 하기, 진도·스트릭(차 안 탄 날 유지)·교정 표현·구간 랭킹(opt-in) 저장
- CarUxRestrictions 기준 화면 제한(주행 중 숫자·텍스트·설정 숨김)
- 차량 시뮬레이터(앱 안) + adb 브로드캐스트로 모든 신호와 오류 재현

### 실연동 구현 (빌드·단위 테스트까지 확인, 에뮬레이터 미검증)
- Pleos Connect 에뮬레이터 설치 스크립트와 CarUxRestrictions 확인 스크립트
- Pleos Vehicle SDK: 방향지시등, 브레이크 페달, 경고등(새로 켜진 경고만 5초 중단). `checkXxxCapabilities`로 지원 여부를 먼저 확인
- CarPropertyManager: 속도(런타임 권한 요청), 기어. 급제동(감속도 + 브레이크)·출발 직후 판단
- AudioManager 오디오 포커스(지연 승인·영구 상실 처리), 재생 용도로 내비 안내 감지, 오디오 모드로 전화 감지
- Pleos STT/TTS (`voiceSource=pleos`): 실제 음성 재생·인식, 중단 시 즉시 멈춤, 시간 제한, 오류 → 기존 재시도 흐름
- MediaBrowserService + MediaSession: 운전대 재생/일시정지/이전(다시 듣기)/정지, 차량 미디어 화면의 "오늘의 수업". 수업은 앱 수명의 `LessonController`가 소유

### 미구현 (Mock으로 대체, 문서화함)
- NaviHelper(도착 시간·다음 회전 거리), 시동(`IGNITION_STATE`), 네트워크(`ConnectivityManager`)
- 백엔드(Spring Boot/PostgreSQL)와 LLM 코치. 현재는 규칙 기반 `MockEnglishCoach`입니다
- 화면 없이 오래 재생하기 위한 mediaPlayback 포그라운드 서비스
- TTS 말하기 속도 조절(SDK에 API 없음)

### 검증 필요
- Pleos Connect 에뮬레이터에서 car·pleos 모드 동작 전체. 특히 **Pleos TTS 재생이 앱의 오디오 포커스를 가져가는지**(`docs/pleos-sdk-status.md` 4장)
- Music/Audio 카테고리 승인 가능 여부, "출발 시 자동 시작"(기본 꺼짐) 허용 여부, DO 선언 승인
- 운전 상황 판단 임계값(가다 서다 3회/60초, 회전 300m, 도착 2분 등)은 실제 주행 데이터로 조정해야 합니다

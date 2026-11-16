#!/bin/bash
# 에뮬레이터가 없으면 켜고, 부팅을 기다린 뒤 디버그 앱을 설치·실행한다.
# 사용: AVD=Pleos_Connect_v2.1 scripts/run.sh            (기본: Pleos 실연동 빌드)
#      scripts/run.sh -Pdrivelingo.vehicleSource=mock -Pdrivelingo.voiceSource=simulated   (일반 에뮬레이터용 Mock 대체 빌드)
# AVD 지정: AVD=U_Pixel_2_ scripts/run.sh   (기본: 첫 번째 AVD)
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
cd "$(dirname "$0")/.."

if ! "$ADB" devices | grep -qE "^emulator-|\s(device|offline)$" && ! pgrep -f "emulator.*-avd" >/dev/null; then
  # -list-avds 는 INFO 로그를 섞어 출력할 수 있어서 이름 형식인 줄만 고른다.
  AVD="${AVD:-$("$EMULATOR" -list-avds 2>/dev/null | grep -E '^[A-Za-z0-9._-]+$' | head -1)}"
  if [ -z "$AVD" ]; then
    echo "AVD 가 없습니다. Android Studio > Device Manager 에서 만들어 주세요." >&2
    exit 1
  fi
  echo "에뮬레이터 시작: $AVD"
  # -allow-host-audio: 없으면 에뮬레이터가 마이크 입력을 0으로 채워 STT(음성 인식)가 아무것도 듣지 못한다.
  nohup "$EMULATOR" -avd "$AVD" -allow-host-audio >/dev/null 2>&1 &
fi

echo "부팅 대기 중... (최대 5분)"
for _ in $(seq 1 150); do
  [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  sleep 2
done
if [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; then
  echo "에뮬레이터가 5분 안에 부팅되지 않았습니다. Android Studio Device Manager 에서 상태를 확인해 주세요." >&2
  exit 1
fi

# 호스트(Mac) 마이크를 에뮬레이터에 연결한다. -allow-host-audio 만으로는 꺼진 채 시작할 수 있고(입력이 무음),
# 에뮬레이터 창의 Extended controls > Microphone 설정과 같다. 재부팅하면 풀리므로 매번 켠다.
# macOS 마이크 권한은 에뮬레이터를 띄운 앱(터미널·VS Code)에 줘야 한다.
"$ADB" emu avd hostmicon >/dev/null 2>&1 || echo "호스트 마이크 연결 실패: 에뮬레이터 창 ⋯ > Microphone 에서 켜 주세요." >&2

echo "빌드·설치"
./gradlew installDebug "$@" --console=plain -q

echo "실행"
"$ADB" shell am start -n com.drivelingo/.app.MainActivity >/dev/null
echo "말차 실행됨"

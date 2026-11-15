#!/bin/bash
# 에뮬레이터가 없으면 켜고, 부팅을 기다린 뒤 디버그 앱을 설치·실행한다.
# 사용: scripts/run.sh [gradle 옵션...]   예) scripts/run.sh -Pdrivelingo.vehicleSource=car
#      Pleos 실연동: AVD=Pleos_Connect_v2.1 scripts/run.sh -Pdrivelingo.vehicleSource=car -Pdrivelingo.voiceSource=pleos
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
  nohup "$EMULATOR" -avd "$AVD" >/dev/null 2>&1 &
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

echo "빌드·설치"
./gradlew installDebug "$@" --console=plain -q

echo "실행"
"$ADB" shell am start -n com.drivelingo/.app.MainActivity >/dev/null
echo "말차 실행됨"

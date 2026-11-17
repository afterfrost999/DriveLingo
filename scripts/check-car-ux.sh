#!/bin/bash
# Pleos Connect 에뮬레이터(AAOS)에서 CarUxRestrictions 가 주차 ↔ 주행에 따라 실제로 바뀌는지 확인한다.
#
# 앞 단계: car 모드 앱이 설치·실행돼 있어야 한다.
#   AVD=Pleos_Connect_v2.1 scripts/run.sh -Pdrivelingo.vehicleSource=car
#
# 기어·속도를 바꿔 주차 → 정차 → 주행 → 주차를 만들고, 각 단계에서
#   1) 플랫폼 상태(dumpsys car_service: 주행 상태·UX 제한)
#   2) 앱이 받은 값(logcat CarUxRestrictions·CarProperty 태그)
# 을 보여 준다.
#
# 값을 바꾸는 방법
# - Pleos Connect 이미지(user 빌드)는 'cmd car_service inject-vhal-event' 를 막는다.
#   그래서 Pleos Connect Virtual Console 의 HTTP API(에뮬레이터 8088 포트)로 기어·속도를 넣는다.
#   브라우저에서 http://127.0.0.1:8088/ 을 열어 같은 값을 손으로 바꿔도 결과가 같아야 한다.
# - userdebug/eng 이미지(일반 AAOS 에뮬레이터)에서는 VHAL 주입을 쓴다.
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
CONSOLE="http://127.0.0.1:8088"

if ! "$ADB" shell pm has-feature android.hardware.type.automotive 2>/dev/null | grep -q true; then
  echo "연결된 기기가 Automotive(AAOS) 가 아닙니다. Pleos Connect 에뮬레이터를 켜 주세요." >&2
  exit 1
fi

if [ "$("$ADB" shell getprop ro.build.type | tr -d '\r')" = "user" ]; then
  MODE=console
  "$ADB" forward tcp:8088 tcp:8088 >/dev/null
  if ! curl -fsS "$CONSOLE/vehicle/state" >/dev/null; then
    echo "Pleos Connect Virtual Console($CONSOLE)에 연결할 수 없습니다. 에뮬레이터 v2.1 이상인지 확인해 주세요." >&2
    exit 1
  fi
  # 콘솔은 브라우저 세션이 살아 있을 때만 입력을 받는다. 확인하는 동안 세션 신호를 보낸다.
  ( while true; do curl -fsS -X POST "$CONSOLE/browser/session?client=check-car-ux" >/dev/null 2>&1 || true; sleep 1; done ) &
  HEARTBEAT=$!; disown "$HEARTBEAT"
  trap 'kill $HEARTBEAT 2>/dev/null; curl -fsS -X POST "$CONSOLE/browser/session?client=check-car-ux&closing=1" >/dev/null 2>&1 || true' EXIT
  sleep 1
else
  MODE=vhal
fi
echo "값 입력 방식: $MODE"

# gear: Park / Drive, speed: km/h
set_vehicle() {
  local gear="$1" kmh="$2"
  if [ "$MODE" = console ]; then
    # 기어를 먼저 바꾸고 속도를 넣는다(P 에서는 콘솔이 속도를 0으로 되돌린다).
    if [ "$gear" = Park ]; then
      curl -fsS -X PUT "$CONSOLE/vehicle/controls?speed=0&accelerator=0&brake=100" >/dev/null
      curl -fsS -X PUT "$CONSOLE/vehicle/gear?gear=Park" >/dev/null
    else
      curl -fsS -X PUT "$CONSOLE/vehicle/gear?gear=$gear" >/dev/null
      curl -fsS -X PUT "$CONSOLE/vehicle/controls?speed=$kmh&accelerator=0&brake=0" >/dev/null
    fi
  else
    # AAOS 표준 VHAL 속성 ID: GEAR_SELECTION(PARK=4, DRIVE=8), PERF_VEHICLE_SPEED(m/s), PARKING_BRAKE_ON
    local vg=4 pb=1
    [ "$gear" = Drive ] && vg=8 pb=0
    "$ADB" shell cmd car_service inject-vhal-event 0x11200402 0 $pb >/dev/null
    "$ADB" shell cmd car_service inject-vhal-event 0x11400400 0 $vg >/dev/null
    "$ADB" shell cmd car_service inject-vhal-event 0x11600207 0 "$(awk "BEGIN{print $kmh/3.6}")" >/dev/null
  fi
}

show() {
  echo "---- $1"
  sleep 3
  "$ADB" shell dumpsys car_service --services CarDrivingStateService 2>/dev/null |
    grep -E "Current Driving State" | sed 's/^ */  플랫폼 /; s/$/  (0=PARKED 1=IDLING 2=MOVING)/' || true
  # 현재 값은 "Display id: 0 UXR: DO: true UxR: 80" 줄에 있다(아래쪽은 상태별 설정 목록).
  "$ADB" shell dumpsys car_service --services CarUxRestrictionsManagerService 2>/dev/null |
    grep -m1 -E "UXR: DO:" | sed 's/^ */  플랫폼 /' || true
  echo "  앱 수신 로그(바뀐 때만 찍힘):"
  { "$ADB" logcat -d -s CarUxRestrictions:I | grep -E "initial|changed" || echo "(변화 없음)"; } | tail -2 | sed 's/^/    /'
  "$ADB" logcat -c
}

"$ADB" logcat -c
set_vehicle Park 0
show "주차 (P, 0km/h) → 기대: PARKED, DO false"

set_vehicle Drive 0
show "정차 (D, 0km/h) → 기대: IDLING, DO true (Pleos v2.1 설정)"

set_vehicle Drive 60
show "주행 (D, 60km/h) → 기대: DO true. Pleos v2.1 은 정차·주행 모두 NO_VIDEO|NO_SETUP(80)이라 정차에서 바뀌지 않음"

set_vehicle Park 0
show "다시 주차 → 기대: PARKED, DO false"

echo
echo "앱 화면도 함께 확인하세요: 주행 중에는 숫자·텍스트·설정이 숨겨지고, 디버그 콘솔의 'CarUx' 값이 바뀌어야 합니다."

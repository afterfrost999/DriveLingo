#!/bin/bash
# Pleos Connect 에뮬레이터(AAOS)에서 CarUxRestrictions 가 주차 ↔ 주행에 따라 실제로 바뀌는지 확인한다.
#
# 앞 단계: car 모드 앱이 설치·실행돼 있어야 한다.
#   AVD=Pleos_Connect_v2.1 scripts/run.sh -Pdrivelingo.vehicleSource=car
#
# VHAL 값을 직접 넣어 주차 → 주행 → 주차를 만들고, 각 단계에서
#   1) 플랫폼 상태(dumpsys car_service: 주행 상태·UX 제한)
#   2) 앱이 받은 값(logcat CarUxRestrictions 태그)
# 을 보여 준다. Pleos Connect Virtual Console 로 속도·기어를 바꿔도 같은 결과가 나와야 한다.
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"

# AAOS 표준 VHAL 속성 ID (VehiclePropertyIds)
PERF_VEHICLE_SPEED=0x11600207   # float, m/s
GEAR_SELECTION=0x11400400       # int, VehicleGear (PARK=4, DRIVE=8)
PARKING_BRAKE_ON=0x11200402     # boolean

if ! "$ADB" shell pm has-feature android.hardware.type.automotive 2>/dev/null | grep -q true; then
  echo "연결된 기기가 Automotive(AAOS) 가 아닙니다. Pleos Connect 에뮬레이터를 켜 주세요." >&2
  exit 1
fi

inject() { "$ADB" shell cmd car_service inject-vhal-event "$1" 0 "$2" >/dev/null; }

show() {
  echo "---- $1"
  sleep 2
  "$ADB" shell dumpsys car_service --services CarDrivingStateService 2>/dev/null |
    grep -iE "current driving state|drivingstate" | head -3 || true
  "$ADB" shell dumpsys car_service --services CarUxRestrictionsManagerService 2>/dev/null |
    grep -iE "requires DO|active restrictions|Current Restrictions" | head -4 || true
  echo "  앱 수신 로그:"
  "$ADB" logcat -d -s CarUxRestrictions:I | tail -2 | sed 's/^/    /'
}

"$ADB" logcat -c
inject $PARKING_BRAKE_ON 1; inject $GEAR_SELECTION 4; inject $PERF_VEHICLE_SPEED 0
show "주차 (P, 0km/h, 주차 브레이크) → 기대: 제한 없음 (DO 불필요)"

inject $PARKING_BRAKE_ON 0; inject $GEAR_SELECTION 8; inject $PERF_VEHICLE_SPEED 0
show "정차 (D, 0km/h) → 기대: 플랫폼 설정에 따라 IDLING. 앱은 정차(STOPPED)로 판단"

inject $PERF_VEHICLE_SPEED 16.7
show "주행 (D, 60km/h) → 기대: requiresDistractionOptimization=true, FULLY_RESTRICTED(511)"

inject $PERF_VEHICLE_SPEED 0; inject $GEAR_SELECTION 4; inject $PARKING_BRAKE_ON 1
show "다시 주차 → 기대: 제한 없음"

echo
echo "앱 화면도 함께 확인하세요: 주행 중에는 숫자·텍스트·설정이 숨겨지고, 디버그 콘솔의 'CarUx' 값이 바뀌어야 합니다."

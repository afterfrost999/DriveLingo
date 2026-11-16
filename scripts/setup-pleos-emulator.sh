#!/bin/bash
# Pleos Connect Emulator(v2.1, Android 14 Automotive) 시스템 이미지를 내려받고 AVD 를 만든다.
#
# Android Studio 의 SDK Update Sites 에 아래 URL 을 추가하는 공식 절차와 같은 일을 터미널에서 한다.
#   https://document.pleos.ai → Getting started → Pleos Connect Emulator 설치 및 실행
#
# 사용: scripts/setup-pleos-emulator.sh            (AVD 이름 기본값: Pleos_Connect_v2.1)
#       AVD=MyPleos scripts/setup-pleos-emulator.sh
#
# 주의
# - 이미지는 약 4.7GB 입니다. 42dot SDK 라이선스에 동의해야 설치합니다(스크립트가 전문을 보여 주고 묻습니다).
# - 공식 권장 사양은 외장 GPU(RTX 3060 Laptop 6GB 이상 등)입니다. 사양이 낮으면 부팅이 느리거나 화면이 깨질 수 있습니다.
# - VHAL 을 쓰려면 반드시 cold boot 로 켜야 합니다. 이 스크립트가 AVD 설정에 cold boot 를 강제합니다.
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
AVD="${AVD:-Pleos_Connect_v2.1}"
REPO="https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x"
CACHE="${PLEOS_CACHE:-$HOME/.cache/pleos-emulator}"

case "$(uname -m)" in
  arm64|aarch64) MANIFEST_URL="$REPO/crp-arm64-sys-img.xml" ;;
  *) MANIFEST_URL="$REPO/crp-sys-img.xml" ;;
esac

SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"
if [ ! -x "$SDKMANAGER" ] || [ ! -x "$AVDMANAGER" ]; then
  echo "Android SDK Command-line Tools 가 없습니다: $SDK/cmdline-tools/latest" >&2
  echo "Android Studio > Settings > Android SDK > SDK Tools 에서 'Android SDK Command-line Tools' 를 설치해 주세요." >&2
  exit 1
fi

mkdir -p "$CACHE"
MANIFEST="$CACHE/sys-img.xml"
echo "이미지 정보 내려받기: $MANIFEST_URL"
curl -fsSL "$MANIFEST_URL" -o "$MANIFEST"

# XML 에서 필요한 값만 뽑는다(패키지 경로, 압축 파일 URL·크기·SHA-1).
read_xml() { python3 -I - "$MANIFEST" "$1" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
pkg = root.find('remotePackage')
what = sys.argv[2]
if what == 'license':
    print(root.find('license').text.strip())
elif what == 'path':
    print(pkg.get('path'))
else:
    print(pkg.find('archives/archive/complete/' + what).text.strip())
PY
}
PKG_PATH="$(read_xml path)"          # 예) system-images;android-34;pleos_car_emulator;arm64-v8a
ZIP_URL="$(read_xml url)"
ZIP_SHA1="$(read_xml checksum)"
ZIP_SIZE="$(read_xml size)"
IMG_DIR="$SDK/$(echo "$PKG_PATH" | tr ';' '/')"

if [ -f "$IMG_DIR/system.img" ] || [ -f "$IMG_DIR/system.img.qcow2" ]; then
  echo "시스템 이미지가 이미 있습니다: $IMG_DIR"
else
  echo
  echo "================ Pleos Connect SDK License (42dot) ================"
  read_xml license | ${PAGER:-less}
  echo "===================================================================="
  read -r -p "위 라이선스에 동의하고 설치할까요? (y/N) " answer
  [ "$answer" = "y" ] || [ "$answer" = "Y" ] || { echo "설치를 취소했습니다."; exit 1; }

  echo "에뮬레이터 패키지 확인"
  yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null || true
  "$SDKMANAGER" --sdk_root="$SDK" "emulator" "platform-tools" >/dev/null

  ZIP="$CACHE/$(basename "$ZIP_URL")"
  echo "시스템 이미지 내려받기 ($((ZIP_SIZE / 1024 / 1024)) MB, 중단돼도 다시 실행하면 이어 받습니다)"
  # 서버가 응답 없이 멈추는 일이 있어, 60초 동안 1KB/s 미만이면 끊고 받은 데서부터 다시 받는다.
  for attempt in $(seq 1 20); do
    curl -fL -C - --speed-limit 1024 --speed-time 60 "$ZIP_URL" -o "$ZIP" && break
    [ "$attempt" = 20 ] && { echo "내려받기에 계속 실패했습니다. 다시 실행하면 이어 받습니다." >&2; exit 1; }
    echo "내려받기가 끊겼습니다. 이어 받기 재시도 ($attempt/20)"
    sleep 5
  done
  echo "SHA-1 확인"
  echo "$ZIP_SHA1  $ZIP" | shasum -a 1 -c -

  echo "압축 풀기: $IMG_DIR"
  TMP="$CACHE/unzip"
  rm -rf "$TMP" && mkdir -p "$TMP"
  unzip -q "$ZIP" -d "$TMP"
  # 압축 안의 최상위 폴더 이름(arm64-v8a, x86_64 등)과 관계없이 system.img 가 있는 폴더를 쓴다.
  SRC="$(dirname "$(find "$TMP" -maxdepth 3 -name 'system.img*' | head -1)")"
  mkdir -p "$(dirname "$IMG_DIR")"
  rm -rf "$IMG_DIR"
  mv "$SRC" "$IMG_DIR"
  rm -rf "$TMP"

  # Android Studio 로 설치하면 생기는 package.xml 이 없으면 avdmanager 가 이미지를 찾지 못한다.
  if [ ! -f "$IMG_DIR/package.xml" ] && [ ! -f "$IMG_DIR/source.properties" ]; then
    python3 -I - "$MANIFEST" "$IMG_DIR/package.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
src = ET.parse(sys.argv[1]).getroot().find('remotePackage')
d = src.find('type-details')
def t(p): return d.find(p).text
xml = f'''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:repository xmlns:ns2="http://schemas.android.com/repository/android/common/02" xmlns:ns5="http://schemas.android.com/sdk/android/repo/sys-img2/03" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
<localPackage path="{src.get('path')}" obsolete="false">
<type-details xsi:type="ns5:sysImgDetailsType"><api-level>{t('api-level')}</api-level><tag><id>{t('tag/id')}</id><display>{t('tag/display')}</display></tag><vendor><id>{t('vendor/id')}</id><display>{t('vendor/display')}</display></vendor><abi>{t('abi')}</abi></type-details>
<revision><major>{src.find('revision/major').text}</major></revision>
<display-name>{src.find('display-name').text}</display-name>
</localPackage>
</ns2:repository>
'''
open(sys.argv[2], 'w').write(xml)
PY
  fi
fi

if "$SDK/emulator/emulator" -list-avds 2>/dev/null | grep -qx "$AVD"; then
  echo "AVD 가 이미 있습니다: $AVD"
else
  echo "AVD 만들기: $AVD ($PKG_PATH)"
  echo no | "$AVDMANAGER" create avd -n "$AVD" -k "$PKG_PATH" -d automotive_1024p_landscape --force >/dev/null
fi

# 공식 가이드 설정: Cold boot(Quick boot 면 VHAL 사용 불가), RAM 4GB, 하드웨어 그래픽, 마이크 입력(STT 테스트).
CONFIG="$HOME/.android/avd/$AVD.avd/config.ini"
set_config() {
  if grep -q "^$1=" "$CONFIG"; then sed -i '' "s|^$1=.*|$1=$2|" "$CONFIG"; else echo "$1=$2" >> "$CONFIG"; fi
}
set_config fastboot.forceColdBoot yes
set_config fastboot.forceFastBoot no
set_config hw.ramSize 4096
set_config hw.gpu.enabled yes
set_config hw.gpu.mode host
set_config hw.audioInput yes
set_config hw.keyboard yes

echo
echo "완료. 실행:"
echo "  AVD=$AVD scripts/run.sh -Pdrivelingo.vehicleSource=car"
echo "주행 규제 확인:"
echo "  scripts/check-car-ux.sh"

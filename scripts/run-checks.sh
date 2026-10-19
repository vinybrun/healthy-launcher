#!/usr/bin/env bash
# Layers: unit tests, then a device-owner UI and blocking walk.
# Exits non-zero on the first failure. No skip switch.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

export JAVA_HOME="${JAVA_HOME:-/home/viny/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

if [[ ! -x "$ANDROID_HOME/platform-tools/adb" ]]; then
  echo "FAILED: adb missing under ANDROID_HOME=$ANDROID_HOME" >&2
  exit 1
fi

echo "== unit =="
./gradlew testDebugUnitTest assembleDebug assembleRelease

echo "== emulator =="
adb start-server >/dev/null
if ! adb devices | awk 'NR>1 && $2=="device" {found=1} END {exit !found}'; then
  echo "FAILED: no adb device. Boot an Android 15 emulator with no accounts first." >&2
  exit 1
fi

boot="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
if [[ "$boot" != "1" ]]; then
  echo "FAILED: device has not finished booting" >&2
  exit 1
fi

apk="app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$apk" ]] || { echo "FAILED: missing $apk" >&2; exit 1; }
adb install -r -d "$apk"

policy="$(adb shell dumpsys device_policy | tr -d '\r')"
if [[ "$policy" != *"com.healthylauncher/.block.AdminReceiver"* ]]; then
  adb shell dpm set-device-owner com.healthylauncher/.block.AdminReceiver
fi

adb shell cmd appops set com.healthylauncher SCHEDULE_EXACT_ALARM allow
adb shell cmd appops set com.healthylauncher android:get_usage_stats allow
adb shell pm grant com.healthylauncher android.permission.POST_NOTIFICATIONS
adb shell pm grant com.healthylauncher android.permission.WRITE_SECURE_SETTINGS
adb shell settings put secure enabled_accessibility_services com.healthylauncher/com.healthylauncher.block.GuardService
adb shell settings put secure accessibility_enabled 1
listeners="$(adb shell settings get secure enabled_notification_listeners | tr -d '\r')"
ours="com.healthylauncher/com.healthylauncher.block.NotifBlocker"
if [[ "$listeners" != *"NotifBlocker"* ]]; then
  if [[ -z "$listeners" || "$listeners" == "null" ]]; then
    adb shell settings put secure enabled_notification_listeners "$ours"
  else
    adb shell settings put secure enabled_notification_listeners "${listeners}:$ours"
  fi
fi
adb shell cmd role add-role-holder android.app.role.HOME com.healthylauncher
adb shell settings put global animator_duration_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global window_animation_scale 0

echo "== device walk =="
./gradlew connectedDebugAndroidTest

dest="${1:-/tmp/healthy-film}"
mkdir -p "$dest"
adb pull /sdcard/Android/data/com.healthylauncher/files/film "$dest" || {
  echo "FAILED: film frames were not written" >&2
  exit 1
}
echo "OK frames in $dest"

#!/usr/bin/env bash
# round.sh <serial> <apk> <prefix>: install, start Nouride, wait until it answers, screenshot Home, Connect, More and the
# Versions screen in portrait.
set -u
S=$1; APK=$2; P=$3
timeout 200 adb -s "$S" install -r "$APK" | tail -1
adb -s "$S" shell 'input keyevent KEYCODE_WAKEUP; settings put system accelerometer_rotation 0; settings put system user_rotation 0'
adb -s "$S" shell 'am start -n tech.nouverse.nouride.home.dev/tech.nouverse.nouride.android.ui.StatusActivity --ez start true' >/dev/null
sleep 4
# Start may open HyperOS's battery page first.
adb -s "$S" shell dumpsys activity activities | grep -q "topResumedActivity=.*securitycenter" && adb -s "$S" shell input keyevent KEYCODE_BACK
for i in $(seq 1 40); do
  adb -s "$S" shell 'toybox wget -q -O - http://127.0.0.1:18264/health 2>/dev/null' | grep -q ok && break; sleep 3
done
echo "answering after $i tries"; sleep 4
"$(dirname "$0")/shots.sh" "$S" "$P" home connect more
adb -s "$S" shell 'am start -n tech.nouverse.nouride.home.dev/tech.nouverse.nouride.android.ui.VersionsActivity' >/dev/null; sleep 4
adb -s "$S" exec-out screencap -p > "$P-versions.png"
adb -s "$S" shell input keyevent KEYCODE_BACK

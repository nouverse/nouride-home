#!/usr/bin/env bash
# versions-shot.sh <serial> <out.png> [logs.png]: Nouride off, More, tap "Versions and updates ›" (and "Technical Logs ›").
S=$1; OUT=$2; LOGS=${3:-}
tap_text() {
  adb -s "$S" shell uiautomator dump /sdcard/wl.xml >/dev/null 2>&1
  b=$(adb -s "$S" exec-out cat /sdcard/wl.xml | python3 -I -c '
import sys,re
x=sys.stdin.read(); m=re.search(r"text=\"" + re.escape(sys.argv[1]) + r"\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", x)
print((int(m[1])+int(m[3]))//2, (int(m[2])+int(m[4]))//2) if m else print("")' "$1")
  [ -n "$b" ] && adb -s "$S" shell input tap $b
}
adb -s "$S" shell 'am start -n tech.nouverse.nouride.home.dev/tech.nouverse.nouride.android.ui.StatusActivity --ez stop true --es tab more' >/dev/null 2>&1
sleep 6
adb -s "$S" shell 'am start -n tech.nouverse.nouride.home.dev/tech.nouverse.nouride.android.ui.StatusActivity --es tab more' >/dev/null 2>&1
sleep 2
adb -s "$S" shell input swipe 540 1600 540 500 300; sleep 1.5   # More's rows are under the fold; a touch also ends key mode
tap_text "Versions and updates ›"; sleep 4
adb -s "$S" exec-out screencap -p > "$OUT"
adb -s "$S" shell input tap 60 200 >/dev/null; sleep 1   # the back arrow, by touch (a key would leave touch mode)
if [ -n "$LOGS" ]; then adb -s "$S" shell input swipe 540 1600 540 500 300; sleep 1.5; tap_text "Technical Logs ›"; sleep 3; adb -s "$S" exec-out screencap -p > "$LOGS"; adb -s "$S" shell input tap 60 200; fi
adb -s "$S" shell rm -f /sdcard/wl.xml

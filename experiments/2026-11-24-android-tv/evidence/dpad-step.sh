#!/usr/bin/env bash
# dpad-step.sh <name> <key>...: send D-pad keys to the TV, save a screenshot <name>.png (kept local, never
# committed) and print the focused node and the activity in front. TV=<address:port> picks the device.
T=${TV:?the TV, address:port}; S=$(dirname "$0"); name=$1; shift
for key in "$@"; do adb -s $T shell input keyevent "KEYCODE_$key"; sleep 0.7; done
sleep 0.8
adb -s $T exec-out screencap -p > "$S/$name.png"
adb -s $T shell 'uiautomator dump /sdcard/ui.xml >/dev/null; cat /sdcard/ui.xml' | grep -o '<node [^>]*focused="true"[^>]*' | grep -o 'text="[^"]*"\|content-desc="[^"]*"\|class="[^"]*"\|bounds="[^"]*"' | tr '\n' ' '; echo
adb -s $T shell 'dumpsys activity activities | grep topResumedActivity' | sed 's/.*u0 //'

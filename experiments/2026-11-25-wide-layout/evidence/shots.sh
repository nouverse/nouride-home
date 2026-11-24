#!/usr/bin/env bash
# shots.sh <serial> <prefix> <tab>...: open each tab of Home Dev and save a screenshot as <prefix>-<tab>.png
set -u
S=$1; P=$2; shift 2
for t in "$@"; do
  adb -s "$S" shell "am start -n tech.nouverse.nouride.home.dev/tech.nouverse.nouride.android.ui.StatusActivity --es tab $t" >/dev/null 2>&1
  sleep 2.5
  adb -s "$S" exec-out screencap -p > "$P-$t.png"
done

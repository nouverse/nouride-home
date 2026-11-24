#!/usr/bin/env bash
# long-poll.sh <out> <count>: every 60 s, the TV's power state over adb (TV) and Nouride Home Dev's /health from
# another machine on the LAN (TV_HOST); one line each, time in UTC.
out=$1; n=$2
for i in $(seq 1 "$n"); do
  w=$(timeout 10 adb -s "${TV:?set TV to address:port}" shell 'dumpsys power | grep -m1 mWakefulness=' 2>/dev/null | tr -d ' \r')
  body=$(curl -s -m 8 -w ' HTTP%{http_code}' "http://${TV_HOST:?set TV_HOST}:18264/health")
  echo "$(date -u +%H:%M:%S) ${w:-adb=none} $(echo "$body" | grep -o 'HTTP[0-9]*') uptime=$(echo "$body" | grep -o '"uptime":[0-9]*' | cut -d: -f2)" >> "$out"
  sleep 60
done

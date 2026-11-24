#!/usr/bin/env bash
# standby-poll.sh <out> [count]: polls Nouride Home Dev's /health on the TV (TV_HOST) from another machine on
# the LAN every 30 s; one line each: time (UTC), HTTP code, Nouride's uptime in seconds.
out=$1; n=${2:-20}
for i in $(seq 1 "$n"); do
  body=$(curl -s -m 8 -w ' HTTP%{http_code}' "http://${TV_HOST:?set TV_HOST}:18264/health")
  echo "$(date +%H:%M:%S) $(echo "$body" | grep -o 'HTTP[0-9]*') uptime=$(echo "$body" | grep -o '"uptime":[0-9]*' | cut -d: -f2)" >> "$out"
  sleep 30
done

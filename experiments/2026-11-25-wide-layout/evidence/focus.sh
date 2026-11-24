#!/usr/bin/env bash
# focus.sh <serial> <key>...: press each key and print what holds focus in Home Dev's window (from uiautomator).
S=$1; shift
for k in "$@"; do
  adb -s "$S" shell input keyevent "$k"; sleep 0.8
  adb -s "$S" shell uiautomator dump /sdcard/wl.xml >/dev/null 2>&1
  adb -s "$S" exec-out cat /sdcard/wl.xml | python3 -I -c '
import sys,re
x=sys.stdin.read()
m=re.search(r"<node [^>]*focused=\"true\"[^>]*>",x)
if not m: print(sys.argv[1], "-> (none)"); sys.exit()
n=m.group(0)
g=lambda a: (re.search(a+r"=\"([^\"]*)\"",n) or [None,""])[1]
txt=g("text") or g("content-desc")
if not txt:
  # the first text inside the focused node
  rest=x[m.end():m.end()+3000]; t=re.search(r"text=\"([^\"]+)\"",rest); txt=t.group(1) if t else ""
print(sys.argv[1], "->", g("class").split(".")[-1], repr(txt[:40]), g("bounds"))
' "$k"
done
adb -s "$S" shell rm -f /sdcard/wl.xml

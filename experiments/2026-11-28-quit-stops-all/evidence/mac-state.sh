# What the Mac's Nouride Home Dev and its Nouride are, in one look (no secrets): the plists, launchd's job, the port.
U=$(id -u)
DEV=tech.nouverse.nouride.home.dev
DATA="$HOME/Library/Application Support/$DEV"
echo "== $(date -u +%FT%TZ)"
echo "-- app processes"; pgrep -fl 'Nouride Home' | sed "s|$HOME|~|g"
echo "-- ~/Library/LaunchAgents (nouride)"; ls ~/Library/LaunchAgents | grep -i nouride || echo "(none)"
echo "-- app folder plists"; ls "$DATA" | grep plist || echo "(none)"
echo "-- launchd: $DEV.nouride"; launchctl print gui/$U/$DEV.nouride 2>&1 | grep -E '^\s*(state|pid|path|last exit code) =|Could not find' | sed "s|$HOME|~|g"
echo "-- launchd: $DEV.login"; launchctl print gui/$U/$DEV.login 2>&1 | grep -E '^\s*(state|pid|path|last exit code) =|Could not find|arguments' -A0 | sed "s|$HOME|~|g"
echo "-- port 18264"; lsof -nP -iTCP:18264 -sTCP:LISTEN 2>/dev/null | awk 'NR>1{print $1, $2}' | sort -u || true
echo "-- /health"; curl -s -m 3 -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18264/health || echo "no answer"
echo "-- never touched: release app label and Nouride's own label"
launchctl print gui/$U/tech.nouverse.nouride.home.nouride 2>&1 | grep -E '^\s*(state|pid) =|Could not find'
launchctl print gui/$U/tech.nouverse.nouride 2>&1 | grep -E '^\s*(state|pid) =|Could not find'
ls -l "/Applications/Nouride Home.app/Contents/MacOS/nouride-home" 2>/dev/null | awk '{print $6,$7,$8}'

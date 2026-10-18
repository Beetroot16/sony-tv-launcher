#!/usr/bin/env bash
# Connects to the TV over Wi-Fi ADB, waits for the user to approve the prompt on screen,
# saves the address to state/tv, and prints what the TV is.
#   scripts/connect.sh 192.168.1.42
source "$(dirname "$0")/lib.sh"
need_adb

ip="${1:-}"
if [[ -z "$ip" && -f "$STATE/tv" ]]; then ip="$(cat "$STATE/tv")"; fi
[[ -n "$ip" ]] || die "usage: $0 <tv-ip>"
[[ "$ip" == *:* ]] || ip="$ip:5555"
export ANDROID_SERIAL="$ip"

adb connect "$ip" || true
state="$(adb get-state 2>/dev/null || true)"
if [[ "$state" != "device" ]]; then
  # A fresh adb server sometimes reports "No route to host" on the first try.
  adb kill-server >/dev/null 2>&1 || true
  adb connect "$ip" || true
fi

echo "If the TV shows 'Allow USB debugging?', tick 'Always allow from this computer' and press OK." >&2
for _ in $(seq 1 60); do
  state="$(adb get-state 2>/dev/null || true)"
  [[ "$state" == "device" ]] && break
  sleep 2
done
[[ "$state" == "device" ]] || die "not authorized after 2 minutes (state: ${state:-offline}). Is ADB/USB debugging on in Developer options? Then re-run."

echo "$ip" > "$STATE/tv"
echo "connected: $ip"
echo "maker:     $(adb shell getprop ro.product.manufacturer | tr -d '\r')"
echo "model:     $(adb shell getprop ro.product.model | tr -d '\r')"
echo "android:   $(adb shell getprop ro.build.version.release | tr -d '\r')"
echo "home:      $(current_home)"

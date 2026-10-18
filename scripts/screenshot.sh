#!/usr/bin/env bash
# Saves what the TV is showing right now and prints the file path.
#   scripts/screenshot.sh [out.png]
source "$(dirname "$0")/lib.sh"
ensure_connected

out="${1:-$STATE/screen-$(date +%H%M%S).png}"
adb exec-out screencap -p > "$out"
# Downscale 4K captures so they're quick to view (macOS sips; skipped elsewhere).
command -v sips >/dev/null 2>&1 && sips -Z 1280 "$out" >/dev/null 2>&1 || true
echo "$out"

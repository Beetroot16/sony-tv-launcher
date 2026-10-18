#!/usr/bin/env bash
# Undoes everything: re-enables every package these scripts disabled and restores the stock Home.
# The launcher app stays installed (remove it with: adb uninstall dev.dhruv.tvhome).
source "$(dirname "$0")/lib.sh"
ensure_connected

"$(dirname "$0")/tv-home.sh" stock

if [[ -f "$STATE/disabled.txt" ]]; then
  while IFS= read -r pkg; do
    [[ -n "$pkg" ]] && adb shell pm enable "$pkg" </dev/null >/dev/null && echo "enabled: $pkg"
  done < "$STATE/disabled.txt"
  mv "$STATE/disabled.txt" "$STATE/disabled.restored-$(date +%Y%m%d-%H%M%S).txt"
fi
echo "Restored."

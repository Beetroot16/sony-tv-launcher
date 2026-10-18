#!/usr/bin/env bash
# Read-only snapshot of the TV: model, home screen, and every package (changes nothing).
# Writes state/survey-<timestamp>.txt and prints its path.
source "$(dirname "$0")/lib.sh"
ensure_connected

out="$STATE/survey-$(date +%Y%m%d-%H%M%S).txt"
{
  echo "# maker:   $(adb shell getprop ro.product.manufacturer | tr -d '\r')"
  echo "# model:   $(adb shell getprop ro.product.model | tr -d '\r')"
  echo "# device:  $(adb shell getprop ro.product.device | tr -d '\r')"
  echo "# android: $(adb shell getprop ro.build.version.release | tr -d '\r')"
  echo "# home:    $(current_home)"
  echo
  echo "## home screen candidates"
  adb shell cmd package query-activities --brief -a android.intent.action.MAIN \
    -c android.intent.category.HOME | tr -d '\r'
  echo
  echo "## user-installed apps"
  adb shell pm list packages -3 | sed 's/^package://' | tr -d '\r' | sort
  echo
  echo "## enabled system packages"
  adb shell pm list packages -s -e | sed 's/^package://' | tr -d '\r' | sort
  echo
  echo "## already disabled"
  adb shell pm list packages -d | sed 's/^package://' | tr -d '\r' | sort
} > "$out"
echo "$out"

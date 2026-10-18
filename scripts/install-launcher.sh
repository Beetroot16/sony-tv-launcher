#!/usr/bin/env bash
# Installs (or updates) the Apple TV-style launcher. Doesn't make it the Home screen yet:
# run scripts/tv-home.sh custom for that.
#   scripts/install-launcher.sh               # latest release from GitHub
#   scripts/install-launcher.sh my-build.apk  # a local APK
source "$(dirname "$0")/lib.sh"
ensure_connected

apk="${1:-}"
if [[ -z "$apk" ]]; then
  apk="$STATE/tv-home.apk"
  echo "Downloading $APK_URL"
  curl -fL --progress-bar -o "$apk" "$APK_URL" || die "download failed"
fi
[[ -f "$apk" ]] || die "no such file: $apk"

out="$(adb install -r "$apk" 2>&1 || true)"
echo "$out"
if echo "$out" | grep -q 'INSTALL_FAILED_UPDATE_INCOMPATIBLE'; then
  # Built with a different signing key: replace it (this resets the saved tile order).
  echo "Signature differs from the installed copy; reinstalling."
  adb uninstall "$LAUNCHER_PKG" >/dev/null
  adb install "$apk"
elif ! echo "$out" | grep -q '^Success'; then
  die "install failed"
fi
echo "Installed $(adb shell dumpsys package "$LAUNCHER_PKG" | grep -m1 versionName | tr -d ' \r')."
echo "Next: scripts/tv-home.sh custom"

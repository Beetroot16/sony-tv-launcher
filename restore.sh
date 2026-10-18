#!/bin/zsh
# Re-enables everything disabled on the Sony KD-55X80K and restores the stock Google TV home screen.
export ANDROID_SERIAL=${ANDROID_SERIAL:-192.168.0.163:5555}
adb connect "$ANDROID_SERIAL" >/dev/null
for p in $(cat "${0:A:h}/packages.txt") com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith; do
  adb shell pm enable "$p"
done
adb shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity

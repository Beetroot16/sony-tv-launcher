#!/bin/zsh
# Switch the Sony TV's Home screen between the custom launcher and stock Google TV.
#   ./tv-home.sh custom   -> custom launcher (Google TV home + its ads disabled)
#   ./tv-home.sh google   -> stock Google TV home screen back
export ANDROID_SERIAL=${ANDROID_SERIAL:-192.168.0.163:5555}
adb connect "$ANDROID_SERIAL" >/dev/null

case "$1" in
  custom)
    adb shell pm enable dev.dhruv.tvhome >/dev/null
    adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
    adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
    adb shell cmd package set-home-activity dev.dhruv.tvhome/.MainActivity
    ;;
  google)
    adb shell pm enable com.google.android.tungsten.setupwraith
    adb shell pm enable com.google.android.apps.tv.launcherx
    adb shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity
    ;;
  *)
    echo "usage: $0 custom|google"; exit 1
    ;;
esac
adb shell input keyevent KEYCODE_HOME

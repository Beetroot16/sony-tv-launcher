#!/usr/bin/env bash
# Switches the Home screen between the custom launcher and the TV's stock one.
#   scripts/tv-home.sh custom   # launcher becomes Home; stock home apps that grab the Home button are disabled
#   scripts/tv-home.sh stock    # put the stock home screen back
source "$(dirname "$0")/lib.sh"
ensure_connected

home_candidates() {
  adb shell cmd package query-activities --brief -a android.intent.action.MAIN \
    -c android.intent.category.HOME | tr -d '\r' | awk '/\//{print $1}'
}

case "${1:-}" in
  custom)
    adb shell pm path "$LAUNCHER_PKG" >/dev/null 2>&1 || die "launcher not installed. Run scripts/install-launcher.sh first."
    adb shell pm enable "$LAUNCHER_PKG" >/dev/null
    # Remember the stock home once, so 'stock' can return to it.
    stock="$(current_home)"
    if [[ ! -s "$STATE/stock-home" && "$stock" == */* && "$stock" != "$LAUNCHER_PKG/"* ]]; then
      echo "$stock" > "$STATE/stock-home"
    fi
    # Make sure the launcher can actually start before taking anything away.
    adb shell am start -n "$LAUNCHER_HOME" >/dev/null 2>&1 || die "launcher failed to start"
    sleep 2
    adb shell cmd package set-home-activity "$LAUNCHER_HOME" >/dev/null
    press_home
    # Google TV's launcher (and its "recovery" home) win the Home button over the default
    # setting, so disable every other home app that outranks us, one at a time.
    for _ in 1 2 3 4; do
      [[ "$(resumed_activity)" == "$LAUNCHER_PKG/"* ]] && break
      for c in $(home_candidates); do
        pkg="${c%%/*}"
        [[ "$pkg" == "$LAUNCHER_PKG" || "$c" == *FallbackHome* ]] && continue
        adb shell pm disable-user --user 0 "$pkg" >/dev/null
        remember "$STATE/home-disabled.txt" "$pkg"
        echo "disabled stock home: $pkg"
        break
      done
      adb shell cmd package set-home-activity "$LAUNCHER_HOME" >/dev/null
      press_home
    done
    now="$(resumed_activity)"
    if [[ "$now" == "$LAUNCHER_PKG/"* ]]; then
      echo "Home is now the custom launcher."
    else
      die "Home still opens '$now'. Run scripts/tv-home.sh stock to undo."
    fi
    ;;
  stock|google)
    if [[ -f "$STATE/home-disabled.txt" ]]; then
      while IFS= read -r pkg; do [[ -n "$pkg" ]] && adb shell pm enable "$pkg" </dev/null >/dev/null && echo "enabled: $pkg"; done < "$STATE/home-disabled.txt"
      rm -f "$STATE/home-disabled.txt"
    fi
    if [[ -s "$STATE/stock-home" ]]; then
      adb shell cmd package set-home-activity "$(cat "$STATE/stock-home")" >/dev/null
    fi
    press_home
    echo "Home is now: $(resumed_activity)"
    ;;
  *)
    die "usage: $0 custom|stock"
    ;;
esac

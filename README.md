# Sony TV debloat + launcher

Tools for the Sony Bravia KD-55X80K (Google TV, Android 12), driven over Wi‑Fi ADB.

- `packages.txt`: bloat/tracking packages disabled with `pm disable-user --user 0`
- `restore.sh`: re-enables them plus the stock Google TV launcher (`launcherx`) and `setupwraith`
- `launcher/`: custom Apple TV–style home screen (Android TV app, in progress)

Connect: `adb connect 192.168.0.163:5555`

# Home — Apple TV–style launcher for Android TV / Google TV

A small (plain Views + Kotlin, no AndroidX) home screen for the Sony Bravia KD-55X80K
(Google TV, Android 12), driven entirely by the D-pad remote.

- **Top shelf hero** (top ~55% of the screen): the focused tile's art shown large, a blurred,
  darkened copy as the full-screen background, and the name in big type. It crossfades (~250 ms)
  as focus moves. Continue Watching items show their poster, title and "Resume".
- **Continue watching** row (from the system Watch Next list; hidden if empty or not readable).
- **Top row**: large rounded tiles using each app's TV banner (icon on a colour card if the app
  has none). The focused tile scales to 1.1x with a soft glow and shows its name underneath.
  Defaults: Netflix, YouTube, Prime Video, JioHotstar, Sony LIV, ZEE5, Apple TV, Crunchyroll,
  MUBI, MX Player (installed ones only), then **Inputs** (`com.sony.dtv.tvx`, if present) and
  **Settings** (opens the quick settings panel).
- **All apps** row: every other leanback app, A–Z.
- **Long-press OK** on an app: Move left / Move right / Remove from (or Add to) top row / App info.
  The order is saved in SharedPreferences.
- **Quick settings panel**: press **MENU** on the remote or select the gear tile. Wi‑Fi,
  Bluetooth, Sound output, Picture, Sleep timer, Inputs, All settings (each hidden if the TV
  doesn't provide it), plus launcher options: focus sounds on/off and reset top row.
  Back closes it.
- **Focus sounds**: a quiet tick on focus moves and a click on OK (toggle in the panel).
- Back on the home screen does nothing; Home while already home jumps to the first tile.
- Refreshes when apps are installed/removed/updated and on every return to the home screen.
- Permissions: `INTERNET` is used only to download Continue Watching poster images.
  No analytics, no ads, no other network use.

HOME activity: **`dev.dhruv.tvhome/.MainActivity`**

## Install / activate (from the Mac)

```sh
adb connect 192.168.0.163:5555 && adb install -r tv-home-v0.1.0.apk && adb shell cmd package set-home-activity dev.dhruv.tvhome/.MainActivity
```

Then press Home on the remote. To go back to Projectivy:
`adb shell cmd package set-home-activity <projectivy package>/<activity>`
(see `adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME`).

### Continue watching permission

The Watch Next list belongs to the system TV provider. Try granting read access:

```sh
adb shell pm grant dev.dhruv.tvhome com.android.providers.tv.permission.READ_EPG_DATA
```

If that fails with "not a changeable permission type" (or similar), Android 12 doesn't let a
sideloaded app hold it, and the row simply stays hidden. Even when granted, some TV providers
only return programs published by the calling app, so the row may still be empty — that's
expected, nothing else is affected.

## Signing

The keystore and its passwords are **never committed** (`*.jks`, `keystore.properties` are
git-ignored). A release built with a different key can't be installed over an existing one:
if you ever re-sign with a new keystore, uninstall first
(`adb uninstall dev.dhruv.tvhome`), then install and run `set-home-activity` again.
Uninstalling resets the saved top-row order.

`v0.1.0` was signed with a key generated in the cloud build session (certificate SHA-256
`6da18894…6628be`, `CN=TV Home, O=dhruv`). That keystore was handed over outside git. Keep it
to sign later versions so `adb install -r` upgrades in place. You can also add it as the
repository secrets below so CI signs releases for you.

## Build

Requirements: JDK 17+, Android SDK with platform 34 (`ANDROID_HOME` set or `local.properties`
with `sdk.dir=...`).

```sh
cd launcher
./gradlew lint assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew lint assembleRelease    # signed if keystore.properties exists, else app-release-unsigned.apk
```

`launcher/keystore.properties` (git-ignored):

```properties
storeFile=/absolute/path/tv-home.jks
storePassword=...
keyAlias=tvhome
keyPassword=...
```

Create a key: `keytool -genkeypair -keystore tv-home.jks -alias tvhome -keyalg RSA -keysize 3072 -validity 10000`.

Sign an unsigned APK by hand (build-tools on PATH):

```sh
zipalign -p -f 4 app-release-unsigned.apk aligned.apk
apksigner sign --ks tv-home.jks --ks-key-alias tvhome --out tv-home.apk aligned.apk
apksigner verify --print-certs tv-home.apk
```

## CI

- `.github/workflows/launcher.yml`: on pushes touching `launcher/**`, runs `lint assembleDebug`
  plus `assembleRelease`, and uploads `tv-home-debug`, `tv-home-release-unsigned` and the lint
  report as workflow artifacts.
- `.github/workflows/launcher-release.yml`: when a release tagged `launcher-v*` is published
  (or run manually with a tag), runs `lint assembleRelease` and attaches the APK to the release.
  If the repository secrets `LAUNCHER_KEYSTORE_B64` (base64 of the .jks),
  `LAUNCHER_KEYSTORE_PASSWORD`, `LAUNCHER_KEY_ALIAS`, `LAUNCHER_KEY_PASSWORD` exist it attaches a
  signed `tv-home-vX.Y.Z.apk`; otherwise it only keeps the unsigned APK as a workflow artifact
  (sign it by hand as above and `gh release upload` it).

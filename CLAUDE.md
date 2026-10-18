# CLAUDE.md: de-ad an Android TV / Google TV and give it a clean home screen

You're helping someone remove the ads and bloat from their smart TV and, if they want, replace
the home screen with the Apple TV–style launcher in `launcher/`. Everything happens over Wi‑Fi
ADB from this computer. Nothing is rooted, nothing is uninstalled, and every change can be
undone with `scripts/restore.sh`.

When the user says something like "set up my TV", "remove the ads from my TV" or "do the
steps", follow the playbook below in order. Keep them posted with one short line per step.

## Ground rules

- **Disable, never uninstall.** Only use `pm disable-user --user 0` (through `scripts/debloat.sh`)
  and `pm enable` to undo. Never `pm uninstall`, never `pm clear`, never root or bootloader tricks.
- **Ask before changing the TV.** Reading is free. Before disabling anything, show the user the
  exact package list with a one-line reason for each, and wait for a yes.
- **Never disable what keeps the TV working:** inputs/HDMI, tuner, picture/sound settings, the
  remote, Play Store/Play services, the keyboard, system UI, networking. `scripts/debloat.sh`
  refuses the common ones (`PROTECTED` regex), but use judgement for brand packages too. If you
  aren't sure what a package does, leave it.
- **Keep a way back.** Every change goes through the scripts so it's recorded in `state/`
  (git-ignored). Don't run raw `pm disable-user` commands yourself.
- **Check with your own eyes.** After each TV change, run `scripts/screenshot.sh` and look at the
  image before saying it worked.
- **The user does the physical steps** (remote, on-screen prompts). Tell them exactly which
  menu to open, then wait.
- Some steps happen on screens you can't control (Google sign-in, app stores). Don't type
  passwords or account details for the user.

## Requirements

- The computer and TV on the same network.
- `adb`. Check with `adb version`. If it's missing, install it after asking:
  macOS `brew install --cask android-platform-tools`, Debian/Ubuntu `sudo apt install adb`,
  Windows: use WSL, or install Google's platform-tools and run the same commands.
- The scripts are bash and work on macOS and Linux.

## Playbook

### 1. Find the TV

```bash
scripts/find-tv.sh
```

It lists devices on the local /24 with `adb:open|closed` and their Cast name (usually brand +
model, e.g. `SONY KD-55X80K`). If several show up, ask which one. If nothing shows up, ask the
user for the TV's IP (Settings → Network → About on most TVs) and make sure the TV is on.

### 2. Developer mode (user, with the remote)

If the TV shows `adb:closed`, walk the user through this. Wording varies by brand:

1. **Settings → System → About** (on some TVs **Settings → Device Preferences → About**).
2. Press OK on **Android TV OS build** (or **Build**) about 7 times, until "You are now a developer".
3. Back to **Settings → System → Developer options** (or **Device Preferences → Developer options**).
4. Turn on **USB debugging**. Some TVs (Sony, for example) call it **ADB debugging**. If there's a
   separate **Network debugging / Wireless debugging** switch, turn that on too.
   **No USB cable is needed.** It works over Wi‑Fi.

### 3. Connect

```bash
scripts/connect.sh <tv-ip>
```

Tell the user to watch the TV: an **"Allow USB debugging?"** prompt appears. They should tick
**Always allow from this computer** and press OK. The script waits up to 2 minutes, saves the
address to `state/tv`, and prints maker, model, Android version and the current home screen.

If you get `failed to authenticate` or `unauthorized`, the prompt is waiting on the TV. If you
get `No route to host`, the script retries once. Otherwise check the IP and that debugging is on.

### 4. Survey (read-only)

```bash
scripts/survey.sh
```

This writes `state/survey-<time>.txt` with the model, the home-screen apps, user-installed apps,
enabled system packages and already-disabled packages. Read the whole file. Then:

- Work out **where the ads come from.** On Google TV it's mostly the stock home screen
  (`com.google.android.apps.tv.launcherx`): sponsored banner and "Top picks". On older Android
  TV it's `com.google.android.tvlauncher` or `leanbacklauncher`. Some brands also ship their own
  promo, recommendation and ACR ("what's on screen") tracking apps.
- Build the candidate list:
  - start from `lists/google-tv-common.txt`;
  - add the brand list from `lists/` if one matches (e.g. `lists/sony-google-tv.txt`);
  - for other brands, pick candidates from the survey yourself. Typical safe ones are promo,
    demo/retail mode, "recommendation", "select", ACR/Samba/Alphonso tracking, usage analytics,
    and preinstalled shopping/news apps. Unsure means skip.
- Also point out user-installed apps the user may not want, but only disable those if they say so.

### 5. Confirm and debloat

Show the user a table (package → what it is → why it's safe to disable), plus a short "leaving
alone" note for the important brand packages. After they say yes:

```bash
scripts/debloat.sh -y lists/google-tv-common.txt lists/<brand-list>.txt <extra.packages> ...
```

It skips protected, missing and already-disabled packages and records the rest in
`state/disabled.txt`. If you built a list for a new brand/model, save it as
`lists/<brand>-<platform>.txt` in the same commented format and suggest the user contribute it
back (a PR to this repo).

### 6. Home screen: pick one

Disabling bloat doesn't remove the ads on the stock **home screen itself**. Offer two options:

- **A. Keep the stock home, turn on "Apps only mode"** (Google TV only, no hacks):
  Settings → Accounts & sign-in → the profile → **Apps only mode**. This removes
  recommendations and sponsored rows. The user flips it themselves. Done.
- **B. Replace the home screen with the custom launcher** (the Apple TV look, no ads at all):

  ```bash
  scripts/install-launcher.sh          # downloads the latest release APK and installs it
  scripts/tv-home.sh custom            # makes it Home; disables stock home apps that hijack the Home button
  scripts/screenshot.sh                # check it
  ```

  `tv-home.sh custom` first checks that the launcher starts, then sets it as Home and presses
  Home. If another home app still takes over (Google TV's `launcherx` and its recovery screen
  `setupwraith` both do), it disables those one at a time, records them in
  `state/home-disabled.txt`, and stops as soon as Home opens the launcher. Tell the user that
  the stock quick-settings panel goes away with the stock home. They use the launcher's
  Settings button, the remote's settings/⚙️ button, or the **Menu** key instead.

### 7. Verify and hand over

Take a screenshot. Then tell the user:

- what was disabled (from `state/disabled.txt` and `state/home-disabled.txt`);
- how to use the launcher: long-press OK on a tile to move, pin or unpin it; Menu opens quick
  settings; Continue Watching only appears once apps publish "watch next" data;
- the three commands they can run any time:

  ```bash
  scripts/tv-home.sh custom    # custom launcher as Home
  scripts/tv-home.sh stock     # stock home screen back
  scripts/restore.sh           # undo everything
  ```

## Troubleshooting

- **The Home button still opens the stock screen:** run `scripts/tv-home.sh custom` again. It
  disables whatever else claims Home. If it errors, `scripts/tv-home.sh stock` puts things back.
- **Home opens Settings or a blank "recovery" screen:** a stock home app is half-disabled. Run
  `scripts/tv-home.sh custom` (or `stock`) to settle it.
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`:** the installed launcher was signed with another key.
  `install-launcher.sh` handles it by reinstalling, which resets the tile order.
- **The TV's IP changed:** `scripts/find-tv.sh`, then `scripts/connect.sh <new-ip>`.
- **After a factory reset** everything is back to stock, and `state/` is stale. Delete `state/`
  and start from step 1.
- **Something broke after debloating:** `scripts/restore.sh`, then re-run debloat with a
  smaller list to find the culprit.

## Working on the launcher

The source is in `launcher/` (Java, plain Views, no AndroidX; see `launcher/README.md`).
You don't need an Android SDK locally: push to `main` and the `launcher` GitHub Actions
workflow builds `tv-home-debug` (download with `gh run download <run-id> -n tv-home-debug`).
Debug and release builds share `launcher/debug.keystore`, so `adb install -r` updates in place.
To ship, bump `versionCode`/`versionName` in `launcher/app/build.gradle.kts`, then
`gh release create launcher-vX.Y.Z --generate-notes`. The release workflow attaches
`tv-home.apk`, which is what `scripts/install-launcher.sh` downloads.

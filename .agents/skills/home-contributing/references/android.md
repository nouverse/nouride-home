# Nouride Home for Android

The Android app, in `apps/android` (Kotlin, Gradle, no third-party Android libraries). The rules both apps share are in
`../SKILL.md`.

## How it works

1. The APK carries `libproot.so` and `libproot-loader.so` (built by `scripts/build-proot.sh`, static aarch64) under
   both `lib/arm64-v8a` and `lib/armeabi-v7a` (the same bytes: `Arm32Copy` in `build.gradle.kts` copies them, and
   `checkApkNatives<Variant>`, run by assemble, refuses an APK whose two folders differ or that carries other native
   code), and the Debian arm64 layer `assets/rootfs.tgz` (fetched by `scripts/build-rootfs.sh`). Before anything is
   started, `server/DeviceSupport` runs proot's version check once: a kernel that refuses it (ENOEXEC) cannot run
   Nouride, and the app says so (`Phase.UNSUPPORTED`) instead of unpacking and installing.
2. On the first start, `install/` unpacks the rootfs into the app's data directory, then runs the official installer
   inside Debian (`Nouride.INSTALL`: the packages the minimal image lacks, then `https://get.nouride.com/install.sh`).
3. `server/ServerService` is a foreground service of type `specialUse`. It holds a partial wake lock and a Wi-Fi lock,
   and `Supervisor` runs `nouride start` under proot (`ProotCommand`), restarting it with backoff when it exits on its
   own.
4. `HealthPoller` asks Nouride's `/health` on the phone every minute; `ServerState` holds the phase and the last answer
   for the screens and the notification.
5. `power/` asks for the battery exemption and opens the phone's own battery settings. `boot/` starts the service on
   boot.
6. `update/` is the app's own updates, from this repository's GitHub Releases: `UpdateFeed` reads `releases/latest`
   (pure, tested), `Updates` checks it when the app opens (every 6 h at most, at once after the app changed version) and from the service twice a day,
   downloads the APK against its sha256 and hands it to `PackageInstaller`. `UpdateReceiver` hears the installer: the
   screen asking the person, then the result, and the new version starts Nouride again on success. `ui/UpdateCard`
   is Home's card, the one place the update is offered, Android's permission to install included (its sentence and
   button are `ui/UpdateLooks`, pure and tested); it waits while the first start sets Nouride up. `ui/AppUpdater` is the
   download and install both it and the Versions screen use. Nouride's own update is `update/NourideUpdates`: the
   dashboard's routes (`/api/control/update`, `/update/download`, `/update/progress`, `/update/install`) on the
   loopback, signed in with the install's control token (`NourideUpdate.TOKEN_PATH`, what `nouride` commands use), so it
   needs no dashboard account. Nouride swaps its binary and exits, and `Supervisor` starts the new one.

7. `ui/` is everything a person sees: `StatusActivity` holds the three tabs along the bottom (`HomeTab`, `ConnectTab`,
   `MoreTab`, `BottomBar`, the rules in `Tabs`), `DashboardActivity` the dashboard in a WebView, `SupportActivity`
   Technical Logs, `AddressActivity` the address of another computer (its refusals and its one check in `AddressForm`,
   pure and tested), which hands the address back to `StatusActivity`. Connect's switch is `server/NetworkAccess` (the
   `.env`'s `HOST=0.0.0.0` or `127.0.0.1`, which `nouride start` reads and which wins over `config.toml`), then
   `ServerService.ACTION_RESTART`: the daemon stopped cleanly and the `Supervisor` starting it again at once
   (`restartNext`), not counted as a crash in its backoff.
   `QrCode` draws the Connect tab's code without a library; `QrCodeTest` checks it module by module
   against segno's output. `Appearance` is the More tab's Light or Dark: every activity starts from a context in it
   (`attachBaseContext`), as Android's per-app setting only exists from Android 12.

Nouride listens on its default port, 18254 (`Nouride.PORT`), and the Router edition's LLM gateway on 18256
(`Nouride.GATEWAY_PORT`). The debug build moves both (below).

8. `server/Place` is the person's answer to where Nouride lives: `PHONE`, or `REMOTE` at an address
   (`server/RemoteHost`, which is pure and tested: what it takes and what it refuses, https only because the WebView may
   not speak cleartext to anything but the loopback). It is unset until the first screen is answered (a phone with Nouride on disk counts as
   `PHONE` without asking: `Place.answer`), it is changed on More either way, and while it is
   `REMOTE` nothing of the phone's own server runs: `BootReceiver` and `UpdateReceiver` do not start it, Home, Connect,
   More and Versions show that computer instead, and `DashboardActivity` loads `RemoteHost.dashboard` with `LinkPolicy`
   told which origin is Nouride's, its own login page the way in. Home asks that
   computer's `/health` when it opens (`server/RemoteReply`, pure and tested) to say whether it answers. The install this phone already has stays on disk
   untouched, so answering `PHONE` again picks it up as it was.

## Constraints, and why

- **targetSdk 28.** From 29 on, Android refuses to run files from the app's data directory (W^X), and proot runs every
  Debian program from there. Termux targets 28 for the same reason. So the app is sideloaded, never on Google Play.
- **A 64-bit kernel, any userspace.** Nouride ships no 32-bit ARM build, and proot and Debian are aarch64. Most
  Google TV and Android TV boxes run a 32-bit userspace on a 64-bit kernel: their PackageManager installs only an APK
  with an `armeabi-v7a` folder (it never looks at the ELF inside), they have no `/system/bin/linker64`, yet an app
  there may exec static aarch64 programs (fork, ptrace, mmap, sockets all pass the app's seccomp filter). So proot is
  static, built against API 29 (`NDK_API` in `assets.lock`: a static bionic executable for an older API aborts with
  "executable's TLS segment is underaligned"), and packaged in both folders. Never decide the architecture from
  Java's `os.arch` or `uname` in the app's process: a 32-bit app on a 64-bit kernel reads `armv8l`. Measured on a
  Google TV Streamer, `experiments/2026-11-24-android-tv/`.
- **The official installer, never a bundled Nouride.** The phone gets the same release as every other install, and
  `nouride update` works as it does elsewhere.
- **Pinned inputs.** `scripts/assets.lock` holds the sha256 of the proot and talloc archives and the Debian layer's
  digest. Changing one is one commit with the lock and the reason.
- **No third-party Android libraries.** The APK is 48.5 MiB already, nearly all of it Debian.
- **The debug build is a second app.** `applicationIdSuffix ".dev"` in `apps/android/app/build.gradle.kts`: "Nouride Home Dev",
  `tech.nouverse.nouride.home.dev`, a yellow icon (`apps/android/app/src/debug/res`), Nouride on 18264, its gateway on 18266 (written
  into Nouride's `config.toml` before the wizard, as Nouride has no environment variable for it), and the link scheme
  `nouridehome-dev`. Anything that names the app's ports, its id or its link reads `BuildConfig`, never a literal, or
  the developer's copy reaches the person's Nouride.
- **HyperOS (Xiaomi) can hold broadcasts back from an app not allowed to autostart**, and which ones differs: on the
  owner's Redmi Note 12 (HyperOS 2.0.202 global) `MY_PACKAGE_REPLACED` was refused (logcat: "process is not permitted
  to auto start"), while after a restart `BOOT_COMPLETED` reached both the release and the Dev build with Autostart off,
  about 40 s after the first unlock, and Nouride answered 9 s later (2026-11-08,
  `experiments/2026-11-08-restart-on-hyperos/`). The installer's own report to the app always arrives, which is why
  `UpdateReceiver`, not `BootReceiver`, starts Nouride after an update. Autostart's switch is in Security's list
  (`PowerSettings.AUTOSTART_LIST`), not on the app's page in Settings. To test a restart: `adb reboot`, unlock once
  without opening the app, and watch `/health` from another machine (wireless debugging is off after a restart, and
  the phone's address may change); then `adb logcat -d -b all | grep -i nouride` shows `Start proc ... for broadcast
  {...BootReceiver}`.

## Build and test

```sh
scripts/setup-toolchain.sh ~/android-toolchain   # once, no root
. ~/android-toolchain/env.sh
scripts/build-proot.sh
scripts/build-rootfs.sh
(cd apps/android && ./gradlew assembleDebug testDebugUnitTest)
```

Unit tests run on the JVM (`apps/android/app/src/test/`): backoff, the health answer, proot's arguments, the tar reader, process
handling; and in `spec/` the cases both apps share (`spec/fixtures/`, which Gradle hands the tests as `nouride.spec`).
A change carries a test that fails without it: break the change, run `./gradlew testDebugUnitTest` in `apps/android`,
see it red, put it back, and say so in the commit.

## Releases

Test an update before a release with two signed builds and a feed of your own: `scripts/bump-version 0.0.10`, build
the release with `NOURIDE_HOME_UPDATE_FEED=http://127.0.0.1:18299/latest.json`, then again after
`scripts/bump-version 0.0.11` (put VERSION and what it wrote back afterwards: never commit a test version); sign both with the release key on the owner's machine (`apksigner sign --ks-pass env:...`, never a password on the
command line), serve a `latest.json` shaped like GitHub's (`tag_name`, `draft`, `prerelease`, `assets` with
`name` and `browser_download_url`) with the APK and its `.sha256` next to it, `adb reverse tcp:18299 tcp:18299`,
install the first and let it find the second. Plain HTTP is accepted from `127.0.0.1` only. A wrong sha256 must end
in "The update didn't download"; after the update, Nouride must be running again without the app being opened.

A release is a GitHub release `vX.Y.Z` published on `main`, X.Y.Z being what VERSION says (the workflow refuses
another); `.github/workflows/release.yml` runs on that (never on a push), builds, tests, signs and attaches
`nouride-home-X.Y.Z.apk` to it. The version code comes from the version (major × 1 000 000 + minor ×
1 000 + patch), so each release must be higher than the last. The workflow refuses an APK not signed with the key
in `scripts/release-cert.sha256`: a phone refuses an update signed with another key. Never commit a keystore. Try a
change to the workflow with a manual run (a dry run) before tagging.

## On an Android TV

What the TV changes: the launcher (`LEANBACK_LAUNCHER` and the banner, `res/drawable-xhdpi/banner.png`, drawn by
`scripts/make-icons.py`), a remote's D-pad (`ui/FocusRing`, on every screen), the words (`res/values-television`,
checked by `TelevisionWordsTest`), a wired network (`SupportText.lanAddressOf`), and no battery exemption to ask for
(`PowerSettings.keepRunning`: Android TV's settings answer that request with an empty screen).

- The Google TV Streamer the owner's family watches runs the Dev build. Before taking its screen, check
  `adb shell dumpsys activity activities | grep topResumedActivity`; leave it on what was in front.
- Wireless ADB's connect port changes when it is switched on again: probe 30000-49999 with an ADB CNXN, the port that
  answers `STLS` is it.
- `adb install` of a build new to the TV shows Play Protect's "unsafe app blocked" (targetSdk 28): someone, or the
  D-pad over adb, opens "More details" and picks "Install anyway". With the TV in its screensaver the install is
  rejected unseen: wake it first (`input keyevent KEYCODE_WAKEUP`). An update of the installed build asks nothing.
- Walk a screen with `input keyevent DPAD_UP/DOWN/LEFT/RIGHT/CENTER/BACK` and read what has focus from
  `uiautomator dump` (`focused="true"`).
- `ps -A -o PID,PPID,USER,ARGS` shows `nouride start`; `run-as <id> kill -TERM <pid>` checks the supervisor.
- Google TV's "Your apps" row draws the app's round icon; the banner is for launchers that show banners.

## On a real phone

What a unit test cannot reach (the service staying alive, Doze, a brand's battery manager, the first install) is
checked on a phone, and the change says which phone, which Android, and what was seen.

- Install: `adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk`. Over wireless ADB, Play Protect asks on the
  screen; someone has to tap it. HyperOS also asks "Install via USB" for a package new to the phone, not for an
  update of one it has.
- The status screen takes extras for adb (the debug build's id is `tech.nouverse.nouride.home.dev`, a release's
  `tech.nouverse.nouride.home`; the classes keep the package `tech.nouverse.nouride.android`):
  `adb shell am start -n tech.nouverse.nouride.home.dev/tech.nouverse.nouride.android.ui.StatusActivity --ez start true`
  (also `stop`, `dashboard`), and `--es tab connect` (or `home`, `more`) opens a tab. `run-as` works on the debug
  build only.
- Never put a real password, bot token or personal id in a test, a fixture, a commit or a log you keep.


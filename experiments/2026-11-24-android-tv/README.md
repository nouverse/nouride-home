# Nouride Home on a Google TV Streamer, and the same build on a Redmi Note 12

Run on 2026-11-24, on branch `feat/android-tv` (PR #17), with the debug build, Nouride Home Dev
(`tech.nouverse.nouride.home.dev`).

## The question

Can Nouride Home install and run Nouride on an Android TV box whose apps are 32-bit but whose kernel is 64-bit, and is
the app usable there with a remote? And does the same build still work on a 64-bit phone?

## The devices

| | Google TV Streamer (kirkwood) | Redmi Note 12 (topaz) |
| --- | --- | --- |
| Android | Android TV 14 (SDK 34), Google TV launcher | Android 15, HyperOS 2.0 |
| ABIs (`ro.product.cpu.abilist`) | `armeabi-v7a,armeabi` only, `zygote32`, no `/system/bin/linker64` | `arm64-v8a,armeabi-v7a,armeabi` |
| Kernel | 64-bit GKI 5.15; a 32-bit process reads `uname -m` as `armv8l` | 64-bit |
| RAM | 4 GB | 6 GB |
| ABI the app was installed as | `armeabi-v7a` | `arm64-v8a` |

`evidence/device.txt` and `evidence/package.txt` (the TV), `evidence/note12.txt` (the phone).

## What was found before the change (by hand, the same day)

A hand-built APK with aarch64 ELF files named `lib*.so` under `lib/armeabi-v7a/` installed on the Streamer:
PackageManager picks the ABI folder and does not look at the ELF inside. From the app's own process (seccomp filter on,
`untrusted_app_27`), a static aarch64 executable ran: fork, ptrace syscall stops, execve of another 64-bit binary, mmap
RWX, a TCP listen. Home's proot as it was (a dynamic PIE needing `/system/bin/linker64`) could not start there. Built
static with the API 24 toolchain, it aborted with "executable's TLS segment is underaligned"; with API 29 it ran.

## What the branch does

- proot and its loader are static aarch64 executables built against API 29 (`scripts/build-proot.sh`), the same bytes
  under `lib/arm64-v8a` and `lib/armeabi-v7a` (Gradle copies them; `checkApkNatives<Variant>` refuses an APK whose two
  folders differ).
- A device whose kernel refuses an aarch64 executable (ENOEXEC) is told it cannot run Nouride (`DeviceSupport`).
- The TV launcher, a ring where the D-pad is, "this TV" in the words, an address on a network cable, and no battery
  exemption asked for on a TV.

## On the Streamer

| Check | Result |
| --- | --- |
| Install of the new build | Play Protect blocked it ("unsafe app", built for an older Android: targetSdk 28). With the TV in its screensaver the install was rejected with nobody to answer; with the TV awake, "More details", then "Install anyway", over the D-pad. Installed as `armeabi-v7a`. |
| proot from the app's user | `run-as ... ./libproot.so --version` printed proot's banner and exited 0 |
| The app's own first start | "Run it on this phone" pressed with the D-pad: Debian unpacked in 16 s (5442 entries), the official installer ran for 1 min 42 s and exited 0, Nouride 0.8.46 (router) ready 3 s later (`evidence/service.log`, `evidence/install.log`) |
| `/health` from another machine on the LAN | `ok`, 0.8.46, about 83 MB RSS (`evidence/health.json`) |
| Update over the existing install (twice, `adb install -r`) | No dialog. Nouride stopped with the old app and was running again by itself 2 to 15 s later (`MY_PACKAGE_REPLACED`) |
| Supervisor | SIGTERM to `nouride start` at 21:52:38: exited 0, started again at :40, `/health` answered at :42 |
| Launcher | Listed under `LEANBACK_LAUNCHER`; "Nouride Home Dev" in Google TV's "Your apps" row (Google TV draws the round icon there, not the banner) and opened from it |
| D-pad, Home | Open Nouride ringed; down to the tab bar; Home, Connect, More reached and opened |
| D-pad, More | This TV / Another computer, Allow (before the battery fix), Appearance's three choices, Turn off Nouride: each reached and ringed. Going up from the bar can land on a choice lower in the page than expected (Android's own nearest-view search), never on nothing |
| D-pad, Connect | The address pill reached; the QR code and the address shown with the TV's Wi-Fi address |
| Words | "This TV", "Nouride runs here, on this TV.", "Select Open Nouride on Home to come back." |
| Battery exemption | Android TV's settings answer `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `com.android.tv.settings/.EmptyStubActivity`: Allow did nothing. With the fix, the TV shows no reminder, no More dot and no card |
| Dashboard with the D-pad | The WebView's own spatial navigation moves between the page's fields and links; focus lands on the first field. A text field opens the TV keyboard; Back closes it. Up from the top field scrolls the page first, then reaches "Back to Nouride Home" |
| Back rule in the dashboard | The remote's Back on the first page asks "Go back to Nouride Home?"; Stay keeps the page; "Back to Nouride Home" asks at once; Go back returns to Home |
| Notification | There is no shade on Android TV: nothing the app needs is only in its notification. The foreground service runs (`isForeground=true`) |

The dashboard of this Nouride had never been past `/welcome` (it asks for a new password); it was not finished, as that
would leave a password nobody holds. Deeper dashboard pages were not walked on the TV.

### Asleep, and with another app in front

Not measured long enough to say how Nouride fares in a TV's standby. The TV was put to sleep at 21:48 local and
`/health` answered from the LAN every 30 s, but a remote's key woke it 2.5 minutes later: far too short for Android's
Doze, which starts after the screen has been off for a while. From then on the family used the TV (its screensaver at
22:13, YouTube in front from about 22:29), and every `/health`, 51 of 51 that evening, answered: Nouride keeps
running while another app plays (`evidence/standby-evening.log`). A poll of the TV's power state and `/health` every 2
minutes for 12 hours was left running on the dev box to catch the TV's own night in standby; it is not part of this
record.

## On the Redmi Note 12

Home Dev from this branch installed over the Home Dev already there (`adb install -r`, no dialog), as `arm64-v8a`, with
the same proot (sha256 `d68e7a34...`, the static build). Start: Nouride 0.8.41 (the version this install already had)
was ready at 22:05:13 and `/health` answered `ok`. The phone's words are unchanged ("this phone", the battery line on
Home), and Start opened HyperOS's battery page as before.

Two exits came first (`daemon exited with 1`): Nouride refused to start because its lock file named PID 2128, which a
restart of the phone had given to another process; once that PID was gone it took the lock over. That is Nouride's
lock check, not this app's change.

## What it means

- A 32-bit-userspace TV box with a 64-bit kernel runs Nouride from Nouride Home with no change to Nouride and none to
  Debian: only proot had to be static and packaged under the 32-bit ABI folder.
- On a TV the app is usable with a remote alone, the dashboard included.
- A device with a 32-bit kernel was not available; the refusal is tested by unit tests only.

## Evidence

- `device.txt`, `package.txt`: the Streamer and how the app was installed there.
- `service.log`, `install.log`: the app's events and the installer's output on the Streamer (the address the installer
  printed removed).
- `health.json`: Nouride's `/health` from the LAN.
- `note12.txt`: the Note 12's run.
- `dpad-step.sh`: one D-pad step with a screenshot (kept local) and the focused view; `standby-poll.sh`,
  `long-poll.sh`: `/health` (and the TV's power state) every 30 or 60 s; `standby-evening.log`: what they recorded.

Screenshots of the TV and the phone were looked at and kept off the repository.

## How to remove Home Dev from the Streamer

`adb uninstall tech.nouverse.nouride.home.dev`, or Settings, Apps, Nouride Home Dev, Uninstall. That removes its
Debian and its Nouride with it.

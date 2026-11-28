# Quit Nouride stops Nouride, and Nouride runs only while the app shows it (desktop)

## The question

Up to 0.2.2 the desktop app's Quit left Nouride running, and Nouride's LaunchAgent sat in `~/Library/LaunchAgents`,
so launchd started it at every login with no app and no menu bar icon: nothing on screen said Nouride was alive. The
owner's rule since 2026-11-28 is that Nouride runs only while something shows it. With this change:

- Quit Nouride (the menu bar, and the app's menu with ⌘Q) asks "Quit Nouride?" while Nouride runs here, then stops
  Nouride and the app goes; Cancel changes nothing.
- Opening the app starts a stopped Nouride.
- The menu bar's Stop Nouride server asks "Stop Nouride server?" and stops it at once, the app staying; Start Nouride
  server starts it at once.
- Nouride's plist lives in the app's folder; an install from before moves it there on the first start; the app opens
  at login through a login item of its own and starts Nouride there, with no window.
- Whatever else ends the app (the Dock's Quit, a logout) stops Nouride unasked; a crash leaves it running.

Does Nouride Home Dev do that on a real Mac, starting from a 0.2.2-era install?

Run on 2026-11-28, 10:57 to 11:05 +0700.

## What was tested

| | |
| --- | --- |
| Commit | `feat/quit-stops-all`: the builds were the tree of `d323144` (the Quit commit); steps 2 to 8 ran on a build whose `terminate` hook was the first, deadlocking one (below), steps 9 and 10 on `d323144`'s. The docs and the version commits after it change no code |
| Desktop | Nouride Home Dev (debug, `tech.nouverse.nouride.home.dev`), built on the Mac over the previous Home Dev build (main's Open Nouride flow, 0.2.2); a MacBook Air (Apple M1), macOS 27.0.1 |
| Nouride | 0.8.41, Home Dev's own install, label `tech.nouverse.nouride.home.dev.nouride`, port 18264 |

The release app (`/Applications/Nouride Home.app`, the owner's, in remote mode) was neither opened nor quit: same PID
(37738) before and after. Its label `tech.nouverse.nouride.home.nouride` and Nouride's own `tech.nouverse.nouride`
were not loaded before and not loaded after (`evidence/00-before.txt`, `evidence/11-after.txt`).

## Tests

| Check | Result |
| --- | --- |
| `cargo test --locked` in every crate (Linux) | 209 passed, 0 failed |
| `bun test` (apps/desktop) | 39 pass, 0 fail; `bun run build` built |
| `./gradlew testDebugUnitTest` (apps/android, after the word change) | 208 tests, 0 failed |
| `cargo check --locked`, with and without `--features dev` (the Tauri binary, on the Mac) | No error, no warning |
| `scripts/bump-version --check` | No file disagrees; VERSION is 0.2.3 |

## On the Mac

Each step's output is in `evidence/` under its number. `mac-state.sh` is the one-look script (the app processes, the
plists, launchd's jobs, the port, `/health`); `windows-on-screen.js` lists which processes have a window on screen
(owners only: no picture is taken).

| # | Step | Result |
| --- | --- | --- |
| 0 | Before | The 0.2.2-era Home Dev running; Nouride's plist in `~/Library/LaunchAgents`, its job running (pid 867), 18264 answering |
| 1 | The old app quit (`--e2e quit`, old behaviour: Nouride kept running), the new build opened | Nouride's plist moved to `<app folder>/tech.nouverse.nouride.home.dev.nouride.plist`, none left in `~/Library/LaunchAgents`; the job loaded from the old path kept running, same pid 867; the login item written (`tech.nouverse.nouride.home.dev.login.plist`, the app's executable and `--login`, RunAtLoad); the dashboard opened; the menu bar: "Dev · Nouride is running", "A new Nouride is ready", Open Nouride, Stop Nouride server, Quit Nouride |
| 2-3 | Quit from the menu bar, first build | The question did not show in the app: tauri-plugin-dialog with no window asks through CFUserNotification, drawn by UserNotificationCenter, which the hook cannot read. The app was ended (`--e2e quit`): Nouride stopped with it (launchd no longer knew the job, 18264 closed). The alert stayed on the Mac's screen after the app had gone; it was taken down by ending UserNotificationCenter (`killall`; launchd starts it again on demand), after checking it had no other window up. Fixed: the question is an NSAlert of the app's own (`questions.rs`) |
| 4 | The new build opened with Nouride stopped | Nouride started at once from the app folder's plist (new pid), `/health` 200 within about 3 s; Home shown while it started, as Open Nouride does |
| 5 | Quit from the menu bar, Cancel, then Quit | The alert read through `--e2e alert`: "Quit Nouride?", "Your agents stop answering until you open Nouride again.", buttons Cancel and Quit. Cancel: no alert, Nouride running with the same pid, the app running. Quit: the app gone, launchd no longer knows the job (`launchctl print` exit 113), 18264 closed, no window of the app or UserNotificationCenter on screen |
| 6 | Opened again; Stop Nouride server, then Start Nouride server | Opened: Nouride started (pid 48300), the dashboard came back. Stop: "Stop Nouride server?", "Your agents stop answering until you start it again.", Cancel and Stop; Stop pressed: the job gone, 18264 not answering, the app still running with its dashboard window, the menu bar "Dev · Nouride is off" with Start Nouride server. Start: running again (pid 48431), `/health` 200 |
| 7 | The app's menu | Its last item is Quit Nouride (`menu_quit`, ⌘Q) where the system's Quit was. Pressed: the same question; Cancel left everything running |
| 7-8 | `--e2e terminate`, first form | Called inside a Tauri main-thread callback, AppKit's terminate re-entered tao's handler lock in `applicationWillTerminate` and the app hung (main thread in `__psynch_mutexwait`, from `sample`). The hook's fault: the Dock's Quit reaches AppKit from its own event dispatch. The hung process was ended with SIGTERM; Nouride kept running (pid 48431), as after a crash, and opening the app again brought the icon back with the same Nouride. The hook now sends terminate from the main queue |
| 9 | `--e2e terminate` (as the Dock's Quit and a logout) | No question; the app gone, Nouride stopped, 18264 not answering; the app's log: "the app ends: Nouride stops" |
| 10 | A login, without logging out: `launchctl bootstrap gui/<uid>` of the login item's plist, as launchd does at login | launchd ran the app with `--login`; Nouride started and answered; no window opened (`dump`: both windows null); the menu bar there; the app's log: "opened at login", "the app opened: Nouride starts" |
| 11 | After | Home Dev running (started by its login item), Nouride running from the app folder's plist (pid 49477), `/health` 200; in `~/Library/LaunchAgents` only the login item |

## What this means

- Quit, Stop and Start do what the owner asked, and the questions show and answer as designed; opening the app brings
  a stopped Nouride back, and a 0.2.2 install moves over without its Nouride stopping.
- A question asked with no window must not go through tauri-plugin-dialog on macOS: it becomes a CFUserNotification
  that outlives the app.
- A Dev hook that ends the app must not call AppKit's terminate from inside a Tauri callback.

## Not verified

- **A real logout and login.** The owner was not logged out. The login was played by loading the login item into
  launchd by hand (step 10). Whether macOS shows a "background item added" notice for it, and how it is listed under
  Login Items in System Settings, was not seen; `sfltool dumpbtm` needs an administrator's authorization, which an ssh
  session cannot give.
- **The Dock's own Quit menu and a logout** were played by `--e2e terminate` (AppKit's terminate), not by a click on
  the Dock or a real logout.
- **Quit with Nouride on another computer** (no question, the app goes at once) is covered by the rule's tests
  (`service::rules::quit_asks`), not run on the Mac.
- **The alert as a person sees it**: read and pressed through AppKit (`--e2e alert`), not looked at; no picture of the
  screen was taken.
- **The release build**: only the Dev build ran. The release app's own plist migration (label
  `tech.nouverse.nouride.home.nouride`) is the same code with the release's label.
- **The app's own update restart** leaving Nouride running: not exercised (a Dev build is never offered an update).

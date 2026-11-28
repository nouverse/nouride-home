# Nouride Home for desktop

The desktop app (Tauri v2, macOS first): the screens in `apps/desktop`, the binary in `apps/desktop/src-tauri`, the
logic in `crates/`, the shared look in `packages/ui-kit`. The rules both apps share are in `../SKILL.md`.

## How it works

1. `apps/desktop/src-tauri` (Tauri v2) opens the app's window on the screens in `apps/desktop/src` and puts the cat in the menu bar. It holds no
   business logic: every rule is in `crates/`, which `apps/desktop/src-tauri/src/commands/` calls and the screens reach only
   through `apps/desktop/src/commons/lib/ipc.ts` (Tauri's `invoke` and one `state` event).
2. The app keeps one state (`apps/desktop/src-tauri/src/state.rs`): where Nouride runs (`place`), the local phase
   (`daemon::phase`), the remote reply, the setup's progress, the update notices. A poller asks `/health` every few
   seconds while the window is open and every minute when it is hidden; each change is sent to the UI as `state` and
   redraws the tray menu.
3. **Installing** (`daemon::installer`): the official installer is downloaded into the app's folder and run with
   `HOME=<data>/home`, a `PATH` of links to the system's tools without `launchctl` (`daemon::installer::shim`), and
   `--dir <data>/nouride --port <build port> --host 127.0.0.1 --variant router`. On macOS the installer otherwise writes
   `~/Library/LaunchAgents/tech.nouverse.nouride.plist`, `~/.local/bin/nouride` and `~/.config/nouride/nouride.env`
   and loads launchd: a person's own Nouride, installed by hand, would be replaced. `installer::args` and `env` are
   tested; never run the installer any other way.
4. After it: the gateway port into `config.toml` for a non-default port (`daemon::config`), the first account into
   `.env` (`daemon::first_admin`), then `nouride service install --kind launchd --dir <dir>` with the same `HOME`
   makes Nouride's own plist in the install folder, and `service::launchd` gives it the app's label and writes it to
   the app's folder, `<data>/<label>.plist`, never `~/Library/LaunchAgents`, where launchd would load it at every login
   without the app. It builds the `bootstrap`, `bootout`, `kickstart -k` and `print` command lines and reads `print`'s
   answer. An install from 0.2.2 or before has its plist in `~/Library/LaunchAgents`: the app's first start moves it
   (`LaunchAgent::move_from`, its own label only), and the job loaded from it keeps running.
5. **Nouride runs only while the app shows it** (`service::rules`): opening the app starts a stopped Nouride
   (`windows::start_if_stopped`, from `open_nouride`); Quit Nouride asks while Nouride runs here, then boots it out and
   exits (`commands::quit_now`); whatever else ends the app (RunEvent::Exit: the Dock's Quit, a logout) stops it
   unasked (`commands::stop_on_exit`), except the restart after the app's own update. The app opens at login through a
   LaunchAgent of its own, `~/Library/LaunchAgents/<Build::login_label>.plist`, RunAtLoad, no KeepAlive, running the
   app with `--login` (`service::login`; kept in step with the place on each poll): the app then starts Nouride and
   opens no window. The menu bar's questions are an NSAlert run from the main queue (`questions.rs`): with no window,
   tauri-plugin-dialog asks through CFUserNotification, another process's alert the app can neither read nor close.
6. **Opening the dashboard**: `daemon::device_sign_in` trades the app's device token for a session over HTTP (making
   one with the control token first when there is none or it was refused, `spec/device-sign-in.md`), the session
   cookie is set on the dashboard's window (`set_cookie`), and the window's `on_navigation` asks `link_policy::link`
   what each load is. The dashboard's two ways out of a page are links it catches there: the header's Back is
   decided by `link_policy::back` from WebKit's own history of the window (`windows/history.rs`), "Back to Nouride
   Home" by `link_policy::leave`, and the question either may ask is a sheet on the window (`tauri-plugin-dialog`). A script in the window (`device_sign_in::watch_script`) turns the dashboard's in-page move to its
   sign-in page into a load, which `on_navigation` cancels to sign in again. Its
   `on_download` asks `downloads::source` whether a download is kept, names it in Downloads (`downloads::free_path`,
   after taking back the number wry gives a taken name itself) and shows a line in the page (`downloads::line_call`).
7. **Nouride's update**: `updates::nouride` reads the control-token routes, `updates::notice` keeps what was heard,
   `updates::stall` restarts a stuck download. The app's own: `tauri-plugin-updater` against this repository's
   Releases.

## Builds

`shared::Build` holds every name and number that differs between the release and the Dev build: the bundle id, the
app's name, Nouride's port and its gateway's, the LaunchAgent label, the link scheme. The cargo feature `dev` picks
`Build::DEV`; `tauri.dev.conf.json` (merged over `tauri.conf.json` with `--config`) gives the Dev app its id, name and
yellow icon. A literal port or id anywhere else is a bug: the Dev build would reach the person's Nouride.

```sh
cd apps/desktop
bun run build:dev        # Nouride Home Dev (debug), target/debug/bundle/macos/Nouride Home Dev.app
bun run build:release    # Nouride Home, target/release/bundle/macos/Nouride Home.app
```

`target/` is the Cargo workspace's, at the repository's root. Both build the screens first (`beforeBuildCommand` in
`tauri.conf.json`). The Tauri CLI runs under Bun (`bun --bun`): a Mac with no Node needs nothing more. Builds
are unsigned (ad-hoc): no Apple Developer account yet.

Nouride's `service install --kind launchd` writes its start command split on spaces (up to 0.8.38 at least), so an
install under `~/Library/Application Support` got a plist launchd could not run (exit 126). `service::launchd::
with_program` puts the binary's path back as one argument; keep it until Nouride writes the argv whole.

## Tests

- Crates: `cargo test` in each, on Linux, no screen: `for d in crates/*/; do (cd "$d" && cargo test -q) || exit 1; done`.
  The rules ported from Android keep Android's test cases, one for one, so a difference is visible; those both apps
  implement are also in `spec/fixtures/`, read by `crates/*/tests/spec_*.rs` and by the Android tests.
- UI: `cd apps/desktop && bun test` (the words, the sections and their shortcuts, the dashboard window's new-tabs script,
  formatting) and `bun run build`. Pictures of the screens without a Mac: `bun run build:fixture` (Tauri's API mocked
  by `apps/desktop/fixtures/`, never in the app's bundle) and `experiments/2026-11-16-desktop-layout/render.mjs`.
- A change carries a test that fails without it: break it, see it red, put it back, and say "sabotage: ..." in the
  commit body.

## Testing on a Mac (over ssh)

The owner's Mac is reached with `ssh mac`. The owner's own Nouride runs there (`~/nouride`, LaunchAgent
`tech.nouverse.nouride`, port 18254) and benchmark containers on 18354 and 18454: never stop, update, sign in to or
write to any of them. Test only Nouride Home Dev, which installs into
`~/Library/Application Support/tech.nouverse.nouride.home.dev/` and runs on 18264/18266 under the label
`tech.nouverse.nouride.home.dev.nouride`.

- Work in `~/nix-work/nouride-home` (rsync the tree there, without `target/` and `node_modules/`).
  rustup is user-level (`. ~/.cargo/env`); Bun is `~/.bun/bin/bun`; `zsh -lc` does not put either on PATH.
- Before and after every run: `scripts/mac-guard.sh` records the sha256 of
  `~/Library/LaunchAgents/tech.nouverse.nouride.plist` and the PID of `launchctl list tech.nouverse.nouride`, and
  fails when either changed.
- The e2e hooks (Dev build only, `apps/desktop/src-tauri/src/e2e.rs`): run the app's binary again with `--e2e <action>` and the
  running app does it (single instance): `dump` (the state as JSON into `<data>/e2e/state.json`), `place-here`,
  `place-remote <address>`, `start`, `stop`, `open-dashboard`, `close-dashboard`, `close-main` (both as the window's close
  button: the window is destroyed), `show <home|connect|versions|settings|logs>` (makes the window again when it is closed),
  `dashboard-js <script>` (runs a script in the dashboard's window, as a click there would; it returns nothing, so a
  script that reads the page writes what it found into the address with `history.replaceState` for `dump` to show),
  `sheet [button's words]` (the question on the dashboard's window, "Go back to Nouride Home?": its words and
  buttons, and with a button's words a click on it; `dump` shows it as `question`, `null` when none is up),
  `alert [button's words]` (the menu bar's question, "Quit Nouride?" or "Stop Nouride server?", the same way; `dump`
  shows it as `alert`), `tray <item id>` (`open`, `start`, `stop`, `quit`, `updates`), `menu [item id]` (the menus at
  the top of the screen as JSON, and with `menu_settings`, `menu_home` or `menu_quit` a press on that item),
  `terminate` (ends the app through AppKit, as the Dock's Quit and a logout do), `check-nouride-update`, `shot <name>`
  (a capture of the app's own window only, never the screen). `quit` ends the app as Quit does once answered: Nouride
  stops with it. `dump` shows `agent` (Nouride's plist) and `login` (the login item).
  `dump` gives each window's size, and `null` for a window that is closed (destroyed). Run by its login item the app
  has `--login` on its command line, which `scripts/e2e-mac.sh`'s `pgrep -fx` does not match: call the binary with
  `--e2e` directly then. While `<data>/e2e/no-browser` exists, a link the dashboard sends to the browser is
  only logged ("would go to the browser"): the browser is on the person's screen, so open it for real once, at the
  end, and say how many times you did.
- The first download from the dashboard's window makes macOS ask on screen whether the app may use the Downloads
  folder, and WebKit waits for the answer: the app stops answering its hooks until someone clicks Allow. Ask the
  person at the Mac before a first download test.
- Quit what you open (`--e2e quit`, which stops the Dev Nouride too), and remove only what you made: the Dev app's
  folder (Nouride's plist is in it), its LaunchAgent if loaded (`launchctl bootout
  gui/$(id -u)/tech.nouverse.nouride.home.dev.nouride`), and its login item
  (`~/Library/LaunchAgents/tech.nouverse.nouride.home.dev.login.plist`).
- A login can be tried without logging out: `launchctl bootstrap gui/$(id -u)` with the login item's plist runs it as
  launchd does at login.


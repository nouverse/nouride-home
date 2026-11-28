pub mod dashboard;
mod history;
pub mod main;

use std::sync::Arc;

use looks::tray::Target;
use tauri::{AppHandle, Manager, Theme};

use crate::commands;
use crate::state::{App, Appearance};

/// Open Nouride, the one way the app is opened: the menu bar's item, the Dock, opening the app again and its start
/// all start this computer's Nouride when it was stopped (by Quit or Stop), then show what `looks::tray::open_target`
/// says, the dashboard or, with none to reach (Nouride starting, too), Nouride Home. Off the main thread: starting
/// Nouride and opening the dashboard ask launchd and Nouride first.
pub fn open_nouride(handle: &AppHandle, app: &Arc<App>) {
  let (h, a) = (handle.clone(), app.clone());
  std::thread::spawn(move || {
    start_if_stopped(&h, &a);
    let s = a.snapshot();
    match looks::tray::open_target(s.place, &s.look) {
      Target::Dashboard => dashboard::open(&h, &a),
      Target::Home => main::show(&h),
    }
  });
}

/// Starts this computer's Nouride when the app installed it and it is stopped (`service::rules::starts_on_open`). The
/// app's login item does only this: Nouride comes back with the icon, and no window opens at login.
pub fn start_if_stopped(handle: &AppHandle, app: &Arc<App>) {
  let loaded = service::Supervisor::job(&app.agent).unwrap_or_default().loaded;
  if service::rules::starts_on_open(app.now(loaded)) {
    crate::applog::write(&app.layout.app_log(), "the app opened: Nouride starts");
    commands::start_now(handle, app);
  }
}

/// Applies the appearance chosen on More to every window: the screens and the dashboard follow it through
/// `prefers-color-scheme`.
pub fn apply_appearance(handle: &AppHandle, appearance: Appearance) {
  let theme = match appearance {
    Appearance::System => None,
    Appearance::Light => Some(Theme::Light),
    Appearance::Dark => Some(Theme::Dark),
  };
  for window in handle.webview_windows().values() {
    let _ = window.set_theme(theme);
  }
}

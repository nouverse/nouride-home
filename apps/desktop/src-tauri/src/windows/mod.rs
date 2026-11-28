pub mod dashboard;
mod history;
pub mod main;

use std::sync::Arc;

use looks::tray::Target;
use tauri::{AppHandle, Manager, Theme};

use crate::state::{App, Appearance};

/// Open Nouride, the one way the app is opened: the menu bar's item, the Dock, opening the app again and its start
/// all show what `looks::tray::open_target` says, the dashboard or, with none to reach, Nouride Home. Off the main
/// thread: opening the dashboard asks Nouride first.
pub fn open_nouride(handle: &AppHandle, app: &Arc<App>) {
  let (h, a) = (handle.clone(), app.clone());
  std::thread::spawn(move || {
    let s = a.snapshot();
    match looks::tray::open_target(s.place, &s.look) {
      Target::Dashboard => dashboard::open(&h, &a),
      Target::Home => main::show(&h),
    }
  });
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

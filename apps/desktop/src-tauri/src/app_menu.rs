//! The menus along the top of the screen while the app is in front: Tauri's own (the app's, File, Edit with copy and
//! paste, View, Window, Help), with two ways to Nouride Home's window that need nothing from the dashboard's page, so
//! a page that breaks never leaves the person without one: Settings… (⌘,) in the app's menu, and the app's name in the
//! Window menu. The app's menu ends with the menu bar's Quit Nouride (⌘Q) in place of the system's own Quit, which
//! would end the app without a word and leave nothing to say Nouride runs.

use tauri::menu::{Menu, MenuItem, MenuItemKind, PredefinedMenuItem, WINDOW_SUBMENU_ID};
use std::sync::Arc;

use tauri::{AppHandle, Manager, Wry};

use crate::state::App;
use crate::{commands, windows};
use crate::words::t;

pub const SETTINGS: &str = looks::window::MENU_SETTINGS;
pub const HOME: &str = "menu_home";
/// Its own id: every menu event comes here, the menu bar's `quit` too, which the menu bar answers itself.
pub const QUIT: &str = "menu_quit";

pub fn build(handle: &AppHandle, app_name: &str) -> tauri::Result<Menu<Wry>> {
  let menu = Menu::default(handle)?;
  // The app's menu comes first on a Mac: Settings… goes under About and its line, as every Mac app has it.
  #[cfg(target_os = "macos")]
  if let Some(MenuItemKind::Submenu(app_menu)) = menu.items()?.first() {
    let settings = MenuItem::with_id(handle, SETTINGS, t(SETTINGS), true, Some("CmdOrCtrl+,"))?;
    app_menu.insert_items(&[&settings, &PredefinedMenuItem::separator(handle)?], 2)?;
    let items = app_menu.items()?;
    let system_quit = items.iter().position(|i| matches!(i, MenuItemKind::Predefined(p) if p.text().is_ok_and(|w| w.starts_with("Quit"))));
    if let Some(at) = system_quit {
      app_menu.remove_at(at)?;
      app_menu.insert(&MenuItem::with_id(handle, QUIT, t(looks::tray::QUIT_WORDS), true, Some("CmdOrCtrl+Q"))?, at)?;
    }
  }
  if let Some(MenuItemKind::Submenu(window)) = menu.get(WINDOW_SUBMENU_ID) {
    let home = MenuItem::with_id(handle, HOME, app_name, true, None::<&str>)?;
    window.append_items(&[&PredefinedMenuItem::separator(handle)?, &home])?;
  }
  Ok(menu)
}

/// Called for every menu event, the menu bar icon's too: only these three are this menu's.
pub fn press(handle: &AppHandle, id: &str) {
  match id {
    SETTINGS => windows::main::show_at(handle, "settings"),
    HOME => windows::main::show(handle),
    QUIT => commands::quit_now(handle, handle.state::<Arc<App>>().inner()),
    _ => {}
  }
}

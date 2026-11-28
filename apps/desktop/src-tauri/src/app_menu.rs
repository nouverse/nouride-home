//! The menus along the top of the screen while the app is in front: Tauri's own (the app's, File, Edit with copy and
//! paste, View, Window, Help), with two ways to Nouride Home's window that need nothing from the dashboard's page, so
//! a page that breaks never leaves the person without one: Settings… (⌘,) in the app's menu, and the app's name in the
//! Window menu.

use tauri::menu::{Menu, MenuItem, MenuItemKind, PredefinedMenuItem, WINDOW_SUBMENU_ID};
use tauri::{AppHandle, Wry};

use crate::windows;
use crate::words::t;

pub const SETTINGS: &str = looks::window::MENU_SETTINGS;
pub const HOME: &str = "menu_home";

pub fn build(handle: &AppHandle, app_name: &str) -> tauri::Result<Menu<Wry>> {
  let menu = Menu::default(handle)?;
  // The app's menu comes first on a Mac: Settings… goes under About and its line, as every Mac app has it.
  #[cfg(target_os = "macos")]
  if let Some(MenuItemKind::Submenu(app_menu)) = menu.items()?.first() {
    let settings = MenuItem::with_id(handle, SETTINGS, t(SETTINGS), true, Some("CmdOrCtrl+,"))?;
    app_menu.insert_items(&[&settings, &PredefinedMenuItem::separator(handle)?], 2)?;
  }
  if let Some(MenuItemKind::Submenu(window)) = menu.get(WINDOW_SUBMENU_ID) {
    let home = MenuItem::with_id(handle, HOME, app_name, true, None::<&str>)?;
    window.append_items(&[&PredefinedMenuItem::separator(handle)?, &home])?;
  }
  Ok(menu)
}

/// Called for every menu event, the menu bar icon's too: only these two are this menu's.
pub fn press(handle: &AppHandle, id: &str) {
  match id {
    SETTINGS => windows::main::show_at(handle, "settings"),
    HOME => windows::main::show(handle),
    _ => {}
  }
}

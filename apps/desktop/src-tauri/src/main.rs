//! Nouride Home for desktop: the window, the menu bar and the commands, wired to the crates. No business logic here.

#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod app_menu;
mod applog;
mod commands;
#[cfg(feature = "dev")]
mod e2e;
mod poller;
mod questions;
mod setup;
mod state;
mod tray;
mod versions;
mod windows;
mod words;

use std::sync::Arc;

use daemon::Layout;
use shared::Build;
use tauri::{Manager, RunEvent};

use crate::state::App;

fn main() {
  let build = Build::current();
  let mut builder = tauri::Builder::default();
  // One app at a time: opening it again is Open Nouride in the first (and, in the Dev build, hands it an e2e action).
  builder = builder.plugin(tauri_plugin_single_instance::init(|handle, argv, _cwd| {
    #[cfg(feature = "dev")]
    if argv.iter().any(|a| a == "--e2e") {
      let app = handle.state::<Arc<App>>().inner().clone();
      let handle = handle.clone();
      std::thread::spawn(move || e2e::handle(&handle, &app, &argv));
      return;
    }
    let app = handle.state::<Arc<App>>().inner().clone();
    if service::login::launched_at_login(&argv) {
      let handle = handle.clone();
      std::thread::spawn(move || windows::start_if_stopped(&handle, &app));
    } else {
      windows::open_nouride(handle, &app);
    }
  }));
  builder
    .plugin(tauri_plugin_opener::init())
    .plugin(tauri_plugin_clipboard_manager::init())
    .plugin(tauri_plugin_updater::Builder::new().build())
    .plugin(tauri_plugin_deep_link::init())
    .plugin(tauri_plugin_dialog::init())
    .menu(move |handle| app_menu::build(handle, build.name))
    .on_menu_event(|handle, event| app_menu::press(handle, event.id().as_ref()))
    .setup(move |tauri_app| {
      let handle = tauri_app.handle().clone();
      // The bundle id the config gave this app must be the build's: a release config with the Dev feature (or the
      // other way round) would put one build's Nouride in the other's folder.
      let identifier = tauri_app.config().identifier.clone();
      if identifier != build.id {
        return Err(format!("this binary is {} but its bundle is {identifier}", build.id).into());
      }
      let data = tauri_app.path().app_data_dir()?;
      let home = tauri_app.path().home_dir()?;
      // SAFETY: getuid has no preconditions and cannot fail.
      let uid = unsafe { libc::getuid() };
      let app = Arc::new(App::new(build, Layout::new(&data), uid, &home));
      applog::write(&app.layout.app_log(), &format!("{} {} started", build.name, env!("CARGO_PKG_VERSION")));
      // Up to 0.2.2 Nouride's plist was where launchd loads it at every login, without the app: it moves into the
      // app's folder, and the job loaded from it keeps running.
      match app.agent.move_from(&service::launchd::launch_agents(&home)) {
        Ok(true) => applog::write(&app.layout.app_log(), "Nouride's LaunchAgent moved into the app's folder"),
        Ok(false) => {}
        Err(e) => applog::write(&app.layout.app_log(), &format!("Nouride's LaunchAgent could not move: {e}")),
      }
      tauri_app.manage(app.clone());
      windows::apply_appearance(&handle, app.appearance());
      tray::create(&handle, &app)?;
      poller::start(handle.clone(), app.clone());
      // The app opens as Open Nouride does, once it has looked at Nouride: no window is made from the config. Run by
      // its login item, it only starts Nouride and stays in the menu bar.
      let at_login = service::login::launched_at_login(&std::env::args().collect::<Vec<_>>());
      let (h, a) = (handle.clone(), app.clone());
      std::thread::spawn(move || {
        poller::tick(&h, &a, &mut 0);
        if at_login {
          applog::write(&a.layout.app_log(), "opened at login");
          windows::start_if_stopped(&h, &a);
        } else {
          windows::open_nouride(&h, &a);
        }
      });
      // A Nouride the app installed is checked for a new version when the app opens, 15 minutes apart at most.
      let (h, a) = (handle.clone(), app.clone());
      std::thread::spawn(move || {
        std::thread::sleep(std::time::Duration::from_secs(5));
        versions::maybe_check_notice(&a, updates::notice::ON_OPEN_MS);
        poller::send(&h, &a);
      });
      Ok(())
    })
    .invoke_handler(tauri::generate_handler![
      commands::state,
      commands::choose_here,
      commands::choose_remote,
      commands::start,
      commands::retry,
      commands::turn_off,
      commands::open_dashboard,
      commands::set_access,
      commands::set_appearance,
      commands::check_app,
      commands::update_app,
      commands::check_nouride,
      commands::update_nouride,
      commands::install_nouride,
      commands::logs,
      commands::copy,
      commands::qr,
    ])
    .build(tauri::generate_context!())
    .expect("Nouride Home could not start")
    .run(|handle, event| {
      // A closed window is destroyed with its WebKit processes (`windows::main`); closing the last one asks the app
      // to exit with no code, and the app stays in the menu bar. Quit gives a code.
      if let RunEvent::ExitRequested { code, api, .. } = &event {
        if !looks::window::exits(*code) {
          api.prevent_exit();
        }
      }
      // Whatever ends the app stops the Nouride it runs, asked or not: Quit asked already, and stopped it; the Dock's
      // Quit and a logout end the app through the system and are not asked (`service::rules::stops_on_exit`). Not
      // when the app starts again to finish its own update: it is back in a moment.
      if let RunEvent::Exit = event {
        let app = handle.state::<Arc<App>>().inner().clone();
        commands::stop_on_exit(&app);
      }
      // The Dock icon clicked with no window shown is Open Nouride.
      #[cfg(target_os = "macos")]
      if let RunEvent::Reopen { has_visible_windows: false, .. } = event {
        windows::open_nouride(handle, handle.state::<Arc<App>>().inner());
      }
      let _ = (handle, event);
    });
}

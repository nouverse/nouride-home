//! The menu bar's two questions before Nouride stops (`looks::ask`): the system's own alert, over every window, since
//! the menu bar has no window of its own to put a sheet on. One at a time: a second press while one is up asks nothing.
//!
//! On macOS it is an NSAlert of the app's own, run from the main queue. Not tauri-plugin-dialog's: without a window it
//! asks through CFUserNotification, an alert drawn by another process that the app cannot read or close, which stays
//! on screen when the app has gone. Not from a Tauri callback either: while a modal runs inside one, the app's other
//! events wait for it to end.

use std::sync::atomic::{AtomicBool, Ordering};

use looks::ask::Question;
use tauri::AppHandle;

use crate::words::t;

static ASKING: AtomicBool = AtomicBool::new(false);

/// Asks `question` and calls `then` with the answer: true for its yes, false for Cancel. The app comes to the front
/// first, or the alert would open behind the window the person was in when they pressed the menu bar.
pub fn ask(handle: &AppHandle, question: Question, then: impl FnOnce(bool) + Send + 'static) {
  if ASKING.swap(true, Ordering::SeqCst) {
    return;
  }
  let words = [t(question.title), t(question.body), t(question.yes), t(question.no)];
  #[cfg(target_os = "macos")]
  {
    let _ = handle;
    dispatch2::DispatchQueue::main().exec_async(move || {
      let yes = alert(&words);
      ASKING.store(false, Ordering::SeqCst);
      then(yes);
    });
  }
  #[cfg(not(target_os = "macos"))]
  {
    use tauri_plugin_dialog::{DialogExt, MessageDialogButtons};
    let [title, body, yes, no] = words;
    handle.dialog().message(body).title(title).buttons(MessageDialogButtons::OkCancelCustom(yes, no)).show(move |yes| {
      ASKING.store(false, Ordering::SeqCst);
      then(yes);
    });
  }
}

/// The alert, modal to the app until it is answered; whether its first button (the yes) was pressed. On the main
/// thread.
#[cfg(target_os = "macos")]
fn alert([title, body, yes, no]: &[String; 4]) -> bool {
  use objc2::MainThreadMarker;
  use objc2_app_kit::{NSAlert, NSAlertFirstButtonReturn, NSApplication};
  use objc2_foundation::NSString;

  let Some(mtm) = MainThreadMarker::new() else { return false };
  #[allow(deprecated)]
  NSApplication::sharedApplication(mtm).activateIgnoringOtherApps(true);
  let alert = NSAlert::new(mtm);
  alert.setMessageText(&NSString::from_str(title));
  alert.setInformativeText(&NSString::from_str(body));
  alert.addButtonWithTitle(&NSString::from_str(yes));
  alert.addButtonWithTitle(&NSString::from_str(no));
  alert.runModal() == NSAlertFirstButtonReturn
}

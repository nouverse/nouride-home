//! Opening the app at login, so that Nouride, which the app starts (`crate::launchd`), is back after a restart with
//! the app's icon in the menu bar beside it. A LaunchAgent of the app's own in `~/Library/LaunchAgents`, which launchd
//! runs once at login (`RunAtLoad`, no `KeepAlive`: a Quit is final until the next login or the person opens it).
//! It runs the app with [`LOGIN_ARG`], so the app starts Nouride and stays in the menu bar without opening a window:
//! macOS's `SMAppService.mainApp` opens the app with no way to tell a login from the person's own click.

use std::fs;
use std::io;
use std::path::{Path, PathBuf};

use crate::launchd::{escape, NOURIDE_OWN_LABEL};

/// What the app is run with at login.
pub const LOGIN_ARG: &str = "--login";

pub struct LoginItem {
  pub label: String,
  /// `~/Library/LaunchAgents/<label>.plist`.
  pub path: PathBuf,
}

impl LoginItem {
  pub fn new(label: &str, launch_agents: &Path) -> LoginItem {
    LoginItem { label: label.to_owned(), path: launch_agents.join(format!("{label}.plist")) }
  }

  /// The plist that runs `app` (the app's own executable) with [`LOGIN_ARG`] once at login, in the person's session.
  pub fn plist(&self, app: &Path) -> String {
    format!(
      r#"<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>{}</string>
  <key>ProgramArguments</key>
  <array>
    <string>{}</string>
    <string>{LOGIN_ARG}</string>
  </array>
  <key>RunAtLoad</key>
  <true/>
  <key>LimitLoadToSessionType</key>
  <string>Aqua</string>
  <key>ProcessType</key>
  <string>Interactive</string>
</dict>
</plist>
"#,
      escape(&self.label),
      escape(&app.display().to_string())
    )
  }

  /// Puts the plist in place for `app` when `wanted`, and takes it away when not; whether anything changed. Written
  /// again when the app has moved. Never loaded now: launchd reads it at the next login, and the app is running.
  pub fn sync(&self, wanted: bool, app: &Path) -> io::Result<bool> {
    if self.label == NOURIDE_OWN_LABEL {
      return Err(io::Error::other("the app's login item may not take Nouride's own label"));
    }
    if !wanted {
      if self.path.is_file() {
        fs::remove_file(&self.path)?;
        return Ok(true);
      }
      return Ok(false);
    }
    let plist = self.plist(app);
    if fs::read_to_string(&self.path).ok().as_deref() == Some(plist.as_str()) {
      return Ok(false);
    }
    if let Some(dir) = self.path.parent() {
      fs::create_dir_all(dir)?;
    }
    fs::write(&self.path, plist)?;
    Ok(true)
  }

  pub fn registered(&self) -> bool {
    self.path.is_file()
  }
}

#[cfg(test)]
mod tests {
  use super::*;

  const LABEL: &str = "tech.nouverse.nouride.home.dev.login";

  fn item(name: &str) -> LoginItem {
    let dir = std::env::temp_dir().join(format!("service-login-{}-{name}", std::process::id()));
    let _ = fs::remove_dir_all(&dir);
    LoginItem::new(LABEL, &dir.join("LaunchAgents"))
  }

  #[test]
  fn the_plist_runs_the_app_once_at_login_with_the_login_argument() {
    let app = Path::new("/Applications/Nouride Home & Co.app/Contents/MacOS/nouride-home");
    let plist = item("plist").plist(app);
    assert!(plist.contains(&format!("<key>Label</key>\n  <string>{LABEL}</string>")));
    assert!(plist.contains(
      "<array>\n    <string>/Applications/Nouride Home &amp; Co.app/Contents/MacOS/nouride-home</string>\n    <string>--login</string>\n  </array>"
    ));
    assert!(plist.contains("<key>RunAtLoad</key>\n  <true/>"));
    assert!(!plist.contains("KeepAlive"), "a Quit would be undone");
  }

  #[test]
  fn it_is_there_only_while_wanted_and_written_again_when_the_app_moved() {
    let item = item("sync");
    let (a, b) = (Path::new("/Applications/A.app/Contents/MacOS/a"), Path::new("/Users/x/Applications/A.app/Contents/MacOS/a"));
    assert!(!item.sync(false, a).unwrap());
    assert!(item.sync(true, a).unwrap());
    assert!(item.registered());
    assert!(!item.sync(true, a).unwrap());
    assert!(item.sync(true, b).unwrap());
    assert!(fs::read_to_string(&item.path).unwrap().contains("/Users/x/Applications/A.app"));
    assert!(item.sync(false, b).unwrap());
    assert!(!item.registered());
  }

  #[test]
  fn nouride_s_own_label_is_never_written() {
    let dir = std::env::temp_dir().join(format!("service-login-{}-own", std::process::id()));
    let item = LoginItem::new(NOURIDE_OWN_LABEL, &dir);
    assert!(item.sync(true, Path::new("/x")).is_err());
    assert!(!item.path.exists());
  }
}

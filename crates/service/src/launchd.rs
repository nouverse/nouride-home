//! The app's LaunchAgent for Nouride: `<label>.plist` in the app's own folder, loaded in the person's GUI session
//! (`gui/<uid>`) with `launchctl bootstrap`, stopped with `bootout`, restarted with `kickstart -k`, read with `print`.
//! Not in `~/Library/LaunchAgents`, which launchd loads at every login: Nouride runs only while the app is there to
//! show it, so the app starts it (`crate::login` opens the app at login). Loaded, it keeps `KeepAlive`: a Nouride that
//! crashes is started again, and one keeps running when the app itself crashes.

use std::fs;
use std::io;
use std::path::{Path, PathBuf};

use crate::{Exec, Job, Supervisor};

/// The label Nouride's installer and `service install` use, which only a Nouride installed by hand may have.
pub const NOURIDE_OWN_LABEL: &str = "tech.nouverse.nouride";

/// What `launchctl print` exits with for a service it does not know.
const NOT_LOADED: i32 = 113;

pub(crate) fn escape(text: &str) -> String {
  text.replace('&', "&amp;").replace('<', "&lt;").replace('>', "&gt;").replace('"', "&quot;")
}

/// The plist's `ProgramArguments` with `program` as the command it runs. Nouride writes its start command split on
/// spaces (`service install`, up to 0.8.38 at least), so an install folder with a space in it, as every folder under
/// `~/Library/Application Support` has, became two arguments and launchd could not start it (exit 126). Its own update
/// guard, the `/bin/sh -c` script before the command, is kept as it is; a plist without one runs `program` alone.
pub fn with_program(plist: &str, program: &[String]) -> Result<String, String> {
  let key = "<key>ProgramArguments</key>";
  let at = plist.find(key).ok_or("no ProgramArguments in Nouride's plist")?;
  let open = at + plist[at..].find("<array>").ok_or("ProgramArguments is not an array")? + "<array>".len();
  let close = open + plist[open..].find("</array>").ok_or("an unclosed ProgramArguments")?;
  let entries: Vec<&str> = plist[open..close]
    .split("<string>")
    .skip(1)
    .map(|e| e.split("</string>").next().unwrap_or(""))
    .collect();
  let mut keep: Vec<String> = vec![];
  if entries.len() >= 3 && entries[0] == "/bin/sh" && entries[1] == "-c" {
    keep = entries[..3].iter().map(|e| (*e).to_owned()).collect();
  }
  keep.extend(program.iter().map(|p| escape(p)));
  let indent = "    ";
  let body: String = keep.iter().map(|e| format!("\n{indent}<string>{e}</string>")).collect();
  Ok(format!("{}{}\n  {}", &plist[..open], body, &plist[close..]))
}

/// Nouride's generated plist with `label` in place of its own. Refused, rather than written, when it is not the plist
/// expected: no `Label`, no `ProgramArguments`, or its own label anywhere else in it.
pub fn relabel(plist: &str, label: &str) -> Result<String, String> {
  if label == NOURIDE_OWN_LABEL {
    return Err("the app's LaunchAgent may not take Nouride's own label".into());
  }
  if !plist.contains("<key>ProgramArguments</key>") {
    return Err("no ProgramArguments in Nouride's plist".into());
  }
  let key = "<key>Label</key>";
  let at = plist.find(key).ok_or("no Label in Nouride's plist")?;
  let after = &plist[at + key.len()..];
  let open = after.find("<string>").ok_or("no Label value")?;
  if !after[..open].trim().is_empty() {
    return Err("the Label key has no string after it".into());
  }
  let value_start = at + key.len() + open + "<string>".len();
  let close = plist[value_start..].find("</string>").ok_or("an unclosed Label")?;
  let out = format!("{}{}{}", &plist[..value_start], label, &plist[value_start + close..]);
  if out.contains(&format!(">{NOURIDE_OWN_LABEL}<")) {
    return Err("Nouride's own label is still in the plist".into());
  }
  Ok(out)
}

/// `~/Library/LaunchAgents`, which launchd loads at every login.
pub fn launch_agents(home: &Path) -> PathBuf {
  home.join("Library").join("LaunchAgents")
}

pub fn domain(uid: u32) -> String {
  format!("gui/{uid}")
}

pub fn bootstrap(uid: u32, plist: &Path) -> Vec<String> {
  vec!["launchctl".into(), "bootstrap".into(), domain(uid), plist.display().to_string()]
}

pub fn bootout(uid: u32, label: &str) -> Vec<String> {
  vec!["launchctl".into(), "bootout".into(), format!("{}/{label}", domain(uid))]
}

pub fn kickstart(uid: u32, label: &str) -> Vec<String> {
  vec!["launchctl".into(), "kickstart".into(), "-k".into(), format!("{}/{label}", domain(uid))]
}

pub fn print(uid: u32, label: &str) -> Vec<String> {
  vec!["launchctl".into(), "print".into(), format!("{}/{label}", domain(uid))]
}

/// The job from `launchctl print`'s exit code and answer.
pub fn read_print(code: i32, text: &str) -> Job {
  if code != 0 {
    return Job::default();
  }
  let value = |key: &str| {
    text.lines().map(str::trim).find_map(|l| l.strip_prefix(key).and_then(|r| r.trim_start().strip_prefix('=')).map(|v| v.trim().to_owned()))
  };
  Job {
    loaded: true,
    pid: value("pid").and_then(|v| v.parse().ok()),
    last_exit: value("last exit code").and_then(|v| {
      let number: String = v.chars().take_while(|c| c.is_ascii_digit() || *c == '-').collect();
      number.parse().ok()
    }),
  }
}

pub struct LaunchAgent<E: Exec> {
  pub exec: E,
  pub uid: u32,
  pub label: String,
  /// What the job runs: Nouride's binary and `start`, each one argument whatever spaces are in its path.
  pub program: Vec<String>,
  /// `<the app's folder>/<label>.plist`.
  pub path: PathBuf,
}

impl<E: Exec> LaunchAgent<E> {
  /// The job's plist is kept in `dir`, the app's own folder.
  pub fn new(exec: E, uid: u32, label: &str, dir: &Path, program: Vec<String>) -> LaunchAgent<E> {
    LaunchAgent { exec, uid, label: label.to_owned(), program, path: dir.join(format!("{label}.plist")) }
  }

  /// Moves this job's plist out of `launch_agents` (`~/Library/LaunchAgents`, where up to 0.2.2 the app put it and
  /// launchd loaded it at every login) into the app's folder; whether there was one to move. The job loaded from it
  /// keeps running: launchd holds what it read. Only the app's own label is ever looked for: Nouride's
  /// (`tech.nouverse.nouride`) belongs to a Nouride installed by hand.
  pub fn move_from(&self, launch_agents: &Path) -> io::Result<bool> {
    if self.label == NOURIDE_OWN_LABEL {
      return Err(io::Error::other("the app's LaunchAgent may not take Nouride's own label"));
    }
    let old = launch_agents.join(format!("{}.plist", self.label));
    if !old.is_file() {
      return Ok(false);
    }
    if let Some(dir) = self.path.parent() {
      fs::create_dir_all(dir)?;
    }
    if self.path.is_file() {
      fs::remove_file(&old)?;
    } else {
      fs::rename(&old, &self.path)?;
    }
    Ok(true)
  }

  fn run(&self, argv: Vec<String>) -> io::Result<(i32, String)> {
    self.exec.run(&argv)
  }

  fn loaded(&self) -> io::Result<bool> {
    Ok(self.job()?.loaded)
  }
}

impl<E: Exec> Supervisor for LaunchAgent<E> {
  fn install_and_start(&self, generated: &str) -> io::Result<()> {
    let plist = with_program(&relabel(generated, &self.label).map_err(io::Error::other)?, &self.program).map_err(io::Error::other)?;
    if self.loaded()? {
      self.run(bootout(self.uid, &self.label))?;
    }
    if let Some(dir) = self.path.parent() {
      fs::create_dir_all(dir)?;
    }
    fs::write(&self.path, plist)?;
    self.start()
  }

  fn start(&self) -> io::Result<()> {
    if self.loaded()? {
      return Ok(());
    }
    let (code, text) = self.run(bootstrap(self.uid, &self.path))?;
    if code != 0 {
      return Err(io::Error::other(format!("launchctl bootstrap exited {code}: {}", text.trim())));
    }
    Ok(())
  }

  fn stop(&self) -> io::Result<()> {
    if !self.loaded()? {
      return Ok(());
    }
    let (code, text) = self.run(bootout(self.uid, &self.label))?;
    if code != 0 && self.loaded()? {
      return Err(io::Error::other(format!("launchctl bootout exited {code}: {}", text.trim())));
    }
    Ok(())
  }

  fn restart(&self) -> io::Result<()> {
    if !self.loaded()? {
      return self.start();
    }
    let (code, text) = self.run(kickstart(self.uid, &self.label))?;
    if code != 0 {
      return Err(io::Error::other(format!("launchctl kickstart exited {code}: {}", text.trim())));
    }
    Ok(())
  }

  fn job(&self) -> io::Result<Job> {
    let (code, text) = self.run(print(self.uid, &self.label))?;
    if code != 0 && code != NOT_LOADED {
      // Any other refusal is said as not loaded too: Home then offers Start, which says what went wrong.
      return Ok(Job::default());
    }
    Ok(read_print(code, &text))
  }

  fn installed(&self) -> bool {
    self.path.is_file()
  }

  fn definition(&self) -> &Path {
    &self.path
  }
}

#[cfg(test)]
mod tests {
  use super::*;
  use std::cell::RefCell;

  const GENERATED: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>tech.nouverse.nouride</string>
  <key>ProgramArguments</key>
  <array>
    <string>/bin/sh</string>
  </array>
  <key>KeepAlive</key>
  <true/>
</dict>
</plist>
"#;

  const LABEL: &str = "tech.nouverse.nouride.home.dev.nouride";

  #[test]
  fn the_generated_plist_takes_the_apps_label_and_nothing_else_changes() {
    let out = relabel(GENERATED, LABEL).unwrap();
    assert!(out.contains(&format!("<key>Label</key>\n  <string>{LABEL}</string>")));
    assert_eq!(out.replace(LABEL, "tech.nouverse.nouride"), GENERATED);
  }

  #[test]
  fn nouride_s_own_label_is_never_written() {
    assert!(relabel(GENERATED, NOURIDE_OWN_LABEL).is_err());
    let twice = GENERATED.replace("<string>/bin/sh</string>", "<string>tech.nouverse.nouride</string>");
    assert!(relabel(&twice, LABEL).is_err());
  }

  #[test]
  fn what_is_not_nourides_plist_is_refused() {
    assert!(relabel("<plist></plist>", LABEL).is_err());
    assert!(relabel(&GENERATED.replace("<key>Label</key>", ""), LABEL).is_err());
  }

  #[test]
  fn the_commands_name_the_apps_label_in_the_persons_session() {
    assert_eq!(bootout(501, LABEL), ["launchctl", "bootout", "gui/501/tech.nouverse.nouride.home.dev.nouride"]);
    assert_eq!(kickstart(501, LABEL), ["launchctl", "kickstart", "-k", "gui/501/tech.nouverse.nouride.home.dev.nouride"]);
    assert_eq!(print(501, LABEL), ["launchctl", "print", "gui/501/tech.nouverse.nouride.home.dev.nouride"]);
    assert_eq!(bootstrap(501, Path::new("/U/x.plist")), ["launchctl", "bootstrap", "gui/501", "/U/x.plist"]);
  }

  #[test]
  fn print_says_whether_it_is_loaded_and_its_pid() {
    let text = "gui/501/x = {\n\tactive count = 1\n\tstate = running\n\tpid = 4242\n\tlast exit code = (never exited)\n}";
    assert_eq!(read_print(0, text), Job { loaded: true, pid: Some(4242), last_exit: None });
    let exited = "gui/501/x = {\n\tstate = not running\n\tlast exit code = 78: EX_CONFIG\n}";
    assert_eq!(read_print(0, exited), Job { loaded: true, pid: None, last_exit: Some(78) });
    assert_eq!(read_print(113, "Could not find service"), Job::default());
  }

  /// A launchctl that remembers what was loaded.
  struct Fake {
    loaded: RefCell<bool>,
    calls: RefCell<Vec<String>>,
  }

  impl Exec for &Fake {
    fn run(&self, argv: &[String]) -> io::Result<(i32, String)> {
      self.calls.borrow_mut().push(argv[1].clone());
      match argv[1].as_str() {
        "print" => Ok(if *self.loaded.borrow() { (0, "state = running\npid = 7".into()) } else { (113, String::new()) }),
        "bootstrap" => {
          *self.loaded.borrow_mut() = true;
          Ok((0, String::new()))
        }
        "bootout" => {
          *self.loaded.borrow_mut() = false;
          Ok((0, String::new()))
        }
        _ => Ok((0, String::new())),
      }
    }
  }

  fn scratch(name: &str) -> PathBuf {
    let dir = std::env::temp_dir().join(format!("service-launchd-{}-{name}", std::process::id()));
    let _ = fs::remove_dir_all(&dir);
    dir
  }

  fn agent<'a>(fake: &'a Fake, name: &str) -> LaunchAgent<&'a Fake> {
    let data = scratch(name).join("Application Support").join("tech.nouverse.nouride.home.dev");
    LaunchAgent::new(fake, 501, LABEL, &data, vec!["/Users/x/Library/Application Support/a b/nouride".into(), "start".into()])
  }

  const SPLIT: &str = r#"<plist><dict><key>Label</key><string>tech.nouverse.nouride</string>
  <key>ProgramArguments</key>
  <array>
    <string>/bin/sh</string>
    <string>-c</string>
    <string>(d=&quot;/Users/x/Library/Application Support/n&quot;; true); exec &quot;$0&quot; &quot;$@&quot;</string>
    <string>/Users/x/Library/Application</string>
    <string>Support/n/nouride</string>
    <string>start</string>
  </array>
</dict></plist>"#;

  #[test]
  fn a_start_command_split_on_its_spaces_is_put_back_as_one_path_and_the_guard_kept() {
    // The label is the first string; then /bin/sh, -c, the guard, the command.
    let program = vec!["/Users/x/Library/Application Support/n/nouride".to_owned(), "start".to_owned()];
    let out = with_program(SPLIT, &program).unwrap();
    let args: Vec<&str> = out.split("<string>").skip(1).map(|e| e.split("</string>").next().unwrap()).collect();
    assert_eq!(
      args[3..],
      [
        "(d=&quot;/Users/x/Library/Application Support/n&quot;; true); exec &quot;$0&quot; &quot;$@&quot;",
        "/Users/x/Library/Application Support/n/nouride",
        "start"
      ]
    );
    assert!(out.ends_with("</array>\n</dict></plist>"));
  }

  #[test]
  fn a_plist_without_the_guard_runs_the_program_alone_with_its_characters_escaped() {
    let plain = "<key>ProgramArguments</key><array><string>/x/nouride</string><string>start</string></array>";
    let out = with_program(plain, &["/a & b/nouride".to_owned(), "start".to_owned()]).unwrap();
    assert!(out.contains("<string>/a &amp; b/nouride</string>\n    <string>start</string>"));
    assert!(!out.contains("/x/nouride"));
  }

  #[test]
  fn installing_writes_the_apps_plist_under_its_own_name_in_its_own_folder_and_loads_it() {
    let fake = Fake { loaded: RefCell::new(false), calls: RefCell::new(vec![]) };
    let agent = agent(&fake, "install");
    agent.install_and_start(GENERATED).unwrap();
    assert!(agent.path.ends_with(format!("Application Support/tech.nouverse.nouride.home.dev/{LABEL}.plist")));
    assert!(!agent.path.to_string_lossy().contains("LaunchAgents"));
    assert!(fs::read_to_string(&agent.path).unwrap().contains(LABEL));
    assert!(agent.job().unwrap().loaded);
    assert_eq!(*fake.calls.borrow(), ["print", "print", "bootstrap", "print"]);
  }

  #[test]
  fn reinstalling_unloads_the_old_one_first_and_stop_and_start_are_idempotent() {
    let fake = Fake { loaded: RefCell::new(true), calls: RefCell::new(vec![]) };
    let agent = agent(&fake, "again");
    agent.install_and_start(GENERATED).unwrap();
    assert_eq!(fake.calls.borrow()[..2], ["print", "bootout"]);
    agent.stop().unwrap();
    agent.stop().unwrap();
    assert!(!agent.job().unwrap().loaded);
    fake.calls.borrow_mut().clear();
    agent.restart().unwrap();
    assert_eq!(*fake.calls.borrow(), ["print", "print", "bootstrap"]);
  }

  #[test]
  fn an_install_from_before_moves_its_plist_out_of_launch_agents_and_the_running_job_is_left_alone() {
    let fake = Fake { loaded: RefCell::new(true), calls: RefCell::new(vec![]) };
    let agent = agent(&fake, "move");
    let launch_agents = agent.path.parent().unwrap().parent().unwrap().parent().unwrap().join("LaunchAgents");
    fs::create_dir_all(&launch_agents).unwrap();
    let old = launch_agents.join(format!("{LABEL}.plist"));
    fs::write(&old, "the plist of 0.2.2").unwrap();
    let own = launch_agents.join(format!("{NOURIDE_OWN_LABEL}.plist"));
    fs::write(&own, "a Nouride installed by hand").unwrap();
    assert!(agent.move_from(&launch_agents).unwrap());
    assert!(!old.exists());
    assert_eq!(fs::read_to_string(&agent.path).unwrap(), "the plist of 0.2.2");
    assert_eq!(fs::read_to_string(&own).unwrap(), "a Nouride installed by hand");
    assert!(fake.calls.borrow().is_empty(), "launchctl was asked: {:?}", fake.calls.borrow());
    assert!(!agent.move_from(&launch_agents).unwrap());
    // One already in the app's folder wins; the old one goes.
    fs::write(&old, "older").unwrap();
    assert!(agent.move_from(&launch_agents).unwrap());
    assert!(!old.exists());
    assert_eq!(fs::read_to_string(&agent.path).unwrap(), "the plist of 0.2.2");
  }

  #[test]
  fn nouride_s_own_plist_is_never_moved() {
    let fake = Fake { loaded: RefCell::new(true), calls: RefCell::new(vec![]) };
    let dir = scratch("own");
    let launch_agents = dir.join("LaunchAgents");
    fs::create_dir_all(&launch_agents).unwrap();
    let own = launch_agents.join(format!("{NOURIDE_OWN_LABEL}.plist"));
    fs::write(&own, "a Nouride installed by hand").unwrap();
    let agent = LaunchAgent::new(&fake, 501, NOURIDE_OWN_LABEL, &dir.join("data"), vec![]);
    assert!(agent.move_from(&launch_agents).is_err());
    assert!(own.is_file());
  }
}

//! When the app starts, stops and asks about this computer's Nouride. The rule is that Nouride runs only while
//! something shows it: on a computer, the app's icon in the menu bar. So opening the app starts Nouride, quitting the
//! app stops it, and the app, not launchd, brings it back at login (`crate::login`).

use shared::Where;

/// What the rules look at.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Now {
  /// `None` before the first question is answered.
  pub place: Option<Where>,
  /// Nouride on this computer, installed by the app: the only one the app starts and stops.
  pub ours: bool,
  /// Its job is loaded in launchd: running, starting, or being started again after a crash.
  pub loaded: bool,
  /// The first setup is running.
  pub installing: bool,
}

impl Now {
  fn here_and_ours(&self) -> bool {
    self.place == Some(Where::Here) && self.ours
  }
}

/// Opening the app (its start, a Dock click, opening it again, Open Nouride, a login) starts Nouride when it was
/// stopped, by Quit or by Stop.
pub fn starts_on_open(now: Now) -> bool {
  now.here_and_ours() && !now.loaded && !now.installing
}

/// Quit asks first only when it stops something: Nouride running here. Otherwise the app goes at once.
pub fn quit_asks(now: Now) -> bool {
  now.here_and_ours() && now.loaded
}

/// Whatever ends the app stops the Nouride it runs, asked or not (a Quit from the Dock, a logout).
pub fn stops_on_exit(now: Now) -> bool {
  now.ours && now.loaded
}

/// The app opens at login while it runs Nouride here.
pub fn opens_at_login(now: Now) -> bool {
  now.here_and_ours()
}

#[cfg(test)]
mod tests {
  use super::*;

  fn now(place: Option<Where>, ours: bool, loaded: bool) -> Now {
    Now { place, ours, loaded, installing: false }
  }

  const HERE: Option<Where> = Some(Where::Here);
  const REMOTE: Option<Where> = Some(Where::Remote);

  #[test]
  fn opening_starts_only_a_stopped_nouride_the_app_installed_here() {
    assert!(starts_on_open(now(HERE, true, false)));
    assert!(!starts_on_open(now(HERE, true, true)));
    assert!(!starts_on_open(now(REMOTE, true, false)));
    assert!(!starts_on_open(now(HERE, false, false)), "a Nouride installed by hand, or none yet");
    assert!(!starts_on_open(now(None, false, false)));
    assert!(!starts_on_open(Now { installing: true, ..now(HERE, true, false) }));
  }

  #[test]
  fn quit_asks_only_when_it_stops_nouride_here() {
    assert!(quit_asks(now(HERE, true, true)));
    assert!(!quit_asks(now(HERE, true, false)));
    assert!(!quit_asks(now(REMOTE, true, false)));
    assert!(!quit_asks(now(HERE, false, false)));
    assert!(!quit_asks(now(None, false, false)));
  }

  #[test]
  fn ending_the_app_stops_what_it_runs_and_never_a_nouride_installed_by_hand() {
    assert!(stops_on_exit(now(HERE, true, true)));
    assert!(stops_on_exit(now(REMOTE, true, true)));
    assert!(!stops_on_exit(now(HERE, true, false)));
    assert!(!stops_on_exit(now(HERE, false, true)));
  }

  #[test]
  fn the_app_opens_at_login_while_it_runs_nouride_here() {
    assert!(opens_at_login(now(HERE, true, false)));
    assert!(opens_at_login(now(HERE, true, true)));
    assert!(!opens_at_login(now(REMOTE, true, false)));
    assert!(!opens_at_login(now(HERE, false, true)));
    assert!(!opens_at_login(now(None, false, false)));
  }
}

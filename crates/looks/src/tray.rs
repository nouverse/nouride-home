//! The menu bar's menu, top to bottom: the state in one line, the update notice when one waits, Open Nouride, Start or
//! Stop (Nouride the app installed, on this computer), Quit. In the Dev build the state line says Dev first, as its
//! icon carries a mark: both builds can sit in the menu bar at once. Nouride Home's own window has no item: Open Nouride
//! shows it only when there is no dashboard to show (`open_target`), and the dashboard's "Back to Nouride Home" leads
//! to it.

use serde::Serialize;
use shared::{Phase, Where};

use crate::{notices, status, Look};

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct Item {
  /// What the app does when it is pressed.
  pub id: &'static str,
  /// The key of its words.
  pub words: String,
  pub enabled: bool,
  /// The key of words put in place of `{state}` in this item's words.
  #[serde(skip_serializing_if = "Option::is_none")]
  pub state: Option<String>,
}

/// What the menu is drawn from.
#[derive(Debug, Clone, Copy)]
pub struct Input<'a> {
  /// `None` before the first question is answered.
  pub place: Option<Where>,
  pub look: &'a Look,
  /// Nouride on this computer, installed by the app: the only one the app starts and stops.
  pub ours: bool,
  pub phase: Phase,
  pub app_update: bool,
  pub nouride_update: bool,
  /// The Dev build.
  pub dev: bool,
}

pub const STATE: &str = "state";
/// The Dev build's state line: "Dev · {state}".
pub const DEV_STATE: &str = "tray_dev_state";
pub const UPDATES: &str = "updates";
pub const OPEN: &str = "open";
pub const START: &str = "start";
pub const STOP: &str = "stop";
pub const QUIT: &str = "quit";
/// Quit's words, in the menu bar and in the app's menu at the top of the screen.
pub const QUIT_WORDS: &str = "tray_quit";

/// The window Open Nouride shows. The same answer for every way the app is opened: the menu bar, the Dock, opening
/// the app again, the app's start.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
pub enum Target {
  /// Nouride's dashboard, in its window.
  Dashboard,
  /// Nouride Home's window: before the first question is answered, and while there is nothing to reach (Nouride off,
  /// starting, failed, or the other computer not answering), since Home is where that is said and Start is.
  Home,
}

pub fn open_target(place: Option<Where>, look: &Look) -> Target {
  if place.is_some() && status::can_share(look) {
    Target::Dashboard
  } else {
    Target::Home
  }
}

fn item(id: &'static str, words: impl Into<String>, enabled: bool) -> Item {
  Item { id, words: words.into(), enabled, state: None }
}

pub fn menu(input: Input) -> Vec<Item> {
  let mut out = vec![];
  let state = if input.place.is_none() { "tray_not_set_up".to_owned() } else { input.look.words.title() };
  if input.dev {
    out.push(Item { state: Some(state), ..item(STATE, DEV_STATE, false) });
  } else {
    out.push(item(STATE, state, false));
  }
  if let Some(notice) = notices::tray(input.app_update, input.nouride_update) {
    out.push(item(UPDATES, notice, true));
  }
  out.push(item(OPEN, "tray_open", true));
  if input.place == Some(Where::Here) && input.ours {
    if status::can_turn_off(input.phase) {
      out.push(item(STOP, "tray_stop", true));
    } else if matches!(input.phase, Phase::Stopped | Phase::Failed) {
      out.push(item(START, "tray_start", true));
    }
  }
  out.push(item(QUIT, QUIT_WORDS, true));
  out
}

#[cfg(test)]
mod tests {
  use super::*;
  use shared::Reply;

  fn ids(items: &[Item]) -> Vec<&'static str> {
    items.iter().map(|i| i.id).collect()
  }

  fn input(place: Option<Where>, look: &Look, ours: bool, phase: Phase) -> Input<'_> {
    Input { place, look, ours, phase, app_update: false, nouride_update: false, dev: false }
  }

  #[test]
  #[test]
  fn open_shows_the_dashboard_only_when_there_is_one_to_reach() {
    let running = status::of(Phase::Running, true);
    assert_eq!(open_target(Some(Where::Here), &running), Target::Dashboard);
    assert_eq!(open_target(Some(Where::Remote), &status::remote(Some(Reply::Answers))), Target::Dashboard);
    assert_eq!(open_target(Some(Where::Here), &status::adopted(true)), Target::Dashboard);
    assert_eq!(open_target(None, &running), Target::Home);
    for phase in [Phase::Stopped, Phase::Installing, Phase::Starting, Phase::Failed] {
      assert_eq!(open_target(Some(Where::Here), &status::of(phase, false)), Target::Home, "{phase:?}");
    }
    assert_eq!(open_target(Some(Where::Remote), &status::remote(Some(Reply::Down))), Target::Home);
    assert_eq!(open_target(Some(Where::Here), &status::setup_failed()), Target::Home);
  }

  #[test]
  fn running_here_offers_open_and_stop_with_the_state_first_and_quit_last() {
    let look = status::of(Phase::Running, true);
    let items = menu(input(Some(Where::Here), &look, true, Phase::Running));
    assert_eq!(ids(&items), [STATE, OPEN, STOP, QUIT]);
    assert_eq!(items[0], item(STATE, "running_title", false));
  }

  #[test]
  fn off_here_offers_start_and_open() {
    let look = status::of(Phase::Stopped, false);
    assert_eq!(ids(&menu(input(Some(Where::Here), &look, true, Phase::Stopped))), [STATE, OPEN, START, QUIT]);
  }

  #[test]
  fn only_open_for_nouride_while_it_moves() {
    let look = status::of(Phase::Installing, false);
    assert_eq!(ids(&menu(input(Some(Where::Here), &look, true, Phase::Installing))), [STATE, OPEN, QUIT]);
  }

  #[test]
  fn another_computer_or_a_nouride_not_the_apps_is_never_started_or_turned_off_from_here() {
    let look = status::remote(Some(Reply::Answers));
    assert_eq!(ids(&menu(input(Some(Where::Remote), &look, true, Phase::Running))), [STATE, OPEN, QUIT]);
    let adopted = status::adopted(true);
    assert_eq!(ids(&menu(input(Some(Where::Here), &adopted, false, Phase::Running))), [STATE, OPEN, QUIT]);
  }

  #[test]
  fn before_the_question_it_says_so_and_offers_open() {
    let look = status::of(Phase::Stopped, false);
    let items = menu(input(None, &look, false, Phase::Stopped));
    assert_eq!(ids(&items), [STATE, OPEN, QUIT]);
    assert_eq!(items[0].words, "tray_not_set_up");
  }

  #[test]
  fn the_dev_build_says_so_on_the_state_line_and_nothing_else_changes() {
    let look = status::of(Phase::Running, true);
    let release = menu(input(Some(Where::Here), &look, true, Phase::Running));
    let dev = menu(Input { dev: true, ..input(Some(Where::Here), &look, true, Phase::Running) });
    assert_eq!(dev[0], Item { id: STATE, words: DEV_STATE.to_owned(), enabled: false, state: Some("running_title".to_owned()) });
    assert_eq!(release[0].state, None);
    assert_eq!(dev[1..], release[1..]);
  }

  #[test]
  fn an_update_waiting_is_the_second_line() {
    let look = status::of(Phase::Running, true);
    let items = menu(Input { nouride_update: true, ..input(Some(Where::Here), &look, true, Phase::Running) });
    assert_eq!(items[1], item(UPDATES, "tray_nouride_update", true));
  }
}

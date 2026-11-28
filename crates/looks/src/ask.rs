//! The questions the menu bar asks before it stops Nouride, in the system's own alert (`questions.rs` in the app):
//! confirmations of something the person pressed, as a modal may be. Settings asks Stop's in its page, in the same
//! words.

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Question {
  pub title: &'static str,
  pub body: &'static str,
  /// Does what was pressed.
  pub yes: &'static str,
  /// Changes nothing.
  pub no: &'static str,
}

/// Quit Nouride, while Nouride runs here: the app goes and Nouride stops with it.
pub const QUIT: Question = Question { title: "quit_title", body: "quit_body", yes: "quit", no: "cancel" };

/// Stop Nouride server: Nouride stops, the app stays in the menu bar.
pub const STOP: Question = Question { title: "stop_title", body: "stop_body", yes: "stop", no: "cancel" };

pub const ALL: [Question; 2] = [QUIT, STOP];

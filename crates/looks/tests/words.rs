//! Every key the looks name has its words in `packages/ui-kit/src/words.json`, and every placeholder in them is one
//! the screens fill: a state cannot ship without its sentence, nor a sentence with a `{…}` left in it.

use looks::{ask, leave, notices, tray, versions, window, Words};
use serde_json::Value;
use shared::{Phase, Reply, Where};

fn words() -> serde_json::Map<String, Value> {
  let text = include_str!("../../../packages/ui-kit/src/words.json");
  match serde_json::from_str(text).expect("words.json is JSON") {
    Value::Object(map) => map,
    _ => panic!("words.json is an object"),
  }
}

fn has(map: &serde_json::Map<String, Value>, key: &str) {
  assert!(map.get(key).and_then(Value::as_str).is_some_and(|s| !s.is_empty()), "no words for {key}");
}

#[test]
fn every_state_has_a_title_and_a_body() {
  let map = words();
  for w in Words::ALL {
    has(&map, &format!("{}_title", w.key()));
    has(&map, &format!("{}_body", w.key()));
  }
}

#[test]
fn every_version_card_sentence_and_label_has_words() {
  let map = words();
  for look in versions::all() {
    has(&map, look.words);
    if let Some(label) = look.label {
      has(&map, label);
    }
  }
}

#[test]
fn every_menu_item_and_notice_has_words() {
  let map = words();
  for (app, nouride) in [(false, false), (true, false), (false, true), (true, true)] {
    if let Some(k) = notices::tray(app, nouride) {
      has(&map, k);
    }
    if let Some(k) = notices::versions_row(app, nouride) {
      has(&map, k);
    }
  }
  for phase in [Phase::Stopped, Phase::Running, Phase::Failed] {
    for place in [None, Some(Where::Here), Some(Where::Remote)] {
      for dev in [false, true] {
        let look = looks::status::of(phase, true);
        let input = tray::Input { place, look: &look, ours: true, phase, app_update: true, nouride_update: true, dev };
        for item in tray::menu(input) {
          has(&map, &item.words);
          if let Some(state) = &item.state {
            has(&map, state);
            assert!(map[&item.words].as_str().unwrap().contains("{state}"), "{}", item.words);
          }
        }
      }
    }
  }
  let _ = looks::status::remote(Some(Reply::Down));
}

#[test]
fn the_question_before_the_dashboard_closes_has_its_words_and_its_two_answers() {
  let map = words();
  for k in [leave::TITLE, leave::BODY, leave::GO_BACK, leave::STAY] {
    has(&map, k);
  }
  assert_ne!(map[leave::GO_BACK], map[leave::STAY]);
  assert!(map[leave::TITLE].as_str().unwrap().ends_with('?'));
}

#[test]
fn the_questions_before_nouride_stops_have_their_words_and_two_answers() {
  let map = words();
  for q in ask::ALL {
    for k in [q.title, q.body, q.yes, q.no] {
      has(&map, k);
    }
    assert_ne!(map[q.yes], map[q.no]);
    assert!(map[q.title].as_str().unwrap().ends_with('?'), "{}", q.title);
  }
  assert_eq!(map[ask::QUIT.title], "Quit Nouride?");
  assert_eq!(map[ask::QUIT.body], "Your agents stop answering until you open Nouride again.");
  assert_eq!(map[ask::STOP.title], "Stop Nouride server?");
  assert_eq!(map[ask::STOP.body], "Your agents stop answering until you start it again.");
  assert_eq!((map[ask::STOP.yes].as_str(), map[ask::STOP.no].as_str()), (Some("Stop"), Some("Cancel")));
}

#[test]
fn the_app_menus_settings_has_its_words() {
  has(&words(), window::MENU_SETTINGS);
}

#[test]
fn only_the_placeholders_the_screens_fill_are_in_the_words() {
  let filled = ["{version}", "{percent}", "{step}", "{steps}", "{app}", "{nouride}", "{name}", "{state}"];
  for (key, value) in words() {
    let Some(text) = value.as_str() else { continue };
    let mut rest = filled.iter().fold(text.to_owned(), |t, p| t.replace(p, ""));
    rest.retain(|c| c == '{' || c == '}');
    assert!(rest.is_empty(), "{key}: {text}");
  }
}

/// Android's words (`res/values/strings.xml`, and the TV's), as name and text, with Android's escaped quotes undone.
fn android(folder: &str) -> Vec<(String, String)> {
  let path = format!("{}/../../apps/android/app/src/main/res/{folder}/strings.xml", env!("CARGO_MANIFEST_DIR"));
  let text = std::fs::read_to_string(&path).expect("Android's strings.xml");
  text
    .split("<string name=\"")
    .skip(1)
    .map(|rest| {
      let (name, rest) = rest.split_once("\">").expect("a string's name");
      let value = rest.split("</string>").next().unwrap_or("").replace("\\'", "'");
      (name.to_owned(), value)
    })
    .collect()
}

#[test]
fn both_apps_stop_and_start_the_nouride_server_in_the_same_words_and_quit_says_what_it_quits() {
  let map = words();
  let phone: std::collections::HashMap<_, _> = android("values").into_iter().collect();
  for key in ["stop_server", "stop_title", "stop_body", "stop", "stopping_title", "versions_nouride_off", "dashboard_unreachable_body"] {
    assert_eq!(map[key].as_str(), phone.get(key).map(String::as_str), "{key}");
  }
  assert_eq!(map["stop_server"], "Stop Nouride server");
  assert_eq!(map["tray_stop"], map["stop_server"]);
  assert_eq!(map["tray_start"], "Start Nouride server");
  assert_eq!(map["tray_quit"], "Quit Nouride");
  for k in ["quit_title", "quit_body", "quit"] {
    has(&map, k);
  }
  let mut all: Vec<(String, String)> = map.iter().filter_map(|(k, v)| Some((k.clone(), v.as_str()?.to_owned()))).collect();
  all.extend(android("values"));
  all.extend(android("values-television"));
  // "Turn off" is the switch for other devices now, never Nouride itself.
  let old = ["Turn off Nouride", "Turn Nouride off", "Turning Nouride off", "turns off. Its agents", "Quit Nouride Home"];
  let left: Vec<_> = all.iter().filter(|(_, v)| old.iter().any(|o| v.contains(o)) || v == "Turn off").collect();
  assert!(left.is_empty(), "{left:?}");
}

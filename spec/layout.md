# The app's screen at each width: sidebar, sections and columns

Both apps show the same sections, and on a wide screen in the same shape: the desktop's window and an Android tablet,
TV or phone turned sideways. What decides the shape is the width of the window the app has now, which changes as a
desktop window is resized, a phone is turned, or Android splits the screen. Cases: `fixtures/layout.json`.

## The sections

In the sidebar's order, which is also the desktop's Cmd+1..5:

1. **Home**: how Nouride is, and its one button.
2. **Connect**: Nouride on another device.
3. **Versions and updates**: the app's version and Nouride's, each with its update.
4. **Settings**: where Nouride runs, keeping it running, the appearance, turning it off.
5. **Technical Logs**, set apart at the bottom, for whoever helps.

They are siblings: none opens inside another. The open one sits in the soft lavender pill. A dot on Versions and
updates says an update waits there; on Android, a dot on Settings says the phone may stop Nouride with the screen off,
as it is fixed there.

On a phone's narrow screen Android folds the last three into **More** (`Tab.MORE`): Settings' cards with Versions and
updates and Technical Logs as rows that open a screen of their own, and the dot on More for either reason.

## The shapes

`width` is the window's width: CSS px on the desktop, dp on Android (`Configuration.screenWidthDp`).

| Width | Sidebar | Pages |
| --- | --- | --- |
| under `sidebar_from` (720) | Android: none, the bottom bar (Home, Connect, More). Desktop: a rail of icons | one column |
| `sidebar_from` (720) to under `two_columns_from` (960) | the sidebar with labels | one column |
| `two_columns_from` (960) and up | the sidebar with labels | two columns where a page has them: Home's state card with what stands beside it, Connect's code beside the switch and the address, Versions' two cards side by side |

There is no tab bar on the desktop at any width, and no icon rail on Android: under 720 the phone is a phone.

No sidebar (nor bottom bar) before the first question, where Nouride should run, is answered, nor while the first
setup runs: the question or the setup's progress stands alone in the middle.

Each page keeps a readable width, centred, on a wide screen: `readable.page` (960), Settings `readable.settings`
(720); Technical Logs takes the whole content area. Nothing is cut, overlaps or scrolls sideways at any width (but a
log line, inside Technical Logs' own box).

## Back

The sections are siblings, so Back from any of them goes to Home, and from Home it leaves the app, as the phone's bar
does (`Tabs.back`). On a TV's remote, Left and Right move between the sidebar and the page, and the sidebar is entered
at the open section.

## Where the apps differ

- **Two columns at 960 to about 965.** Android takes two columns from the window's width, 960 dp. The desktop's pages
  follow their own width (`@container`, `@min-[680px]:`), which with the labelled sidebar (228 px and its hairline) and
  the page's padding (28 px each side) is a window of 965 px; the case at 960 says `desktop` one column.
- **Under 720.** Android is the phone (a bottom bar); the desktop is still a desktop window, with an icon rail.

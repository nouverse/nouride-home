# The desktop's layout on wide Android screens: a Redmi Note 12 and a Google TV Streamer

Run on 2026-11-25 (Asia/Jakarta), on branch `feat/wide-layout` (PR #19), with the debug build, Nouride Home Dev
(`tech.nouverse.nouride.home.dev`).

## The question

From 720 dp, does the Android app show the desktop app's shape (the sidebar, its sections, readable widths, two
columns from 960 dp) without anything cut, overlapping or scrolling sideways, and can a remote's D-pad use it? Under
720 dp, does the phone look exactly as it did?

## The widths tried

| Device and setting | Window width | Shape (`Sections.shape`) |
| --- | --- | --- |
| Note 12 portrait, 440 dpi | 393 dp | phone, the bottom bar |
| Note 12 sideways, 440 dpi | 873 dp | the sidebar, one column |
| Note 12 sideways, `wm density 300` | 1280 dp | the sidebar, two columns |
| Note 12 portrait, `wm density 300` | 576 dp | phone, the bottom bar |
| Google TV Streamer, 1920 × 1080 at 320 dpi | 960 dp (540 dp tall) | the sidebar, two columns |

The unit tests cover every width in `spec/fixtures/layout.json` (360 to 1920) without a screen.

## Redmi Note 12 (Android 15, HyperOS 2.0)

**Portrait is unchanged.** The same screens, in the same state of Nouride, were shot with a build of `main` (8ae0ef4)
and with the branch, and compared pixel by pixel below the status bar (`evidence/compare.py`,
`evidence/portrait-compare.txt`): Home, Connect and More were identical with the first build of the branch. With its
final build, Connect, More and the Versions and updates screen differ only in the system's gesture handle under the
bar, which Android tints by what is behind it; Home differs only by a focus ring that a key press left on main's run;
Technical Logs' frame (back bar, Copy, Share) is identical, its words being Nouride's of the moment.

**Sideways, 873 dp: the sidebar, one column.** Home, Connect, Versions and updates, Settings and Technical Logs from the
sidebar; a dot on Versions while a new Nouride waited, on Settings while the phone may stop Nouride
(`evidence/pictures/note12-873dp-*.png`). The first build cut two things on this short screen (about 340 dp tall):
Technical Logs below the sidebar's fold, and the log itself on the Technical Logs page, squeezed to nothing under the
summary. Fixed before the PR: the sidebar scrolls and is a little tighter (Technical Logs then fits whole), and on a
wide screen the summary scrolls in the log's own box.

**Sideways, 1280 dp (`wm density 300`): two columns.** Home's state card with the update card and the battery line
beside it; Connect's code on the left with the switch over the address beside it (and, while Nouride was off, the cat
with the switch beside it); the two version cards side by side; Settings alone at 720 dp, centred
(`evidence/pictures/note12-1280dp-*.png`).

**The D-pad**, with key events (which leave touch mode as a remote does), at 1280 dp (`evidence/dpad-note12.txt`): the
first key lands on the open section; Left from a button level with Settings went to Home, the open section, not the
nearest item; Down past Technical Logs stays there; Center opens a section and Right enters its page; Back opens Home
and focus follows to its item.

To wake the phone that night, HyperOS's pocket mode (its proximity sensor covered) had to be left with Volume Up, once.
Rotation and density were put back as they were (auto-rotate on, 440 dpi), and Home Dev's own Nouride was left running.
After the TV's fixes the final build was checked on the phone again: portrait as main, sideways as before.

## Google TV Streamer (Android TV 14)

Reached at 07:38, once its wireless debugging was on again, at a new address (DHCP had moved it). The release app
(`tech.nouverse.nouride.home` 0.2.2, the owner's Nouride) was left alone: not opened, updated or stopped; its Nouride
answered on its port at the end. Home Dev was installed beside it (no Play Protect question this time), never asked to
run Nouride on the TV, and uninstalled at the end.

**The first question** stands alone in the middle, no sidebar, the card 600 dp wide, within the 540 dp height
(`evidence/pictures/streamer-first-question.png`). **Another computer**: a throwaway Nouride on the dev box behind a
quick tunnel, its address typed with the TV's keyboard. Home then showed **the sidebar and two columns**, as at 960 dp
it should (`evidence/pictures/streamer-960dp-*.png`, the address and its code blurred): Home's card alone in the middle
(on a TV no update or battery line stands beside it), Connect's code beside the address, Versions' one card (Nouride's
own is not shown for another computer), Settings at 720 dp, Technical Logs filling the page.

The first build on the TV showed four faults, all fixed with tests before the merge:

| Seen on the TV | Fix |
| --- | --- |
| Home's card ran past the bottom of the 540 dp screen | the cat 150 dp on a wide screen (220 on a phone) |
| Down from Connect's last button went to Technical Logs in the sidebar; Down from Settings stopped, as Android's search preferred a button in the page | Up and Down look only on their own side (`SidebarFocus`) |
| Opening Technical Logs moved the remote's focus into the log | the log scrolls to its end without `fullScroll` |
| Back opened Home but left the remote on Versions' item | the remote follows to the open section |

After them, the D-pad walk (`evidence/dpad-streamer.txt`): focus starts on the open section; Down along the sidebar
reaches Technical Logs and stops; Right enters the page; Left comes back to the open section; Back opens Home, then
leaves the app. The ring follows the pill's corners.

## What was not tested

- A tablet, or Android's split screen at a width between 720 and 960 dp on a real device (the shape is read the same
  way, from `Configuration.screenWidthDp`, and a size change makes the screen again).
- The dark theme at the new widths (the colours are the existing ones: no new colour pair).
- The dashboard (Nouride's web UI inside the app): out of scope, unchanged.

## Evidence

- `evidence/pictures/`: the app's own screens, the status bar cut off, at half size.
- `evidence/portrait-compare.txt`, `evidence/compare.py`: the portrait comparison against `main`.
- `evidence/dpad-note12.txt`, `evidence/dpad-streamer.txt`, `evidence/focus.sh`: the D-pad walks.
- `evidence/shots.sh`, `evidence/round.sh`, `evidence/versions-shot.sh`: how the screens were shot.

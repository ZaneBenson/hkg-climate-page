# Version 1.2: design (2026-10-31)

Follows the owner's first car run of 1.1.0 (capture `private/CAPTURE/1`, 12:05-12:08; analysis in
`private/work/climate-research/capture-3-analysis.md`) and a panel design session (final mockup:
`docs/superpowers/specs/assets/2026-10-31-panel-v1-2.html`, a fragment styled for the brainstorm viewer;
open it in a browser). Target and rules unchanged: 2023 US Ioniq 5,
`NEEV23.USA.`, firmware 260507; `CLAUDE.md` applies. Builds on
`2026-10-31-climate-presets-design.md`.

## Intent

Make presets land reliably, stop the app crashing on Save, make the editor usable on the head
unit, let a preset set each side's temperature, show which preset is in effect, and give the
panel a cleaner look the owner designed.

## What the car run showed (the fixes)

1. **Dropped temperature presses.** The runner sent the next temperature press ~100 ms after the
   car reported the previous one. The car acknowledged the second driver press but never applied
   it (3 of 3). The stock screen's presses were 450-530 ms apart and all landed. Passenger and
   fan presses at 100 ms spacing landed but queued (car answers spread to +100 ms).
2. **Crash on Save.** `Activity.finish()` in the preset editor throws `SecurityException`
   (`getTasks() requires android.permission.GET_TASKS`) on this head unit's framework. The preset
   was already written, which is why 1.0's "could not save" appeared although it saved; in 1.1 the
   throw crashes the app and the climate service (4 of 4 saves).
3. **Dropdowns not scrollable.** A Spinner's drop-down list is drawn below the field and runs off
   the bottom of the screen without scrolling (temperature showed 62-67 only).
4. **Upgrade refused.** NEMESIS (current source, `usb.c do_install`) refuses a package whose id is
   installed while Android is up ("Uninstall … before installing the package on the USB drive");
   with the stick in at boot (Android down) it runs its built-in update (old preuninstall, then
   new preinstall/postinstall), which our hooks already handle correctly. Nothing in our package
   can change this.

## Goals

- **Reliable steps:** at least 400 ms between any two commands of a preset run (re-read at the end
  of the gap, then decide). For temperature and fan steps only: if the value doesn't change
  within 500 ms of a press, re-read and press again, at most 2 retries per step, counted toward
  the item's press cap (owner approved, 2026-10-31). Never a retry for single-press items
  (climate off, auto, A/C, airflow, sync, wheel, eco).
- **Save never crashes:** the app holds `android.permission.GET_TASKS`, and `finish()` is guarded
  so a throw there can never take down the app.
- **Scrollable choices:** every editor dropdown opens as a centred dialog list sized to the screen
  (Spinner dialog mode), so every value is reachable.
- **Driver and passenger temperatures:** a preset has a driver temperature and a passenger
  temperature (each "leave as is" or 62-82 °F). When the preset's Sync is "on", the passenger
  temperature is greyed (the passenger follows the driver).
- **Applied-preset highlight:** the last preset tapped is highlighted on the page while it is being
  applied and for as long as the car still matches every item it sets; the highlight clears for
  good the first time anything drifts (or another preset is tapped, or the app restarts). Only
  that preset, never one that happens to match.
- **Up to six presets**, and the page draws only saved ones.
- **Panel redesign** (below).
- **Presets and settings survive updates** (owner, 2026-10-31): NEMESIS's update uninstalls the
  old app, which wipes its settings. Our preuninstall hook copies the app's settings file
  (`shared_prefs/hkgclimate.xml`: presets, switch temperature, page on/off) to
  `/data/nemesis/hkg.climate.page.settings.xml`; our postinstall hook puts it back, with the app's
  owner and mode, only when the freshly installed app has no settings file yet. The copy stays in
  the NEMESIS folder after a real uninstall (a small leftover; a later install restores it). The
  hooks keep the head unit's shell rules (no printf/tail/head/seq/`[[ ]]`) and log what they did;
  a failed copy or restore never fails the install. Nothing leaves the car.
- **Upgrade instructions:** README and checklists say: to update, plug the new stick in and restart
  the head unit with it in (or uninstall, restart, install).

## The panel (owner's design, `assets/2026-10-31-panel-v1-2.html`)

- **Top line:** outside temperature only (22 sp). Settings becomes a 44 × 44 dp outlined square
  with a gear icon (26 dp) in the text colour; the top line is as wide as the button grid
  (384 dp), so the gear's right edge lines up with the right-hand buttons.
- **Seat buttons** (180 × 180 dp, 24 dp apart, border colour as now: orange = heat mode, blue =
  cool mode, grey = unavailable):
  - A side-view seat icon (56 dp tall, filled, text colour; cushion and backrest slightly bulged),
    at the outer side (left for the driver, right for the passenger, the passenger's mirrored),
    its outer edge aligned with the outer level bar. The icon shows the button's **mode**: heat
    mode → seat with three rising wavy arrows; cool mode → seat with a four-blade fan;
    unavailable → plain seat. Whether the seat is on is shown by the bars.
  - The three level bars (36 × 9 dp) as now. **No state text** under them, and no "Driver" /
    "Passenger" names (position tells the seat).
  - A round 52 dp sub-button (2 dp outline, text colour) near the top inner corner, 14 dp from the
    top and inner edges: **swap** (two arrows in a circle) while the seat is off, **×** while the
    seat is heating or cooling.
  - A tap elsewhere on the button steps the seat as today.
- **Preset slots:** 180 × 50 dp, two per row, 24 dp apart, rows 8 dp apart, up to three rows; only
  saved presets are drawn (no placeholders). The highlighted preset has a 4 dp border and a bold
  name in a brighter text colour (white at night, #1F2A30 by day) and a light grey fill (the text
  colour at ~18 % opacity).
- Icons are drawn in code (the page has no resources of its own), in the day/night text colour.

## How the new controls behave

- **Swap** (seat off): flips the seat's mode for the next tap (heat ↔ cool) — border and icon
  follow. Sends nothing. *Designer's call:* the flipped mode holds while the seat stays off;
  once the seat is turned on its mode is the mode it runs in, and when it goes off again the
  mode returns to the automatic choice (switch temperature). Swap is not offered on a seat
  without the heat-and-cool option (heat only).
- **×** (seat on): one command setting the seat to off (value 2, approved). Same gating as a seat
  tap (seats ready, request id once, nothing while a preset runs).
- **Highlight:** the app keeps the index of the last tapped preset and whether it is "in effect".
  It is in effect from the tap; while the preset runs; and afterwards while every item the preset
  sets reads at its target. *Designer's call:* after the run, the app re-reads the cabin, wheel
  and eco state every 2 s while the page is shown, and uses seat state from the existing seat
  listener; the first read where any set item differs (or is unknown) clears it.

## Editor

- Dropdowns open as dialogs (above). Items, in order: driver seat, passenger seat, steering wheel,
  eco, climate, driver temperature, passenger temperature, fan, A/C, airflow, sync.
- Greying: as in 1.1 (Auto greys fan/A/C/airflow; Off greys every cabin item), plus: Sync "on"
  greys the passenger temperature.
- Six presets in Settings (Add disabled at six).

## Settings app and editor fit beside the side panel (owner, 2026-10-31)

The settings screen and the preset editor are laid out by default for the area left of the
right-hand split panel, so they're usable with the panel open:

- The screen is 1920 × 720 px at density 240 (1280 × 480 dp); the right panel is 640 px (427 dp)
  wide, leaving 1280 px (853 dp) on the left.
- All content sits in one column no wider than 800 dp, aligned to the left edge with 24 dp
  padding (*designer's call* on the numbers), so nothing is ever under the panel, whether or
  not it is shown. Rows that are side by side today (labels and buttons, the preset list's Edit /
  Delete / Up / Down) wrap or shrink to fit the column.
- Dialogs (the dropdown lists, the delete confirmation) are no wider than the column and appear
  within the left area.

## Applying a preset (changes from 1.1)

- Order as 1.1, with "temperature" split: driver steps, then passenger steps.
- Passenger: if Sync is on after the sync step, the passenger isn't stepped (it follows); if the
  car is synced and the preset's Sync is "leave as is" and the passenger target differs, the
  passenger is skipped and named in the result ("sync on"). *Designer's call* (stepping the
  passenger would turn sync off without the preset asking).
- Spacing and retries as in Goals.

## Storage

- Presets: up to 6; a new line format adds the passenger temperature. Old lines (3 and 11 fields)
  still load; an 11-field line's single temperature becomes both sides.

## Non-goals

- A measured inside temperature (the car doesn't report one to the head unit; the stock "inner
  temperature" is the driver set point).
- Showing the cabin set temperature on the page (owner: redundant).
- In-place upgrade with Android up (NEMESIS behaviour; possible later in NEMESIS itself).
- Defrost, recirculation, mirror heat (no head-unit command).
- Deciding how a Manual preset behaves when tapped with the climate off: still untested on the
  car (1.1 checklist step 7 wasn't run); carried to this version's checklist.

## Constraints

- Commands: only those already approved (seats 2-8; the climate flag/value list; wheel press;
  eco 2/1). The × sends seat value 2 (approved). Swap sends nothing.
- The panel changes, so the page dex, the panel delta and `package/patch/*` are rebuilt with the
  existing pipeline (`tools/build-panel.sh`, stock from `private/stock/260507`).
- Page safety unchanged: every page entry point catches Throwable; the page references no
  `com.hkmc` code; new page → app calls are oneway with the existing uid checks.
- Version 1.2.0, versionCode 6.

## Calls made for the builder (designer's calls, 2026-10-31)

- **Spacing covers the runner's commands** (cabin, wheel, eco): seat commands go to the seat
  service and none dropped, so they stay as today (sent on the tap); the runner's first command
  goes out at least 400 ms after the tap's seat sends, and every later runner command at least
  400 ms after the previous one.
- **Retries never apply to a forced press** (the one press Manual sends to leave auto): only an
  ordinary temperature or fan step is retried.
- **What "drift" means for the highlight:** an item the run left alone by design (passenger
  following sync or driver-only, a cool target on a heat-only seat) or reported as not done does
  not count; drift is an item that reached its target and later reads differently. The highlight
  also shows when the tap needed no commands (the car already matched). Seat state is given 3 s
  after the tap (the same window as the seat send check) before it can count as drift.
  Editing, deleting or reordering presets clears the highlight.
- **If `finish()` still throws after a successful save:** the editor's message line says
  "Saved" (and the log has the error); the owner leaves with Back. No crash.
- **Passenger temperature with Sync "on"** is stored as "leave as is"; an old 11-field line with
  Sync "on" loads with only the driver temperature.

## Left to the builder

- Exact retry/spacing implementation and timeouts beyond the numbers above.
- The PageLink keys and transactions for the highlight, swap and × (oneway, same uid rules).
- How the gear, seat, arrows, fan, swap and × shapes are drawn (Path drawing in code), as long as
  they match the mockup.

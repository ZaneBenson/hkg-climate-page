# hkg-climate-page: design

Status: draft, 2026-10-30. Owner: the contributor who requested it (2023 US Ioniq 5 with
ventilated front seats, NEMESIS installed). Grew out of research first done alongside
HMG Leafia Plus; this is a separate NEMESIS package with its own repo.

## Intent

On the Gen5W head unit, turning seat heat or cooling on while driving means switching the
car's touch strip to climate mode or going a few screens deep, and setting up the cabin for
a cold or hot morning means many separate taps. The owner wants:

- a **climate page** on the right-side split screen, always one swipe away, with one-tap
  seat buttons and the outside temperature;
- **presets**: named buttons on that page that apply several climate settings at once
  ("Cold", "Hot");
- a **settings app** to configure the seat buttons and build the presets.

## What it does

### 1. The climate page (right-side split screen)

- A new page in the split screen's page list, reached by swiping up/down like the stock
  Clock and Energy pages. Approved layout (mockup "B"):
  - top line: the outside temperature ("Outside 48 °F"); *(owner's change, 2026-10-30: no
    "taps will heat" text)*;
  - two big seat buttons, driver left and passenger right: seat icon, three level bars,
    orange = heating, blue = cooling, grey = off;
  - a row of preset buttons, each labelled with the preset's short name. *(My call: up to
    four on the page; more presets are allowed in the settings app but only the first four
    in its order are shown.)*
  - a way to open the settings app from the page. *(My call: a small gear button.)*
- **Version 1 layout (owner's choice, 2026-10-30, mockup "B"):** the seat buttons at their
  final size in the upper part; below them, four greyed-out preset slots (two rows of two)
  until presets arrive in version 2. Each seat button also shows its state in words
  ("Driver · heat mid"). **Owner's changes, 2026-10-30:** the top line is only the outside
  temperature; a seat button's border says what the next tap does (orange = heats, blue =
  cools, grey = unavailable); no test strip and no approval switch (taps send the approved
  command); seat buttons 180 dp square, preset slots 180 × 50 dp, 24 dp gaps.
- **Seat button:** each tap steps off -> high -> mid -> low -> off (the stock order). Below
  the switch temperature it heats, at or above it cools. The mode is chosen when the seat
  goes from off to on; while on, taps keep that mode until it returns to off (no flapping).
  If the seat is already on in the other mode (set elsewhere), a tap steps within that mode.
  *(My call.)*
- **The page always shows the real state**, including changes made with the touch strip,
  the stock climate screen, remote start or presets.
- Edge cases *(my calls)*: seats without ventilation (option value other than the
  heat+vent value, see Findings) only heat; outside temperature unavailable -> heat, and the
  top line says the temperature is unknown; seat service not ready -> buttons greyed out.

### 2. The settings app

Our own app, opening in the main (left) part of the screen, reachable from the app list and
from the page's gear button. It lets the owner:

- set the **switch temperature** (default 60 °F; shown in the unit the car displays, °F or °C);
- **create, edit, reorder and delete presets.** A preset is a short name (*my call: up to 10
  characters*, so it fits a button) plus any number of settings from the catalogue below,
  each with a value. Settings not in a preset are left alone when it is applied.
- *(My call, optional)* "copy current": fill a new preset from the car's current values of the
  available settings, to edit and save.

Only settings that have passed the command process below appear in the preset editor.

### 3. Presets

- Tapping a preset button applies its settings, one command per setting, in a fixed order
  *(builder's call)*, each sent once. No retries, no repeats.
- The page then shows the result through the real state (seat bars etc.). If a command is
  refused or the car doesn't change within a short time, a brief message says which setting
  didn't apply. *(My call.)*
- Owner's examples (for the first presets once their settings are available):
  - **Cold:** both seats heat high, steering-wheel heat on, cabin 70 °F, front defrost,
    rear defrost, mirror heat on.
  - **Hot:** both seats cool high, A/C at max.

### Settings catalogue (grows as each one clears the command process)

| Setting | Status (2026-10-30) |
|---|---|
| Driver / passenger seat heat & cool, 3 levels each | **Confirmed** on the owner's car (values below) |
| Steering-wheel heat on/off | Path known from `hkmc-coldstart-heat` (`com.hkmc.vr` transaction 16, `set_StrgWhlHeated_Cmd_AVN`, 1/0); not yet confirmed on this car's log |
| Cabin temperature (driver; passenger/sync?) | Unknown |
| Front defrost | Unknown |
| Rear defrost | Unknown |
| Mirror heat | Unknown (on many Hyundai models tied to rear defrost) |
| A/C max | Unknown (may be a combination: A/C on, coldest, fan high, recirculation) |

## Goals

1. Version 1: the climate page with seat buttons and outside temperature, plus the settings
   app with the switch temperature. On a 2023 US Ioniq 5 (`NEEV23.USA.`, firmware 260507).
2. Version 2: presets, starting with the settings already confirmed (seats, then wheel).
3. Later: more catalogue settings, one at a time, each through the command process.
4. Clean install and uninstall: every stock file restored byte for byte.

## Non-goals

- Other cars in the first versions (US 2022 Ioniq 5 and EV6 later, per car).
- Rear seats, scheduling, automatic turn-on (`hkmc-coldstart-heat` already does boot-time heat).
- Replacing or changing the stock climate screen.
- The status bar (ruled out: both ends are occupied, see Findings).
- A climate screen of our own in the main area as a stand-in for the page (owner's decision).

## Anti-goals (a failure even if it technically works)

- **Talking to the car other than through the stock Android services.** No CAN frames of
  our own, no writing to `micomd` or `/dev/ttyTCC2`.
- **Sending a value we haven't seen the stock controls send.** The USM seat service passes
  any number straight to the MICOM with no range check, so every value we send must come
  from a log of the stock controls on a real car.
- **A climate setting the driver didn't ask for:** a send at boot, on a state update, a
  retry loop, or a tap that sends two steps.
- **Breaking the split screen.** If our patch crashes the split-screen app, the owner loses
  the whole right panel.
- **Manufacturer code in the repo.** Only our code, delta patches against stock files, and
  checksums.

## Rules carried over (owner's call; adopted 2026-10-30)

- Vehicle commands only through the stock services.
- Each new command (setting + values) is confirmed from a log of the stock controls on the
  owner's car, written up (service, method, values, caller uid), and approved by the owner
  before any build sends it. *(My call: the first build carrying a new command logs
  `would set <setting> <value>` instead of sending, until the owner approves it.)* This is a
  separate project, so the owner's approval is the gate; HMG Leafia Plus's maintainer has
  no sign-off role here.
- No per-car secrets collected or published (VIN, serials, accounts, keys, `/data`).
- Anything written to `/system`: stock checksum gate, backup, uninstall restores exact bytes.
- Only the newest firmware per region is supported (US 260507); other firmware is refused
  with a message to update.

## Findings so far

All from the owner's car (NEEV23.USA.0000.V015.012.260507), stock files kept privately at
`~/code/LEAFIA/private/stock` (*suggest moving to `~/hmg-private/stock/neev23`*), never in the repo.

### Seats (confirmed)

- **Set:** `com.hkmc.usm.USMManager` -> binder `com.hkmc.micom.usm` (`IUSMService`):
  `setFrontSeatHeatDrvStep(int)` (transaction 0xd5) / `setFrontSeatHeatAstStep(int)` (0xd6).
  `USMService` sends MICOM packet 0x92/0x11/0x1128 `[seat, value]`, no range check, no
  permission check seen. The stock climate app (`SeatHeatVentPopup` -> `ClimateManager`)
  uses exactly this call (log + `micomd` frame `ff 92 11 01 11 28 00 03 00 <seat> <value> ..`).
  First found by eclipseempire's MIT-licensed `hkmc-coldstart-heat`.
- **Values (both seats):** 2 off; 8/7/6 heat high/mid/low; 5/4/3 cool high/mid/low.
- **Read-back:** within ~40 ms `USM_Front_Seat_Heat_SET_M_RESP(0x9128)` reports
  `FrontSeatHeat_DrvOption/AstOption/DrvState/AstState`; `BCMService` fires
  `IBCMListener.onDrvSeatHeaterStatusChanged(int)` (and Ast). `BCMManager.getDrvSeatHeatState()`
  / `getAstSeatHeatState()` poll the same values. On this heat+vent car both options read 6.
  Guess: 6 = heat and vent fitted; treat other values as heat-only until seen on another car.
- Stock climate app: `sharedUserId android.uid.phone`, permissions `com.hkmc.permission.AUTOMOTIVE_HVAC`
  / `_STATE` / `_DIAGNOSTICS` (protection level normal). Our app needs
  `<uses-library android:name="automotive">`. Guess: a normal-uid app can call USM.

### Outside temperature

Confirmed from the stock code and the car's log (2026-10-30). Research notes:
`docs/notes/2026-10-30-status-and-findings.md`, "Outside temperature".

- **Source:** `DATCService` (binder `com.hkmc.micom.datc`, interface
  `com.hkmc.datc.IDATCService`) decodes the outside temperature from the climate
  controller's frames and keeps °C and °F separately. On the car the stock split-screen
  climate view and the stock climate app both received 65 °F from it.
- **How to read it:** listener only. `DATCManager.addTemperatureListener(DATCTemperatureListener)`;
  the service sends a snapshot as soon as the listener is registered, then sends updates.
  The stock apps use `onAllTemperatureChanged` (17 args: p9-p12 = outside °C, state, °F,
  state). The simpler `onOuterTempChanged(float temp, int unit)` uses unit 0 = °C, 1 = °F.
- **No getter:** `IDATCService` has no outside-temperature call, so `service call` can't
  read it. `getVRClimateStatus(0x20/0x21)` falls through to -1. There is no
  `getOutTempSnsrVal` on this firmware.
- **Display unit:** `getTemperatureUnit()` (transaction 3) returns 1 = °C, 2 = °F. This is a
  different encoding from the listener's 0/1.
- **Valid only if:** state is `0xffff` (normal; `0xff` invalid, `0xfe` sensor error, `0`
  off), the value isn't `100000.0` (out of range), and the value isn't -40 (the stock apps
  hide the reading then). Otherwise the page shows the temperature as unknown.
- **Permissions:** `DATCService` checks no permission or uid on listener registration or
  on `getTemperatureUnit` (only `dump` needs `DUMP`). Not yet tested from an ordinary
  app uid on the car.

### Split screen

- The right panel is a window of type `TYPE_SPLIT_WINDOW_RIGHT` (0x7fa), 640 x 720 px
  (density 240: 1920 x 720 px = 1280 x 480 dp), owned by package `com.mobis.splitscreen.right`
  (binder `com.mobis.splitscreen.right.SplitScreenHome`, interface `ISplitScreenInterfaces`).
  Almost certainly `/system/app/RightScreenHome.apk` (~162 MB, mostly images; **not yet
  collected**).
- **Pages are hard-coded** in the panel app. `com.hkmc.split.manager.SplitScreenManager`
  (automotive.jar) lists fixed view ids (AV 2, MAP 3, CLIMATE 4, ..., CLOCK 0xa, ...,
  TOTAL_SCREENS 0x17). The owner's page list is `Settings.System` `ss_setting`
  (`ss_setting_dr1`/`_dr2` per profile), a comma-separated id list edited by
  `...splitscreensettings.SplitScreenSettings`. No way for another app to register a page.
- **CLIMATE (4) exists as an id but is not offered on this car.** The panel app may already
  contain an unused climate page. Check this first in RightScreenHome.
- Data reaches pages through `ISplitServiceManager` (action `android.intent.action.REMOTE.SPLIT.SERVICE`,
  `update*SplitStatus` / `register*SplitListener`, parcelables in `com.hkmc.splitaidl`).
- Broadcasts: `com.mobis.intent.action.SPLIT_SCREEN_SHOW` / `SPLIT_SCREEN_HIDE`.

### Status bar (ruled out)

SystemUI `statusbar.avn.AvnStatusBar`, layout `status_bar_avn`, 90 px tall. Far left:
profile, back, home, SOS, menu, voice buttons; far right: status icons and split icon. It
shrinks to 1280 px when the split screen is shown.

## Architecture

*(My call; the builder may change it with reasons.)*

- **Our app does all the work.** One APK of our own (normal uid, installed by NEMESIS
  `[app.N] kind=apk`) holds the settings, the presets, the seat logic and every vehicle
  command, in a small service. The command code lives in one place, which keeps it easy to
  review and test.
- **The split-screen patch stays thin.** The patched panel app only adds the page: it draws
  the layout, shows what our service reports (seat states, outside temperature, preset
  names), and forwards taps to our service (bound service or explicit intents to our
  package). If our app is missing, the page shows a short "not installed" note instead of
  crashing.
- **How the page is added** (decide after collecting RightScreenHome, in this order):
  1. If an unused CLIMATE page already exists in the panel app, enable it and replace its
     content with ours, adding the id to the page list that the settings screen offers.
  2. Otherwise, add a new view id the same way the stock pages are built.
  3. If patching the panel app proves impractical (size, free space, structure), **stop and
     report to the owner.** There is no fallback product: the owner decided (2026-10-30) that
     the idea is only worth it as a split-screen page, so a climate screen in the main area is
     not built.
- **Patch delivery:** a delta against the exact stock `RightScreenHome` (md5-gated),
  built with HMG Leafia Plus's `lpatch` (GPL-3.0, credited), restoring the stock file on
  uninstall. Follow Leafia's handling of system apps: inject `classes.dex` into the stock
  APK, remove the odex and dexopt, prefill the Dalvik cache, and disable/re-enable the app
  around the file swap.
- **Deodex/reassemble:** `baksmali x -a 18` against the car's own framework with the full
  BOOTCLASSPATH (from `/init.environ.rc`); `smali a -j 1 --api 18`.

### Decisions from the design review *(my calls)*

- **Page list:** the split-screen settings app (`SplitScreenSettings`) edits `ss_setting`.
  Prefer not to patch it: when the owner turns the page on in our settings app, our app adds
  the page id to `ss_setting` (and `_dr1`/`_dr2`), remembering the previous values, and
  removes it again when turned off or on uninstall. Patch the settings app only if the panel
  ignores ids its settings screen doesn't offer.
- **USM access from a normal app is unproven.** Check it early with a harmless read (a USM
  or BCM getter) from our app. If the stock services refuse our uid, the commands move to a
  small NEMESIS package service with `uid=system` that our app asks to run them. That is
  still the stock services, called from a system-uid process. The phone sharedUserId and
  platform signature are not options.
- **One sender, no double sends:** only our service sends commands. Each tap carries a
  request id; the service ignores a repeated id. A seat tap is resolved against the real
  current state at the moment it is handled.
- **Service lifecycle:** postinstall starts our app once so it leaves KitKat's stopped
  state; the patched page binds our service. Nothing is ever sent at boot or on a state
  update.
- **Building without manufacturer jars:** our code declares the few stock interfaces it
  uses itself (binder descriptors, transaction codes, method signatures as our own AIDL or
  stub classes), so the repo and CI need no Hyundai files. Stock files are only needed for
  research and for making the delta.
- **The 162 MB panel app:** `lpatch` has output limits (12 MB cap for the 5.6 MB menu fix)
  and the unit's free space is unknown. The collection kit also records `df` and
  `/proc/meminfo`. Prefer Leafia's `zipadd` approach (rebuild the APK by adding our
  `classes.dex` to the stock file) over a full-file delta; extend the tool in a vendored copy
  of `lpatch` (GPL-3.0, credited) and offer fixes back to Leafia.

## Packaging

- NEMESIS package `hkg-climate-page` *(id builder's call)*, gated on `ro.build.product`
  prefix `NEEV23.USA.` and firmware 260507; anything else refused with a plain message.
- Parts: our APK; the split-screen patch.
- Install log with a `BUILD` stamp (commit + date). Settings and presets stored by our app
  in its own data (not `/system`); uninstall removes the app and restores the stock panel.
- Credit in `NOTICE.md`: HMG Leafia Plus (`lpatch`, GPL-3.0, research), `hkmc-coldstart-heat`
  (MIT, seat call), NEMESIS (AGPL-3.0, packaging).

## Collecting from the car

The project needs a small read-only USB collection kit of its own (NEMESIS script), modelled
on the Leafia kit: copies named stock files (first: `RightScreenHome.apk`/`.odex` and the
split screen settings app, likely ~170 MB) and the Android log, writes nothing to the car,
never collects personal data. Command research follows the seat pattern: the owner operates
the stock control through every value, then runs the kit within a minute.

## Testing

1. Unit tests on the host for the logic (seat stepping and mode choice, threshold, units,
   preset application order, one command per setting, nothing sent on boot or state
   updates), against fake seat/climate services.
2. Install/uninstall check against a copy of the stock `/system` (adapt Leafia's mock head
   unit idea): the uninstall must leave `/system` byte-identical.
3. On the owner's car: read-only build first for each new command; then the sending build.
   Seats on both sides through every level, below and above the threshold, changes from the
   touch strip reflected on the page, presets, reboot, CarPlay/Android Auto with split
   screen, rear camera, uninstall. Record car and firmware with each change.

## Plan of work (order)

1. Collection kit; collect `RightScreenHome` + split screen settings app.
2. Research the panel app: page structure, the unused CLIMATE page, how pages are listed,
   and whether rebuilding it fits the unit's free space. **Go/no-go:** if the page patch is
   impractical, stop here and report.
3. Our app: seat logic, outside temperature, settings (threshold), service; unit tests.
4. Panel patch (page layout B with seat buttons and outside temperature); install package.
5. On-car: read-only build, owner approval of the seat command, sending build. Version 1.
6. Presets (seats, then wheel after its log check). Version 2.
7. Further settings one by one through the command process.

## Left to the builder

- Exact visuals within the approved layout, sizes, touch targets.
- Preset application order and timing between commands.
- How our app and the patched page talk (bound service, intents, content provider).
- Code structure, naming, logging (prefix `hkg-climate:`).

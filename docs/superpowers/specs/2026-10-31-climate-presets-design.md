# Climate presets: design (2026-10-31)

Extends the presets of the main design (`2026-10-30-hkg-climate-page-design.md`, "3. Presets")
from the two front seats to the cabin climate and the steering wheel, and fixes two things the
owner hit in the presets editor. Target and rules are unchanged: 2023 US Ioniq 5,
`NEEV23.USA.`, firmware 260507; the rules in `CLAUDE.md` apply.

## Intent

The owner wants one tap to get the car ready: for example, on a cold morning, "Cold": both
seats heating, the steering wheel warm, the cabin at 70 °F. Today a preset can only set the
seats. This version lets a preset set the climate items the stock head-unit screen can set,
using exactly the commands the stock screen was seen sending on the owner's car.

## Goals

- A preset can hold, besides its name and the two front seats:
  - **Steering-wheel heat:** on or off.
  - **Climate:** off, auto or manual (see "Climate modes").
  - **Temperature:** one value for both sides.
  - **Fan speed:** 1 to 8.
  - **A/C:** on or off.
  - **Airflow:** face, face and floor, floor, or floor and defrost.
  - **Sync:** on or off.
  - **Climate eco mode:** on or off (added by the owner, 2026-10-31).
  Every item also has "Leave as is", the default, which sends nothing for it.
- The preset editor uses one dropdown per item instead of radio buttons.
- The editor's false "could not save" message is found and fixed.

## Non-goals

- **Defrost (front, rear), recirculation, mirror heat:** the head unit has no command for
  them. The stock screen has no control; in capture 2 no voice command reached them; the car
  has no separate mirror heat (rear defrost and mirrors share one relay). Possibly a later
  Bluelink route; not in this version.
- **Separate driver and passenger temperatures, per-side airflow:** one temperature for now
  (owner's choice); the car has no per-side airflow command.
- **Absolute temperature or fan commands:** the stock screen never sent them (only voice sent
  one absolute temperature); presets step instead (owner agreed to several commands per
  setting).
- More than four presets, or a different page layout.

## Anti-goals

- Sending any value or command the stock controls were not seen sending on this car, or a
  housekeeping command (DATC `DisScrnSet`, flag 0x20; `HU_VRActivity`, fid 0x01).
- Sending anything except in direct response to a tap on a preset (or a seat button): nothing
  at boot, on state changes, on settings changes, or as a retry.
- A preset fighting itself (a later command undoing an earlier one, a climate-off preset
  waking the climate back up).

## Constraints

- **Approval:** the rules in `CLAUDE.md`. The steering-wheel, climate, fan and eco commands
  below are new: the owner approves the write-up (service, method, values, caller uid) before
  any build sends them. The owner chose (2026-10-31) to approve the write-up from the two
  captures instead of a log-only first build, and approved it the same day, so the next build
  sends them. The record is in `docs/notes/2026-10-30-status-and-findings.md`, "Climate
  preset commands". The seat commands are already approved and keep sending.
- **Stock services only:** `DATCManager` via `VehicleInfoManager.getInstance(ctx).getService(...)`
  (as the outside temperature does today) and `USMManager` (as the seats do), from our app
  (`hkgclimate.app`, an ordinary uid). The stock callers are the climate app (DATC, USM) and
  Vehicle Settings (uid 1000); whether DATC accepts our uid is untested (USM did, for seats).
- **Platform:** Android 4.4 / Dalvik, API 19, Java 8 source without lambdas; the same build and
  test tooling as now.
- **Presets already saved** keep working after the update (their seat choices load unchanged,
  every new item reads as "Leave as is").

## Command catalogue (from the owner's car, captures 1 and 2, 2026-10-31)

Analyses: `private/work/climate-research/capture-1-analysis.md`, `capture-2-analysis.md`
(not committed). All DATC commands are `DATCManager.setFrontClimateControl(flag, value)`
(binder `com.hkmc.micom.datc`, transaction 0x41; the service logs
`setFrontClimateControl ---> flag: <f>, set1: <v>` and `success::HU_DATC_<name>(<v>)`), sent
by the stock climate app. The car answered every one with new state in 20-70 ms.

| Item | Command | Values seen | Read-back |
|---|---|---|---|
| Driver temperature | DATC flag 50 (`DrTempUpDn`) | 1 one step up, 2 one step down; a step is 1 °F or 0.5 °C | `DATC_DrTempDisp` raw byte; `getVRClimateStatus(2)` |
| Passenger temperature | DATC flag 49 (`PsTempUpDn`) | 1 up, 2 down | `DATC_PsTempDisp` raw byte; `getVRClimateStatus(29)` (°C) / `(31)` (°F) give the raw byte |
| Sync | DATC flag 22 (`SyncSet`) | 2 when turning it on (from off), 1 when turning it off (from on); turning sync on also turns driver-only off | `DATC_SyncDisp` 1 on / 0 off; `getVRClimateStatus(22)` |
| Fan speed | DATC flag 4 (`MainBlowerUpDn`) | 1 one step up, 2 one step down; raw speed 2..9 = speeds 1..8; presses at the ends change nothing; from raw 1 (auto) a press went to raw 2 | `DATC_MainBlowerDisp` raw; `getVRClimateStatus(5)` |
| Climate off | DATC flag 51, value 1 (sent as `MainBlower` 0) | 1; the whole climate turns off (operating state 2) | operating state (`OpSts`, `getVRClimateStatus(12)`): 1 on, 2 off |
| Climate auto | DATC flag 8 (`AutoSet`) | 1 only; from off it turns the climate on in auto; each further press cycles the auto intensity | `DATC_AutoDisp` 1/0; `getVRClimateStatus(8)` |
| A/C | DATC flag 9 (`AcSet`) | 0 off, 1 on; A/C off also leaves auto | `DATC_AcDisp`; `getVRClimateStatus(9)` |
| Airflow | DATC flag 6 (`FrontModeSet`) | 1 face, 2 face and floor, 3 floor, 4 floor and defrost (the stock popup offers these 4 on this car); 0 means auto (read only, never sent) | `DATC_ModeDisp`; `getVRClimateStatus(6)` |
| Steering-wheel heat | `USMManager.setSWRC_SteeringwheelWarmerSet(int)` (binder `com.hkmc.micom.usm`, 0xd4) | a press: 1, then 0 (it toggles; on/off only, no levels) | `getSWRC_SteeringwheelHeatIndStep2()` (usm 0x244): 1 on, 0 off; `onSWRCStatusReceived` |
| Front seats | already approved (main design) | 2 off, 8/7/6 heat, 5/4/3 cool | as now |
| Eco mode | `USMManager.setClimateControlECOMode(int)` (usm 0xc2; frame `USM_Seat_ETC_C_REQ` 0x1122 `[1, v]`), sent by Vehicle Settings | 2 on, 1 off (this car's CAN-DB generation is 2; on generation 1 the values swap, so the build checks nothing else and only targets this firmware) | `getClimateControlECOMode()` (usm 0x229): 1 on, 0 off |

Raw temperature byte: °F = byte + 56; °C = byte / 2 + 14 (both seen: 71 °F = 15; voice 64 °F =
8). The unit is `DATCManager.getTemperatureUnit()`: 1 °C, 2 °F. Any command, including a
temperature step, wakes the climate when it is off (it came on in auto with A/C in capture 1).

## How it behaves

### The editor

- One dropdown per item, in this order: driver seat, passenger seat, steering-wheel heat,
  eco mode, climate, temperature, fan speed, A/C, airflow, sync. Each starts at "Leave as is" for a new
  preset, or at the saved choice when editing. The seat dropdowns offer the same choices as
  today's radio buttons (cool only on seats with the option, unless already chosen).
- **Climate modes** decide what else can be chosen; the greyed dropdowns keep showing their
  value but are ignored on save (stored as "Leave as is"):

  | Climate | Temperature, sync | Fan speed, A/C, airflow |
  |---|---|---|
  | Leave as is | available | available |
  | Auto | available | greyed |
  | Manual | available | available, and at least one must be set (there is no "leave auto" command, so Manual needs something to send) |
  | Off | greyed | greyed |

  Seats, steering wheel and eco mode are available in every mode.
- Temperature choices: 62 to 82 °F in 1° steps (the stock range). If the car shows °C, the
  dropdown shows °C (17 to 27 in 0.5° steps) and the preset stores °F *(builder's call how to
  store; it must round-trip without drift)*.
- Name rules as today (1-10 characters). A preset must set at least one item.
- **Save:** on success the editor closes; a failure shows its reason. The current false
  "could not save" must be root-caused first: the next build shows the exception class and
  message in the editor's error line and logs the stack trace, and the save path is covered by
  a test. (No cause was found by reading the code: the write happens before anything that
  could throw.)

### Applying a preset (a tap on its slot)

In this order, each item only if the preset sets it and the car is not already there:

1. **Seats** (as today: straight to the target, driver then passenger).
2. **Steering wheel:** if its state differs from the target, one press (1 then 0).
   **Eco mode:** if it differs, one command (2 on, 1 off). *Designer's call:* eco applies even
   with "Climate: off", like the seats and wheel: it is a vehicle setting on the seat service
   and does not wake the climate.
3. **Climate off:** if the preset says off, send climate off (if the climate is on) and stop
   here: nothing else in the cabin is sent.
4. **Climate auto:** one auto press if auto is not on (never a second press, which would
   change the auto intensity).
5. **Manual items:** A/C, then airflow, then fan speed (stepping one press at a time towards
   the target).
6. **Sync:** on or off as chosen.
7. **Temperature:** step the driver side one press at a time to the target. Then read the
   passenger side again: if it is already at the target (sync makes it follow), stop; if
   driver-only is on (`DATC_DriverOnlyDisp` / `getDriverOnlyValue()` 1; the passenger
   temperature then reads 0), skip it; otherwise step it too.

Stepping sends one press, waits for the car's new value (or a short timeout), and decides the
next press from the new value, with a cap on presses per item; a value that does not move is
reported, not retried. If the climate is off and the preset is not "Climate: off", the first
cabin command wakes it; the remaining items then work from the woken state. Nothing is sent
for an item whose current state is unknown. As today, a check a few seconds after the tap
reports any item that did not reach its target on the settings screen; it never resends.

### Calls made for the builder (designer's calls, 2026-10-31)

- **Manual when the car is in auto:** the chosen manual items are sent even if they already
  match, one press each, so the car leaves auto (capture 1: A/C off leaves auto; repeating the
  current airflow or a fan press at an end is acknowledged with no other change). Outside auto,
  an item that already matches is skipped. If the car is still in auto afterwards, the result
  says so.
- **Log-only stepping:** in would-set mode the log shows the presses the current value calls
  for, for example `would set DrTempUpDn 2 x3 (73 -> 70 °F)`; nothing waits for the car.
- **Readiness per service:** seats and the steering wheel need the USM service; cabin items
  need the DATC service and a known cabin state. A preset slot is enabled when at least one of
  its items can run; items that can't are skipped and named in the result.
- **One apply at a time:** while a preset is being applied (stepping can take seconds), other
  preset and seat taps are ignored.
- **The "could not save" fix ships in the same build** as the dropdown editor: the new save
  path writes with a checked `commit()`, keeps the write out of the broad catch, shows the
  exception in the error line and logs it, so the owner's next try either works or tells us why.
- **If the DATC service refuses our uid** (the stock code shows no uid or permission check on
  `setFrontClimateControl`, so this is unlikely): the fallback is the one already noted for the
  seats, a small NEMESIS helper calling the same stock service as the system uid, in a later
  build.

## Left to the builder

- How the app reads the cabin state (the DATC status listener, `getVRClimateStatus`, or both)
  and the exact timeouts and press caps.
- The stored preset format, as long as old presets load unchanged.
- How the would-set (log-only) mode for the new commands is switched off once approved (a
  build constant, as with the seats).
- Where the result of the last preset shows (today: the settings screen's command line).

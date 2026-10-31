# hkg-climate-page Plan 6: climate presets (version 1.1.0)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Presets also set the steering-wheel heat, climate eco mode, climate off/auto/manual, one cabin temperature, fan speed, A/C, airflow and sync, with the owner-approved stock commands; the preset editor uses dropdowns; the false "could not save" is fixed or explained.

**Architecture:** Core gains a richer `Preset` (stored backwards-compatibly), a checked sender for the new commands, and a `PresetRunner` that applies the non-seat items one step at a time, reading the car's state between steps through a polling reader. The app gets two narrow stock-call classes (the only code allowed to name the new setters), a polling cabin reader, wiring in `ClimateService`, and a Spinner-based editor. The panel page and its Bundle contract do not change, so the panel patch is not rebuilt.

**Tech Stack:** as plans 2-5 (Android API 19, Java 8 source without lambdas, d8, JUnit 4 host tests, `tests/app/check-app.sh` dex checks).

**Spec:** `docs/superpowers/specs/2026-10-31-climate-presets-design.md` (behaviour, editor, calls made for the builder). Command approval: `docs/notes/2026-10-30-status-and-findings.md`, "Climate preset commands" (approved 2026-10-31).

## Global Constraints

- **Only these new calls are ever made**, and only from a preset tap:
  - `DATCManager.setFrontClimateControl(int flag, int value)` with exactly: flag 50 or 49 → 1 or 2; flag 4 → 1 or 2; flag 51 → 1; flag 8 → 1; flag 9 → 0 or 1; flag 6 → 1, 2, 3 or 4; flag 22 → 1 or 2.
  - `USMManager.setSWRC_SteeringwheelWarmerSet(int)` with 1, then 0 `WHEEL_RELEASE_MS` = 200 ms later (a 0 only directly after our own 1).
  - `USMManager.setClimateControlECOMode(int)` with 2 (on) or 1 (off).
  - Never `setVRClimateStatus`, any other DATC flag (including 32), absolute temperature or fan flags, driver-only (24) or HEAT (48).
- **Reads:** `DATCManager.getVRClimateStatus(int)` for flags 2, 5, 6, 8, 9, 12, 22, 29, 31; `getDriverOnlyValue()`; `getTemperatureUnit()` (1 °C, 2 °F); `USMManager.getSWRC_SteeringwheelHeatIndStep2()` (1 on, 0 off); `getClimateControlECOMode()` (1 on, 0 off). A getter's -1 (or -1.0) means unknown.
- **Item values in a `Preset`:** `LEAVE = -1` for every item. `wheel`, `eco`, `ac`, `sync`: 0 off, 1 on. `climate`: `CLIMATE_OFF = 1`, `CLIMATE_AUTO = 2`, `CLIMATE_MANUAL = 3`. `tempF`: 62..82. `fan`: 1..8 (car raw level = fan + 1). `airflow`: 1 face, 2 face and floor, 3 floor, 4 floor and defrost.
- **Climate modes** (spec table): Auto → `fan`, `ac`, `airflow` are stored as LEAVE; Off → `tempF`, `fan`, `ac`, `airflow`, `sync` are stored as LEAVE; Manual needs at least one of `fan`, `ac`, `airflow`. Seats, wheel and eco are never greyed.
- **Apply order** (spec "Applying a preset"): seats; wheel; eco; climate off (then stop the cabin part); climate auto; A/C, airflow, fan; sync; driver temperature, then passenger.
- **Timing:** `POLL_MS` = 100, `STEP_WAIT_MS` = 1500 (wait for the car's new value after a send), caps: fan 10 presses, temperature 25 presses per side.
- Nothing is sent at boot, on state updates, on settings changes or as a retry. One apply at a time: while a preset runs, other preset and seat taps are ignored.
- Version `1.1.0`, versionCode `5` (manifest, `NEMESIS.data`, `build-app.sh` defaults, `check-app.sh`).
- The panel (`panel/`, `package/patch/*`) is unchanged.
- Commits: plain words, ending `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. No session links.

## Review Focus

1. **Climate off at the start** (operating state 2) with a preset that is not "Climate: off": the first cabin send wakes the climate; every later item is decided from a fresh read, and an item whose state is still unknown is skipped and named. Test in Task 2.
2. **Stepping that doesn't move** (the car ignores a press, or the value is at a limit): the item stops after one `STEP_WAIT_MS` without a change and is reported; no further presses for it. Test in Task 2.
3. **Celsius cars:** a 70 °F target on a car showing °C steps to 21.0 °C (nearest 0.5) in 0.5° steps, and the stored preset stays 70 °F. Test in Tasks 1 and 2.
4. **Old saved presets** (3-field lines) load with every new item LEAVE, and save back in the new format without changing their seats. Test in Task 1.
5. **A wheel press interrupted** (the service goes away or the sender fails on the 1): no 0 is sent afterwards; a 0 is never sent without our own 1 just before. Test in Task 2 (and the dex check in Task 3).

---

### Task 1: Core preset model, storage and editor choices

**Files:**
- Modify: `core/src/hkgclimate/core/{Preset,PresetList,PresetSummary,ClimateCore}.java`
- Create: `core/src/hkgclimate/core/PresetChoices.java`
- Test: `core/test/hkgclimate/core/{PresetTest,PresetListTest,PresetSummaryTest,PresetChoicesTest}.java` (new or extended)

**Interfaces:**
- Produces:
  - `Preset` keeps `name`, `driver`, `passenger`, `LEAVE`, `MAX_NAME`, `target(Seat)`, and gains `public final int wheel, eco, climate, tempF, fan, ac, airflow, sync;` plus the constants in Global Constraints (`CLIMATE_OFF/AUTO/MANUAL`, `TEMP_MIN_F = 62`, `TEMP_MAX_F = 82`, `FAN_MIN = 1`, `FAN_MAX = 8`, `AIRFLOW_MIN = 1`, `AIRFLOW_MAX = 4`).
  - Constructors: `Preset(String name, int driver, int passenger)` (every new item LEAVE) and `Preset(String name, int driver, int passenger, int wheel, int eco, int climate, int tempF, int fan, int ac, int airflow, int sync)`. Both store the **normalized** values (climate-mode greying applied).
  - `static String check(Preset p)`: null when valid, else a short reason (name rules as today; every item in range or LEAVE; at least one item set: `"choose at least one setting"`; Manual without fan, A/C or airflow: `"manual needs a fan speed, A/C or airflow"`). The old `check(String, int, int)` stays for seat-only callers.
  - `boolean hasSeatItems()`, `boolean hasRunnerItems()` (wheel, eco or any cabin item set).
  - `PresetList.encode` writes 11 tab-separated fields (name, driver, passenger, wheel, eco, climate, tempF, fan, ac, airflow, sync); `decode` accepts 3-field (old) and 11-field lines, skipping anything else or anything that fails `check`.
  - `PresetChoices`: for each item, the offered `(value, label)` pairs in editor order, starting with `(LEAVE, "Leave as is")`; `temperatureChoices(int unit)` labels 62..82 °F, or for unit 1 the °C of each (`celsiusHalf(int f)` = nearest 0.5 of (f - 32) * 5 / 9, shown like `21.0 °C`); `static boolean greyed(String item, int climate)` per the climate-mode table. Seat choices keep using `SeatChoices` (unchanged).
  - `PresetSummary.of(Preset)` lists every set item, e.g. `Cold: driver heat high, passenger heat high, wheel on, auto, 70 °F`.
- [ ] **Step 1: Write failing tests:**
  - normalization: Auto drops fan/A/C/airflow; Off drops temperature, fan, A/C, airflow, sync; seats, wheel, eco survive both;
  - `check`: each range edge (61/62/82/83 °F; fan 0/1/8/9; airflow 0/1/4/5); "choose at least one setting"; Manual with only temperature → the manual reason; Manual with A/C → valid;
  - **Review Focus 4:** a 3-field line decodes with every new item LEAVE and re-encodes as 11 fields with the same seats; an 11-field line round-trips; 4-field and 12-field lines are skipped;
  - **Review Focus 3 (part):** `celsiusHalf(70) == 21.0`, `celsiusHalf(62) == 16.5`, `celsiusHalf(82) == 28.0`;
  - `greyed` for every item × climate value; `PresetSummary` text for a full and a seat-only preset.
- [ ] **Step 2: Run** `tools/test-java.sh` → FAIL.
- [ ] **Step 3: Implement.** `ClimateCore.onPresetTap` switches to `Preset.check(p)`; its seat behaviour is unchanged in this task.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** ("Presets: steering wheel, eco, climate, temperature, fan, A/C, airflow and sync items, stored with the old format still readable").

### Task 2: Core runner: checked cabin sender and step-by-step apply

**Files:**
- Create: `core/src/hkgclimate/core/{CabinCalls,CabinState,CabinReader,Scheduler,CheckedCabinSender,PresetRunner}.java`
- Modify: `core/src/hkgclimate/core/ClimateCore.java`
- Test: `core/test/hkgclimate/core/{CheckedCabinSenderTest,PresetRunnerTest,ClimateCorePresetTest}.java`

**Interfaces:**
- Consumes: Task 1 (`Preset`, `PresetChoices.celsiusHalf`).
- Produces:
  - `interface CabinCalls { void climate(int flag, int value) throws Exception; void wheel(int value) throws Exception; void eco(int value) throws Exception; }`.
  - `final class CabinState` (immutable, public final fields; `UNKNOWN` = -1, temperatures `Float.NaN` when unknown): `unit`, `opState` (1 on, 2 off), `auto`, `ac`, `airflow`, `fanRaw`, `sync`, `driverOnly`, `float driverTemp`, `float passengerTemp` (display unit; passenger 0 or NaN when not shown), `wheel`, `eco`; plus `static CabinState of(...)` for tests and `boolean cabinKnown()` (opState known).
  - `interface CabinReader { CabinState read(); }` (never throws; unknown fields as above). `interface Scheduler { void after(long delayMs, Runnable r); }`.
  - `CheckedCabinSender(CabinCalls calls, SendLog log)`: `boolean climate(int flag, int value)`, `boolean wheelPress(Scheduler s)` (sends 1, schedules the 0 after `WHEEL_RELEASE_MS`; refuses a press while one is pending; the 0 is not sent if the 1 failed), `boolean eco(int value)`. Each refuses any pair outside Global Constraints with a log line `send refused ...`, logs `sent setFrontClimateControl <flag> <value>` / `sent setSWRC_SteeringwheelWarmerSet <v>` / `sent setClimateControlECOMode <v>` or `send failed ...: <throwable>`, catches Throwable, never retries; returns true only when the stock call returned.
  - `PresetRunner(CheckedCabinSender sender, CabinReader reader, Scheduler scheduler, SendLog log)`: `boolean start(Preset p)` (false when busy or the preset has no runner items), `boolean busy()`, `String lastResult()` (one line: what reached its target, what didn't and why, e.g. `Cold: wheel on, auto, 70 °F (3 steps); not done: A/C (no change after 1.5 s)`).
  - The runner walks the apply order in Global Constraints as a state machine on `scheduler` (each step: read, decide, send at most one command, poll every `POLL_MS` until the item's value changes or `STEP_WAIT_MS` passes). Rules from the spec "How it behaves" and "Calls made for the builder": skip items already at target (except Manual items while auto is on, sent once each); climate off → send 51/1 only if `opState == 1`, then end the cabin part; auto → 8/1 only if `auto != 1`; fan from raw 1 → one press, then step; sync → 2 to turn on, 1 to turn off; driver temperature steps 50, then re-read and step 49 unless the passenger is at target, `driverOnly == 1`, or the passenger temperature is not shown; °C targets from `celsiusHalf`, step 0.5.
  - `ClimateCore(SeatCommander sender, int switchTempF, PresetRunner runner)`: `onPresetTap` does the seats as today (when seats are ready and the preset has seat items), then `runner.start(p)`; returns the number of seat sends plus 1 if the runner started. `onTap` returns false while `runner.busy()`. Preset slots are enabled when the preset has runner items, or has seat items and the seats are ready.
- [ ] **Step 1: Write failing tests** with a fake `CabinCalls` (records calls), a fake reader whose state the test (or a fake car reacting to calls) changes, and a manual `Scheduler` the test advances:
  - sender allowlist: every allowed pair passes; flag 32, 24, 48, 5, 28-31, value 3 for flag 9, 0 for flag 6, 5 for airflow, eco 0 and 3 are refused with no call; a throwing call logs `send failed` and returns false;
  - **Review Focus 5:** wheel press sends 1 then 0 after 200 ms; a failing 1 sends no 0; a second press while pending is refused;
  - a full "Cold" preset from all-off: order of calls matches the apply order; temperature 71 → 70 °F is one 50/2; passenger follows with sync (no 49 sent);
  - **Review Focus 1:** climate off at start with Manual + A/C on + 72 °F: the first send wakes, later items decided from fresh reads; an item still unknown is skipped and named in `lastResult()`;
  - **Review Focus 2:** a fan press with no change stops the fan after 1.5 s and is reported; no second fan press;
  - **Review Focus 3:** a °C car, target 70 °F, at 22.5 °C → three 50/2 presses ending at 21.0;
  - climate off preset when already off sends nothing in the cabin part but still applies wheel/eco; Auto when already auto sends no 8/1; Manual while auto sends each chosen manual item once even if equal;
  - driver-only on: no 49 sent; caps: a temperature that keeps moving the wrong way stops at 25 presses;
  - `busy()` during the run; a second `start` and a seat tap are refused while busy; `ClimateCore.onPresetTap` still applies seats exactly as before (existing tests keep passing).
- [ ] **Step 2: Run** `tools/test-java.sh` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** ("Core: presets apply the steering wheel, eco and cabin items one checked step at a time").

### Task 3: App: stock calls, cabin reader, wiring, dex fences, version 1.1.0

**Files:**
- Create: `app/src/hkgclimate/app/{DatcClimateCalls,CabinStateReader}.java`
- Modify: `app/src/hkgclimate/app/{UsmSeatCalls,ClimateService,PageLinkBinder,SettingsActivity}.java`, `app/AndroidManifest.xml`, `tools/build-app.sh`, `stubs/hkmc/src/com/hkmc/datc/DATCManager.java`, `stubs/hkmc/src/com/hkmc/usm/USMManager.java`, `tests/app/check-app.sh`

**Interfaces:**
- Consumes: Task 2 (`CabinCalls`, `CabinReader`, `Scheduler`, `CheckedCabinSender`, `PresetRunner`, the new `ClimateCore` constructor).
- Produces:
  - Stubs gain exactly: `DATCManager.setFrontClimateControl(int, int)`, `float getVRClimateStatus(int)`, `int getDriverOnlyValue()`; `USMManager.setSWRC_SteeringwheelWarmerSet(int)`, `int getSWRC_SteeringwheelHeatIndStep2()`, `setClimateControlECOMode(int)`, `int getClimateControlECOMode()`.
  - `DatcClimateCalls` (the only class naming `setFrontClimateControl`): gets the `DATCManager` from `VehicleInfoManager.getInstance(ctx).getService(VehicleInfoManager.DATC_SERVICE)` on each send (never `new DATCManager`), one call per send, catches nothing.
  - `UsmSeatCalls` also implements the wheel and eco parts of `CabinCalls` and the two USM getters (it stays the only class naming `com.hkmc.usm`), same lazy-manager and drop-on-failure rules.
  - `CabinStateReader implements CabinReader`: the reads in Global Constraints, each guarded (a throw or -1 → unknown); returns an all-unknown state when the DATC service isn't there yet.
  - `ClimateService` builds `CheckedCabinSender` (DatcClimateCalls + UsmSeatCalls), `PresetRunner` with a main-looper `Scheduler`, and passes the runner to `ClimateCore`. The settings screen's "Seat command" line becomes "Last command" and shows the runner's `lastResult()` when the last tap was a preset with runner items (the seat results and 3-second watch work as today).
  - Version 1.1.0 / 5.
- [ ] **Step 1: Extend `tests/app/check-app.sh` first:**
  - badging `versionName='1.1.0'`, `versionCode='5'`;
  - **DATC fence:** every `Lcom/hkmc/datc/DATCManager;.setFrontClimateControl` reference is inside `hkgclimate.app.DatcClimateCalls`; the dex names no `DATCManager` member outside {`addTemperatureListener`, `removeTemperatureListener`, `getTemperatureUnit`, `getVRClimateStatus`, `getDriverOnlyValue`, `setFrontClimateControl`}; no `setVRClimateStatus` anywhere; `DatcClimateCalls` constructs no `DATCManager`;
  - **USM fence:** the existing `usm_refs_ok` allowlist gains the four new members (still only inside `UsmSeatCalls`);
  - calls to `CabinCalls.climate/wheel/eco` (and to `DatcClimateCalls`/`UsmSeatCalls` wheel/eco directly) only from `CheckedCabinSender`;
  - self-tests for the new checks on throwaway dexes (a `setVRClimateStatus` call, a `setFrontClimateControl` outside `DatcClimateCalls`, an extra `DATCManager` member), as the existing usm self-tests do.
- [ ] **Step 2: Run** `sh tests/app/check-app.sh` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `tools/test-java.sh && sh tests/app/check-app.sh && sh tests/panel/check-page.sh` → PASS.
- [ ] **Step 5: Commit** ("App: presets send the approved climate, steering-wheel and eco commands through two fenced stock-call classes; version 1.1.0").

### Task 4: Editor: dropdowns, climate-mode greying, save fix

**Files:**
- Modify: `app/src/hkgclimate/app/{PresetEditorActivity,Prefs,SettingsActivity}.java`, `tests/app/check-app.sh`

**Interfaces:**
- Consumes: Task 1 (`Preset`, `PresetChoices`, `SeatChoices`), Task 3 (`CabinStateReader` unit, or the settings screen's last unit passed as an extra like the seat options).
- Produces:
  - One `Spinner` per item in the spec's order (driver seat, passenger seat, steering wheel, eco, climate, temperature, fan, A/C, airflow, sync), each with its own fixed view id (so saved view state can't mix them up), starting at the saved choice or "Leave as is". Changing climate enables/disables the greyed spinners at once (`PresetChoices.greyed`). Temperature labels follow the car's unit (extra `unit`; default °F).
  - `boolean Prefs.setPresets(List<Preset>)` writes with `commit()` and returns its result.
  - Save: build the `Preset` (normalized), `Preset.check` → reason in the error line; else write; `false` → `could not save: the settings write was refused`; any exception → `could not save: <SimpleClassName>: <message>` plus `Log.w(TAG, ..., t)`. The write and `finish()` are outside the catch that turns errors into "could not save".
- [ ] **Step 1: Extend `check-app.sh`:** the dex has no `RadioGroup`/`RadioButton` reference in `PresetEditorActivity`; `Prefs.setPresets` calls `SharedPreferences$Editor.commit` (not `apply`). Run → FAIL.
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Run** `tools/test-java.sh && sh tests/app/check-app.sh` → PASS.
- [ ] **Step 4: Commit** ("Preset editor: dropdowns for every item, greyed by climate mode; save checks the write and shows any error in full").

### Task 5: Package, docs and checklist

**Files:**
- Modify: `package/NEMESIS.data` (`version=1.1.0`), `README.md`, `docs/notes/2026-10-30-status-and-findings.md` (status row, branches), `docs/superpowers/specs/2026-10-31-climate-presets-design.md` only if a ruling changed it
- Create: `docs/testing/2026-10-31-climate-presets.md`

- [ ] **Step 1: Run the full chain from clean:** `rm -rf build && tools/test-java.sh && sh tests/app/check-app.sh && sh tests/panel/check-page.sh && sh tests/kit/run.sh && sh tests/capture/run.sh && tools/build-app.sh && tools/build-package.sh && sh tests/package/run.sh && tools/build-kit.sh && tools/build-usb.sh && sh tests/usb/run.sh && tools/build-capture.sh` → all pass; `package/patch` unchanged.
- [ ] **Step 2: Write the owner's checklist**, short and numbered:
  1. Install 1.1.0 from the USB stick (restart and replug if it reports low /system space).
  2. Editor: every item is a dropdown; climate Auto greys fan/A/C/airflow, Off greys the cabin items; Save closes the editor. If "could not save: …" appears, write down the full text and run the kit at once.
  3. Build "Cold" (seats heat high, wheel on, climate auto, 70 °F) and "Hot" (seats cool high, climate manual, A/C on, fan 6, airflow face, 68 °F), car on.
  4. With the climate off, tap "Cold": seats, wheel, auto, then the temperature steps to 70; the settings screen's "Last command" says what was done.
  5. Tap "Hot": A/C, airflow, fan 6, 68 °F; the car leaves auto.
  6. A preset with climate off: the climate turns off; seats and wheel still apply.
  7. Eco on/off from a preset matches Vehicle Settings.
  8. Run the capture tool around steps 4-7 (start before step 4, stop after 7) and send `CAPTURE` with notes.
- [ ] **Step 3: Commit** ("Climate presets (1.1.0): package version, docs and checklist").

# hkg-climate-page: status and findings (2026-10-30)

A record of the design, what has been built, and what was learned about the 2023 US
Ioniq 5 head unit (`NEEV23.USA.0000.V015.012.260507`). Companion documents:

- Design: `docs/superpowers/specs/2026-10-30-hkg-climate-page-design.md` (seat call,
  values, read-back, split-screen overview, architecture, rules)
- Plan 1: `docs/superpowers/plans/2026-10-30-feasibility-kit-and-research.md`
- Split-screen research and go/no-go: `docs/research/2026-10-30-split-screen.md`

## Status

| Item | State |
|---|---|
| Design | Approved by the owner. Version 1 page layout "B" chosen. |
| Plan 1 (kit, research, go/no-go) | Done. Decision: GO. |
| Read-only collection kit (`kit/`, `tests/kit/run.sh`) | Built, 30 host checks pass, reviewed, run on the owner's car. |
| Plan 2 (our app, panel patch, package: test build 1) | Done, installed and tested on the owner's car (2026-10-30). Seat taps are logged, not sent. |
| Seat command | Approved by the owner on 2026-10-30 (see "Seat command approval"). Sent by version 1 on each seat tap (no approval switch). |
| Plan 3 (test build 2: approved seat command behind an approval switch) | Installed and tested on the owner's car (2026-10-30): seat taps change both seats through heat and cool. |
| Plan 4 (version 1: owner's layout changes, taps always send, install message for low /system space) | Built (1.0.0), not yet on the car. Checklist: `docs/testing/2026-10-30-version-1.md`. |
| Plan 5 (version 2: presets of the seat settings, up to 4, applied from the page) | Built (2.0.0), not yet on the car. Checklist: `docs/testing/2026-10-30-presets.md`. |
| Branches | Version 1 and presets are built on branches `version-1` and `presets` (stacked), not merged into `main` yet. |

## Seat command approval (2026-10-30)

The owner approved this command for the sending build:

- **Service:** stock `com.hkmc.usm.USMManager` -> binder `com.hkmc.micom.usm`.
- **Methods:** `setFrontSeatHeatDrvStep(int)` (driver), `setFrontSeatHeatAstStep(int)` (passenger).
- **Values:** only 2 (off), 8/7/6 (heat high/mid/low), 5/4/3 (cool high/mid/low), as logged
  from the stock climate controls on this car.
- **Caller:** our app `hkgclimate.app` (an ordinary app uid). The stock climate app calls it as
  `android.uid.phone`; whether the service accepts an ordinary uid is untested. If it refuses,
  the fallback is a small NEMESIS helper running as the system uid.
- **Gate:** seat taps send one step per tap. Preset taps set a seat straight to its target
  (for example 2 to 6, or 8 to 5), at most one command per seat and at most two per tap (driver
  first, then passenger), all with the approved values above. Nothing is sent at boot or on a
  state update. Owner removed the approval switch for version 1 (2026-10-30); taps send by
  default.
- Preset behaviour awaiting the owner's confirmation (2026-10-30).

## Design summary

- A page on the right-side split screen (page id 4), reached by swiping like the stock
  Clock and Energy pages. Version 1 layout "B": a top line with only the outside
  temperature, two 180 dp seat buttons (driver left, passenger right), and four greyed-out
  preset slots below. A seat button's border (orange, blue or grey) shows what the next tap
  does. No test strip.
- Seat button: each tap steps off -> high -> mid -> low -> off. Heat below the switch
  temperature (default 60 °F), cool at or above it; the mode is chosen when the seat goes
  from off to on.
- A settings app (main screen) for the switch temperature, turning the page on/off, and later
  presets. No approval switch: taps send.
- Presets (version 2): named buttons applying several climate settings; each setting
  joins only after its values are confirmed from a log of the stock controls and approved
  by the owner.

## Confirmed on the owner's car

- **Seat values** (both seats, from the stock climate app's log, second Leafia kit run):
  2 off; 8/7/6 heat high/mid/low; 5/4/3 cool high/mid/low. Stock order off -> high -> mid
  -> low -> off. The stock climate screen sends these through the stock seat-settings
  service (details in the design, "Seats (confirmed)").
- **Seat state reads** (hkg-climate kit, `probe.txt`): driver 5, passenger 8, identical
  from the BCM and USM services and identical for root and for an ordinary app uid
  (10999). The owner confirmed the seats were set to driver cool high and passenger heat
  high during the run. An ordinary app can read the seat states.
- **Option value 6** on both seats on this heat+cool car (guess: 6 = heat and cool fitted).
- **Free space and memory** (hkg-climate kit `summary.txt`): `/system` 265.7 MB free,
  `/data` 814.2 MB free, `/storage/upgrade` 14.6 GB free; RAM 3.7 GB total, ~2.6 GB
  available.
- **Split-screen page lists**: `ss_setting` = `0,21,3,2,9,10`; `ss_setting_dr1` (the
  active profile) = `0,21,3,2,6,9,10`; `ss_setting_dr2` = `0,21,3,2,9,10`. Ids: 0 EV
  (battery/range), 21 electricity use, 3 map, 2 media, 6 weather, 9 compass, 10 clock.
  Id 4 (climate) is in none of them. The owner's "energy" page is 0 or 21 (unconfirmed).
- **Stock climate page**: the split screen has no Climate page on this car; the owner
  confirmed it isn't offered in the split-screen settings.
- **Test build 1 on the car** (2026-10-30, hkg-climate kit run at 14:05):
  - Install ran clean: stock checksums matched, `lpatch delta` and the streaming `zipadd`
    produced the pinned md5s on the unit (dex `88f5adeb…`, APK `40244c54…`), dexopt and the
    Dalvik cache prefill worked, and the panel restarted. The only WARN: `/system` was already
    read-only again when the script remounted it ro.
  - The patched panel runs: no crash or VerifyError from `com.mobis.splitscreen.right`, and the
    climate page shows. The stock hidden climate view (`SplitScreenClimateView`) only logs its
    usual page-change lines; it doesn't draw anything.
  - Taps log `would set setFrontSeatHeatAstStep 5` (passenger off, outside at or above the
    switch temperature, so cool high), once per tap.
  - Our ordinary app reads the outside temperature and the seats: the settings screen showed
    seat option 6 and display unit 2.
  - Page 4 is in all three page lists. The owner edited the split-screen list in the stock
    settings afterwards (`ss_setting_dr1` became `3,4,2`), and the climate page survived.
  - Unrelated: the NEMESIS Manager app crashed once (`SecurityException`, `getTasks()` needs
    `GET_TASKS`, from its `DialogActivity.onYes`), apparently when its dialog closed.
- **Test build 2 on the car** (2026-10-30, kit run at 17:22 from the combined stick via
  NEMESIS Manager's "Run USB NEMESIS.sh"):
  - **Our ordinary app uid can set the seats.** 11 taps logged `sent …` (passenger 4, 3, 2;
    driver 8, 7, 6, 2; passenger 8, 7, 6, 2). Each was followed within ~50 ms by USMService
    `[SND] USM_Front_Seat_Heat_SET_C_REQ(0x1128)` and `[RCV] …SET_M_RESP(0x9128)` reporting the
    same state, and our app logged no `no change`, `send failed` or `E/USMManager` line. Options
    6/6. No system-uid helper is needed.
  - Before our taps, USMService also sent driver 5, 4, 3, 2 and passenger 5 with no `sent` line
    from us: the owner using the stock controls. Our next passenger taps then stepped within
    cool (4, 3, 2), as designed.
  - **Update needs a restart in between.** The update ran the old package's uninstall (stock
    panel restored), then the new install refused at once: `/system 109977 KB (need 176357)`.
    The replaced patched APK still took space until the restart (probably still held open). A
    second try after a restart installed cleanly (`/system 272076 KB`).

## Split-screen panel (RightScreenHome) — page integration

From the deodexed `/system/app/RightScreenHome` (package `com.mobis.splitscreen.right`,
system uid, persistent, 161,884,916-byte APK + 2,137,656-byte odex). Line numbers refer to
`SplitScreenHome.smali` in the deodexed tree (kept privately).

- **Page base class** `com.mobis.splitscreen.right.k`: abstract, extends Object, one
  constructor `<init>()V`, one abstract method `getView()Landroid/view/View;`. Overridable
  no-ops: `f(Z)V` (enable/disable), `a(ZLandroid/content/res/Configuration;)V` (day/night or
  language, only called when `mView` is set), `H(Z)V` (transparency), `e(IZ)V`. A subclass
  sets the protected `mContext` and `mHandler` and the public `mView`; `bh(I)V` sets the
  page id (ids outside 0..0x16 become 2). Do not declare a field named `viewId`.
- **getView()** is called once when the page is registered (main thread), again on every
  page-list rebuild, and after configuration changes. It must return the same cached View;
  the panel re-parents it. There is no destroy or page-flip callback.
- **Registration** happens in `SplitScreenHome.cM()V` (from line 13442; 10 registers, p0 =
  v9). Pattern for an ordinary page (clock, id 0xa, lines 15554-15614): construct with
  `(Context mContext, Handler mHandler)`, store, call `bh(id)`, `oa.add(page)`, then post a
  message `what=0x3f5`, `arg1=id`, `obj=page` to the main-looper handler `pv`, whose handler
  stores `page.getView()` into slot `ob[id]`; the title string id goes into `od[id]`.
  Safe insertion points for a new id-4 registration: `:goto_50f` (line ~14907) or just
  after `:cond_a55` (line ~16399). Both are inside the `pz` monitor held in `v1`: keep
  `v1`; `v0` and `v2`-`v8` are free there. Use a field of our own, not the stock `oO`.
- **Showing id 4**: `SplitScreenHome.aS(I)` case 4 (`:pswitch_1b8`, line ~7865) returns
  null on this car (the build is flagged as the new GUI). Making that case return our page
  is the only change needed for a page we register ourselves. Ids whose view is null are
  skipped silently when the page list is laid out.
- **Threads**: `cM()` runs once per process on a worker thread while holding the `pz`
  monitor, so page constructors run off the main thread and must not wait for it.
  `getView()` runs on the main thread.
- **No error handling in the panel**: no typed catch blocks around page construction or
  `getView()`, and no default uncaught-exception handler. A Throwable from a page kills the
  persistent system process, which restarts and can crash again in a loop. Our page code
  must catch everything itself, and the stock hooks should reach our page by reflection
  inside a catch so a page that fails to load is simply absent.
- **Page-list reload**: the broadcast `com.android.settings.splitscreen_settings.changed`
  (receiver `t.onReceive`, needs no permission; optional int extra `splitscreen_moveto`)
  makes the panel re-read `ss_setting*` and re-lay out the page order. It does not rebuild
  pages. So adding id 4 to `ss_setting_dr1` (and the others) and sending this broadcast
  shows the page without a restart. Stock settings code in Settings_W removes id 4 during a
  settings restore; the list editor is probably ThemeSettings.apk (not collected).
- **Planned page design**: a thin page class (compiled from Java against a stub of `k`,
  merged into the deodexed code) that shows content pushed by our own app and forwards taps
  to it, with everything wrapped in catch blocks.

### Hook points checked for the panel patch (plan 2, task 9)

Checked in `private/work/panel-stock-smali` (made by `tools/deodex-panel.sh`; line numbers
are in that tree's `SplitScreenHome.smali`).

- **Deodex:** the 16 boot class path entries alone are enough. baksmali skips no class, gives
  632 classes, and `diff -r` against the 2026-10-30 research tree is empty.
- **End of the `pz` block in `cM()`:** `v1` gets `pz` and is locked at line 14721 and is not
  written again inside the block. The only normal `monitor-exit v1` is at 16425, followed by
  the method's only `return-void`. There is no label between `:cond_a55` (16399) and that
  `monitor-exit`, so every normal path to the end of the block passes `:cond_a55` (by the
  `if-eqz v0` at 16326 or by falling through from the digital-frame block). The other exits
  are the catchall handlers, which rethrow. The insertion stays right after `:cond_a55`. The
  stock lines after it write `v0`, `v2` and `v3` before reading them, so our hook may use them.
- **`aS(I)`:** `:goto_2d` (7377) is followed by `return-object v0`. `:pswitch_1b8` is written
  twice in the method: the case 4 label (7865) and its entry in the packed-switch table (8023).
  `tools/patch-panel.py` counts only labels outside switch tables. smali turns our short
  `goto :goto_2d` into `goto/16`, because the target is too far back for an 8-bit jump.
- **Clock block (15554-15614):** its references to `mContext` and `mHandler` (both named
  through `SplitScreenHome`), `oa`, `od`, `ou`, `pv` with `n/l.sendMessage`, `Message.obtain`,
  `what`/`arg1`/`obj`, `Integer.valueOf` and `ArrayList.add`/`set` match `register.smali`
  (same instruction, same reference). The only new references are our factory, our field and
  `k.bh(I)`.
- **Stock `k`** matches our compile stub (`panel/stub`): class modifiers, field types and
  modifiers, constructor, abstract `getView()`, `f(Z)`, `a(ZConfiguration)`, `H(Z)`, `e(IZ)` and
  synchronized `bh(I)`. Stock `k` also has `viewId`, `b(Message)`, `e(I,String)`, `eF`, `eG`,
  `ek`, `r(String)`, `r(Z)` and `setFullScreen(Z)`; our page declares none of those names.
- **The 0x3f5 handler** is a case in `M`; it checks that `obj` is a `k` before storing its view.
- **Rebuilt `classes.dex`:** 650 classes (632 stock + 18 ours), 1,926,500 bytes; the delta is
  263,503 bytes. Disassembled again, it matches the stock tree apart from our hooks in
  `SplitScreenHome` (labels after them move), added baksmali comments, and `n/s.t(String,String)`:
  there baksmali had replaced four odex instructions it couldn't resolve with `throw` of the same
  register. That register is always null there and each one sits behind an `if-eqz` on a
  register that is always null, so it is dead code either way (the code gets 4 units shorter
  and its labels move). The research tree has the same lines.

## Outside temperature

From the car's log (hkg-climate kit run) and the disassembled framework and stock apps
(kept privately).

- **On the car:** `DATCService` logged `saveVCRMData_CF ---> outTempCF: 65.0` (a debug
  line for telematics, not something we can read). The stock split-screen climate view in
  RightScreenHome logged `setOutsideTempUIAdvanced::temp = 65.0 / tempunit = 2`, state
  `65535`. The stock climate app's `NewClimateControl` logged `updateOutSideTemp() - 65℉`.
- **Source:** `DATCService` (automotive-service.jar) gets the outside temperature from the
  climate controller's frames: byte 0x15 is °C and byte 0x16 is °F, stored as
  `DatcStatus` ambient C/F, each with its own state. Valid range: °C -40..60, °F -40..140.
  Anything outside it is stored as `100000.0`.
- **API:** `DATCManager` (automotive.jar), service `com.hkmc.micom.datc`, descriptor
  `com.hkmc.datc.IDATCService`. `addTemperatureListener(DATCTemperatureListener)`
  registers through `registerDATCListener` (transaction 1). The service immediately sends
  its cached values (`IDATCListener.onTemperatureInformation`, code 1), then sends
  updates.
  - `onAllTemperatureChanged`, 17 args: p1-p4 driver C/state/F/state, p5-p8 passenger,
    p9-p12 outside (`amTempC, amStateC, amTempF, amStateF`), p13-p16 rear, p17
    `changeReqDisp`. The split-screen view and the climate app both use this one.
  - `onOuterTempChanged(float temp, int unit)`: unit 0 = °C, 1 = °F.
- **No getter:** `IDATCService` has no outside-temperature call. `getVRClimateStatus`
  (transaction 0x29) with `TEMP_OUTSIDE_C/F` (0x20/0x21) falls through to -1.0 (read from
  the switch table, not tried on the car). `getOutTempSnsrVal` (named by
  `hkmc-coldstart-heat`) doesn't exist on this firmware.
- **Units:** `getTemperatureUnit()` (transaction 3) and `DatcStatus.getTempUnit()` return
  1 = °C, 2 = °F (`DATCConstants.TEMP_UNIT_C/F`). That's why the log shows
  `tempunit = 2` for °F. The listener's 0/1 is a separate encoding.
- **State values** (`TemperatureDisplayState`): `0xffff` normal, `0xff` invalid, `0xfe`
  sensor error, `0` off, `1` not used. The stock apps show "--" for `0xff` and hide the
  value when it reads -40.
- **Permissions:** no permission or uid check on any `DATCService` binder method except
  `dump` (`DUMP`). An ordinary app should be able to register a listener (not yet tested).
- **Others:** Launcher_WP3 reads it the same way through its own copy of the climate
  view. SystemUI doesn't show outside temperature.

## Install method (following HMG Leafia Plus)

How Leafia rebuilds and installs patched stock system apps (from its repo,
`packages/wireless-carplay-aa`, `tools/`):

- **Delta**: `tools/mkdelta.py <stock .odex> <patched classes.dex> <out.lpd>` (bsdiff4
  1.3.1, hash-pinned). The `.lpd` header carries the source and result lengths and md5s,
  so applying it on the unit refuses anything but the exact stock odex.
- **On the unit**: `lpatch delta <lpd> <stock odex> classes.dex`, then
  `lpatch zipadd <stock apk> classes.dex <new apk>` (adds a stored `classes.dex`, copies
  the rest verbatim, fixed timestamp, deterministic md5), check the md5 against the
  expected value, back up the stock apk and odex to `/data/nemesis/backup/<id>/`,
  `pm disable` the app while its files are swapped (re-enable on every exit path),
  `nemesis remount rw`, copy over via a temp file + rename (new inode), delete the odex,
  `dexopt --preopt <apk> <odex> m=y,u=n`, wipe and prefill the Dalvik cache
  (`/data/dalvik-cache/system@app@<App>.apk@classes.dex`), `nemesis remount ro`, reboot.
  Uninstall restores the backed-up files byte for byte.
- **Size limit**: `lpatch` keeps whole files in static buffers (source 8 MB, output 12 MB,
  `tools/lpatch/lpatch.c:70-81`). The odex -> classes.dex delta fits; `zipadd` on the
  162 MB RightScreenHome APK does not. A streaming `zipadd` (chunked reads/writes, tail
  read for the central directory, incremental CRC32 and MD5, an `lseek` syscall in the
  freestanding build) is needed.
- **Mock test**: Leafia's `tools/mock/install-test.sh` installs and uninstalls against a
  copy of the stock `/system` with stubbed Android commands and checks the uninstall is
  byte-identical; it needs its package names and success string changed for reuse.
- **Our own APK** would install through NEMESIS `[app.N] kind=apk` (NEMESIS handles the
  OEM signer allowlist).

## Not yet established

- **Page-list editor app** (probably ThemeSettings.apk) not collected. Page 4 survives the
  owner editing the list (seen on the car; either the editor keeps it or our app puts it back),
  so collecting it is no longer needed.

## Tooling on this machine

- `private/toolchain/venv`: bsdiff4 1.3.1 (hash-pinned install).
- `private/toolchain/smali`: smali 2.5.2 and its jars (antlr-runtime 3.5.2,
  stringtemplate 3.2.1, antlr 2.7.7), sha1-checked against Maven Central.
- baksmali 2.5.2 and deodexed stock trees in the session scratch folder (private).
- Android SDK: build-tools 30.0.3, platform android-30. Platform android-19 not installed
  (needs the owner to accept the SDK licence:
  `~/Android/sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-19"`).
- `clang` with lld for the ARM `lpatch` build; no `qemu-arm` or `mksh` on this machine.

## Collected files (private, never committed)

- `private/HKGCLIMATE/` (this plugin's kit): RightScreenHome, DisplaySettings, Settings_W,
  SettingsCommon (apk + odex), listings, `init.environ.rc`, `summary.txt`, `probe.txt`,
  Android log.
- `~/code/LEAFIA/private/stock/` (Leafia kit runs): framework (incl. `automotive.jar`,
  `automotive-service.jar`), projection apps, HKMC_Climate, Launcher_WP3, SystemUI.
- Build inputs come from `private/stock/260507/` (read-only: verified stock RightScreenHome apk +
  odex and `init.environ.rc`); kit output folders are never used as build input (a kit run on the
  patched car copies the patched panel into `private/HKGCLIMATE/stock`).

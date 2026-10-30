# Split-screen research: go/no-go

Plan 1, Task 4. Inputs: the owner's kit run (`private/HKGCLIMATE`, product
`NEEV23.USA.0000.V015.012.260507`), the car's framework, and the four apps deodexed with
baksmali 2.5.2 (`x -a 18`, full BOOTCLASSPATH order from `/init.environ.rc` plus every other
framework odex). All four deodexed with no errors or warnings: RightScreenHome 632 classes,
DisplaySettings 515, Settings_W 2435, SettingsCommon 730. Deodexed output and decoded resources
stay in the scratch folder. This note names classes, methods, ids and sizes only.

Obfuscated names (`aS`, `cM`, `m/k`, ...) belong to this exact build (RightScreenHome.apk
161,884,916 bytes). A different build will use different names. "Log" means a line in
`logcat-full.txt` from the panel process (tag `SplitScreen*`, pid 910).

## Criteria

| Criterion | Result | Evidence |
|---|---|---|
| G1 Panel app | **PASS** | Manifest: package `com.mobis.splitscreen.right`, `sharedUserId=android.uid.system`, `persistent=true`, `largeHeap=true`, one service `.SplitScreenHome` (actions `com.mobis.splitscreen.right.SplitScreenHome`, `android.intent.action.REMOTE.SPLIT.SERVICE`). `SplitScreenHome` extends `k/q` extends `k/r` (source `SplitScreenBase.java`, an `android.app.Service`). `k/r.setContentView(I)` inflates the content and adds it with window type `0x7fa` (framework `WindowManager$LayoutParams.TYPE_SPLIT_WINDOW_RIGHT = 0x7fa`); the content is `layout/flipper_layout`. `SplitScreenHome.onCreate` creates `E` (source `SplitScreenInterface.java`, extends `com.hkmc.split.manager.ISplitScreenInterfaces$Stub`), and `E` calls `ServiceManager.addService("com.mobis.splitscreen.right.SplitScreenHome")`. `D` (`ISplitScreenOsdInterfaces$Stub`) registers `...SplitScreenHome.OSD`. `onBind` returns the `com.hkmc.splitaidl.ISplitServiceManager$Stub` (`k/q.Bk`). Second source: automotive.jar `SplitScreenManager.RIGHTAPP_PACKAGE_NAME`/`RIGHTAPP_SERVICE_NAME`/`RIGHTAPP_OSD_SERVICE_NAME` name the same package and services, and the log has `[APP]ISplitScreenInterfaces` and `[APP]SplitScreenService` lines from pid 910. |
| G2 Page mechanism | **PASS** | Id to page: `SplitScreenHome.aS(I)` (logs as `getSplitScreenViewFromId`) is a packed switch over 0..0x16 that returns the page object for each id (or null), and `aR(I)` wraps it, enables the page and returns its `getView()`. Page objects: `SplitScreenHome.cM()` builds every page at startup. Page list: `cO()` (logs `getAdvancedSettingList`) reads `Settings.System` `ss_setting`/`ss_setting_dr1`/`ss_setting_dr2` (by `getUserType()`: 1 gives dr1, 2 gives dr2, anything else the plain key) and splits it on commas. `a([String;ZII)` (`updateFlipperOrder`) parses each id, calls `aR(id)`, and adds the id to the active list `oc` only if the view is non-null. `d(ZI)` (`initFlipper`) enables or disables every page by whether `oc` holds its id, then adds each id's view to `SlidingView` in list order, tagged `F(index, id)` under `id/view_order` (0x7f090000). Second source, log: `addViewToStack view ID = 2, advancedList = [0, 21, 3, 2, 6, 9, 10]` equals the kit's `ss_setting_dr1`, and `setCurrentPage ... view id = 3` shows the map view (`app:id/navi`) at `0,1440-640,2160`: id 3 is index 2 in dr1, so the page is at 2 × 720 px. |
| G3 Code-only page | **PASS** (needs 2 patched methods; see G3 details) | Base class `com.mobis.splitscreen.right.k` (source `SplitScreenView.java`) is a plain abstract `Object` with one abstract method, `getView()`. Every other method has a default. Stock pages build their own views in `getView()`; a subclass can build its views in code with no resources. The page runs inside the panel process, so the panel's `AndroidManifest.xml` doesn't change. Single source for "works when built": no code-only page has been built and run yet. |
| G4 Space | **PASS** | `df`: `/data` free 814.2M ≥ 2 × 161,884,916 B = 308.8 MiB (505 MiB to spare). `/system` free 265.7M. New APK = old + `classes.dex` (≈2.2 MB worst case, stored) and the old 2,137,656-byte odex is replaced by a new one of about the same size, so (new − old − old odex) + 10 MB ≈ 10.1 MB ≪ 265.7M. Even writing a whole second 154.4 MiB copy on `/system` before a rename would fit, with 111 MiB to spare. `/cache` (152.1M) is too small for staging; stage on `/data`. |
| G5 Page list | **ANSWERED IN PART** | Panel side: the panel shows any id in the active key whose `aS` returns a view. It never checks a list of offered items: `updateFlipperOrder` drops ids with a null view without an error, and no other filter covers id 4 (`cN` removes only out-of-season sports ids and EV GT mode). The panel reloads on broadcast `com.android.settings.splitscreen_settings.changed` (receiver `t`, registered without a permission; optional int extra `splitscreen_moveto` = view id to jump to). The chain is IPC msg 0xbf `SPLIT_IPC_HANDLER_MSG_UPDATE_ADVANCE_SETTING_LIST` → `cN()` (`updateAdvancedSetting`) → msg 0x1770 `SPLIT_HANDLER_MSG_UPDATE_FILIPPERODER` → `updateFlipperOrder`. It also reloads on a profile change (`SPLIT_HANDLER_MSG_USER_TYPE_CHANGE` → `ds()` → `cN()`). Editor: `DisplaySettings` `splitscreensettings.SplitScreenSettings` is a stub (it only logs "SplitScreenDummy"). The log names `com.android.themesettings.splitscreensettings.SplitScreenSettingsReceiver`, so the real editor is very likely **ThemeSettings.apk, not collected** (single source). `Settings_W` `util.SplitScreenUtils` holds the shared list rules: `isSupportViewId(ctx, 4)` always returns false, and `removeNotSupportViewId` strips unsupported ids. It runs from `splitscreen.SplitScreenDataBackup.checkValidSSLists` (called from `receivers.SettingsRegulationReceiver.doRestore` after a restore or one-binary update) and from `SettingsDataBackUp`. Conclusion: the panel needs no settings-app patch to show id 4, but stock settings code can remove 4 again, so our app must watch the keys and put 4 back. |
| G6 Climate id 4 | **IMPLEMENTED BUT TURNED OFF** | `m/k` (source `SplitScreenClimateView.java`, about 700 KB of smali, helpers `m/a`..`m/l`: `DATCListener`, `DATCTemperatureListener`, `BCMPowerListener`) is a display-only climate status page: driver and passenger temperature, blower level and mode, AUTO/A/C, outside temperature, air cleaning, smart vent. It has variants for DATC/FATC/EV/HEV, 1- and 2-knob, RHD and navi (99 layouts `climate_*`, 0x7f030014–0x7f030077, plus `climate_osd`/`climate_popup_osd`). It has no seats, no click listeners and no `DATCManager` setters. `cM()` builds it with `bh(4)`, but `aS` case 4 returns null when `n/v.qq()` is true (logs `getViewFromId id = CLIMATE_VIEW but isSSClimateRemoved`). `qq()` = `n/v.isNewGUI()` = any of `MobisBuildFeature.USE_ADVANCEDUI_HYUNDAI/_KIA`, `USE_ECOGUI_COMMON`, `FEATURE_GENESIS`, `USE_NEW_GUI_4TH_KIA/_HYUNDAI` (props `ro.b.f.*`). Case 4 is also refused when `m/k.isFrontMTCType()` is true. Second source, log: `SplitScreenHome isSSClimateRemoved !!` from receiver `t` (on `mobis.intent.action.CLIMATE_VARIANT`/`CLIMATE_REAR_MTC`). The log also shows `SplitScreenClimateView` still alive and reading temperatures (it runs even though the page is off). Settings side: `SplitScreenUtils.isSupportViewId(4)` is false, and the newer default lists (`SS_LIST_STEP60`, `SS_LIST_STEP99999`) leave out 4; only the old `SS_LIST`/`SS_SETTINGS` had it. |
| G7 Read access | **PASS** | `probe.txt`: `com.hkmc.micom.bcm` 21/23 and `com.hkmc.micom.usm` 583/584 return the same values as root and as uid 10999 (5 and 8, non-zero; BCM and USM agree). |
| G8 Tooling | **NOT A BLOCKER; WORK NEEDED** | HMG Leafia Plus `lpatch zipadd` builds the whole output in a 12 MB static buffer (`MAXOUT`, `tools/lpatch/lpatch.c:70-71`, 485–536). The APK is 161,884,916 bytes, so Plan 2 needs a streaming `zipadd`: copy entries from the stock file to the output file one at a time, add or replace `classes.dex`, write the central directory from the offsets it records, and keep the output deterministic. |

### G3 details

- **How stock pages are built:** each page is a `k` subclass built in `SplitScreenHome.cM()`
  in the same steps: `new X(Context, Handler)`; `bh(id)`; add it to the page list `oa`; send
  message 0x3f5 (`SPLIT_HANDLER_MSG_VIEW_STACK_SET`, `arg1` = id, `obj` = page) to handler field
  `pv` (class `n/l`). Its handler `M.a` stores `page.getView()` at index id of the view stack
  `ob`. Then `od.set(id, <title string id>)` and `ou++`. Every stock page inflates layouts
  in `getView()`; none builds its views in code. `subviewExample/a`
  (`SplitScreenViewSubViewTest`, id 0x16) is an empty stub whose `getView()` returns null.
- **How an id becomes a view:** through the packed switch in `aS(I)` (ids 0..0x16; any other
  id logs `getSplitScreenViewFromId is not matched viewId` and returns null). There is no map
  or factory a page could register with.
- **How paging walks the ids:** in the order of the active `ss_setting*` key, through
  `updateFlipperOrder` and `initFlipper` (G2). Pages are 640 × 720 px, stacked vertically in
  `SlidingView`.
- **What a code-only page must hook:** (1) `cM()`: build our page and register it as id 4
  (the steps above, title `string/climate` 0x7f0a0090, which already exists); (2) `aS(I)`
  case 4 (label `pswitch_1b8` in this build): return our page instead of the `qq()`/`m/k`
  branch. No other change is needed for the page to show.
- **Obstacles:**
  - Ids are fixed to 0..0x16. `k.bh(I)` turns any id outside -1 < id < 0x17 into 2 (AV).
    `ob` and `od` are built with 23 slots, and `aS` covers only 0..0x16. A new id (0x17 or
    above) would need at least four more patches, so we reuse 4.
  - Case 4 in `aS` checks `isSSClimateRemoved` and `isFrontMTCType`, so the case body has to
    be replaced, not just switched on.
  - `cM()` runs once per process start, on a worker thread (runnable `n`, started from
    `onCreate`, logs `initSplitScreenView finished`). It is written to run again: it clears the
    lists and calls `f()` on any page left from before. Our constructor must be safe off the
    UI thread, and `getView()` runs later from the `pv` handler.
  - The view stack, the enable/disable loop and `addViewToStack` all use `oa` and `ob`, so
    registering only in `aS` isn't enough.
  - Resources are compiled in as numeric ids. Our page uses none of its own; it may reuse
    existing ones (`string/climate`, drawables) by id only for this exact build.
  - The panel runs as uid 1000 and is persistent. An exception in our page brings down the
    whole right-hand screen, so every entry point needs a try/catch.
- **Uninstall safety:** a leftover `4` in `ss_setting*` doesn't break the stock panel:
  `updateFlipperOrder` drops it because `aS(4)` returns null.

## ss_setting decoding

Ids from automotive.jar `SplitScreenManager`, pages from `aS(I)`.

| Key | Value | Pages in order |
|---|---|---|
| `ss_setting` (guest/default) | `0,21,3,2,9,10` | EV, Electricity use, Map, Media, Compass, Clock |
| `ss_setting_dr1` (profile 1; **the active list** per the `advancedList` log line) | `0,21,3,2,6,9,10` | EV, Electricity use, Map, Media, Weather, Compass, Clock |
| `ss_setting_dr2` | `0,21,3,2,9,10` | same as default |

- **0** = `EVMODE_VIEW` (shares the value with `HEVMODE_VIEW`, `PHEVMODE_VIEW`,
  `CHARGING_STATION_INFO_VIEW`). On an EV (`com.hkmc.hardware.ev`) it is `w/o`
  `SplitScreenViewEv` (layout `ev_system_layout*`: battery %, charging state, range; view ids
  `energy_info_battery`, `energy_info_range_value`), or `w/C` `SplitScreenViewEvStationInfo`
  when `n/v.pU()` is true. **Most likely the owner's "energy" page** (its view ids are literally
  `energy_info_*`).
- **21** = `ELEC_USE_VIEW`: `w/f` `SplitScreenViewEvElectricityUse` (layout
  `ev_electricity_use_layout`: drivetrain / climate / electronics / battery care shares). Needs
  the EV feature and `n/v.rz()`. The other candidate for "energy".
- 3 = `MAP_VIEW` (`o/h` `SplitScreenViewNavi`), 2 = `AV_VIEW` (media; the page depends on the
  current source), 6 = `WEATHER_VIEW` (`SplitScreenViewWeather`), 9 = `COMPASS_VIEW` (`l/a`
  `SplitScreenViewCompass`), 10 = `CLOCK_VIEW` (`clock/g` `SplitScreenViewClock`).
- 4 = `CLIMATE_VIEW` is in none of the three lists.

## What Plan 2 needs

1. **Hooks in RightScreenHome** (this build's names):
   - `SplitScreenHome.cM()`: after the stock `m/k` setup, build our page (a `k` subclass in our
     injected classes), `bh(4)`, `oa.add`, message 0x3f5 to `pv` with `arg1` 4, set
     `od[4]` = 0x7f0a0090, `ou++`. If an instance is left from before, release it first (stock
     pages do this with `f()`).
   - `SplitScreenHome.aS(I)` case 4: return our page. Leave `m/k` (field `oO`) alone; it is
     still built and used apart from the page. As an option, our page could show `m/k`'s
     `getView()` for temperatures, since that view already works on this car.
   - No hook is needed for the page list.
2. **Page list:** our app adds `4` to `ss_setting`, `ss_setting_dr1` and `ss_setting_dr2`
   (remembering the old values), then sends broadcast
   `com.android.settings.splitscreen_settings.changed` (optional `splitscreen_moveto`=4). It
   watches the three keys and puts 4 back if stock settings code removes it (G5). It removes
   4 when the page is turned off or on uninstall.
3. **View id:** reuse CLIMATE **4** (a new id 0x17 or above is blocked; see G3 obstacles).
4. **Page geometry and style:** the window is 640 × 720 px (density 240; `main_view` 640 px
   wide); each page is a full 720 px slot. Stock page layouts are 424 dip wide (≈636 px) ×
   420–480 dip, with a transparent root over the panel background (`ss_bg`, drawable
   `split_bg_screen_bottom_on_b`). Text uses the system default typeface: no fonts or `assets/`
   in the APK. Text sizes run 18–44 sp. Text colour (`text_ev_normal`, `ss_text_*`) is
   `#62797F` by default and `#B6C1C8` with `uiMode` night. Stock pages restyle in
   `a(boolean, Configuration)`, so our page must handle day/night there.
5. **Shown/hidden:** no callback tells a page it is the current one.
   - `k.f(boolean)` is enable/disable: it runs from `a(k, Z)` (logs
     `updateSplitScreenViewEnabled`) when the id enters or leaves the active list.
   - Page changes go to `SlidingView`'s listener `B.bu(int)`, which `u` implements (logs
     `onPageChanged page = `) without passing it to pages.
   - Panel show/hide arrives as broadcasts `com.mobis.intent.action.SPLIT_SCREEN_SHOW` /
     `SPLIT_SCREEN_HIDE` (receiver logs `ACTION_SCREEN_SHOW`/`ACTION_SCREEN_HIDE`).
   - Use our own view's `onAttachedToWindow`/`onWindowVisibilityChanged` plus `f(boolean)`. If
     the page must know it is the current one, add a one-call hook in `u.bu` that passes the
     current child's `F.iO()` id.
6. **Free space:** `/data` 814.2M free (stage there), `/system` 265.7M free, `/cache` 152.1M.
   APK 161,884,916 B, odex 2,137,656 B.
7. **Streaming `zipadd`** in a vendored `lpatch` (G8). Also needed: `smali` 2.5.2 for
   reassembly (`smali a -j 1 --api 18`). Only baksmali is in the scratch tools, so fetch it
   with a checksum. RightScreenHome has no binder stubs of its own (all stubs come from
   automotive.jar), so Leafia's stub-name warning doesn't apply, but compare the rebuilt dex
   class by class anyway.
8. **Process facts:** the panel runs as uid 1000 (`android.uid.system`, platform-signed, so
   Leafia's "stock APK + injected `classes.dex`" path applies). Our page code inside it must
   send no vehicle commands: it only binds to our app's service and forwards taps.

## Open questions

1. **ThemeSettings.apk** (`com.android.themesettings.splitscreensettings.*`, 8,687,389 B +
   odex 637,776 B) is the likely list editor and wasn't collected. Does its screen rewrite
   `ss_setting*` without 4 when the owner edits the list? Add it to the kit.
2. Collect `ss_list`, `ss_list_dr1`, `ss_list_dr2` (the offered/order lists that
   `SplitScreenDataBackup` uses) with the next kit run.
3. Which page is the owner's "energy" page: 0 (battery/range) or 21 (electricity use)? Ask the
   owner.
4. Install: how the persistent system-uid panel behaves under `pm disable
   com.mobis.splitscreen.right` while its APK is swapped (does the right screen come back
   cleanly after `pm enable`?). Test on the mock, then the car.
5. Our app writing `Settings.System` keys from a normal uid with `WRITE_SETTINGS` (allowed
   on stock KitKat; this vendor's SettingsProvider wasn't checked).
6. Dalvik verification of the patched `cM`/`aS` and our page can't be run off-car.

Decision: GO

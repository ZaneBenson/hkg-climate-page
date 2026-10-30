# hkg-climate-page Plan 1: collection kit, split-screen research, go/no-go

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Collect the split-screen panel app from the owner's car and decide, with evidence, whether the climate page can be added (go/no-go), before any app or patch is built.

**Architecture:** A read-only NEMESIS USB kit (shell script, modelled on HMG Leafia Plus's `kits/leafia`) copies named stock files, free space, memory, the split-screen page settings and a read-only binder access probe to the drive. Off-car research of the collected files produces a written report with pass/fail against fixed go/no-go criteria. Plan 2 (our app + page patch) is written only after a "go".

**Tech Stack:** POSIX `sh` (the unit runs Android 4.4 `mksh`/toolbox: no `printf`, no `tail`), `zip`, baksmali 2.5.2 for research.

**Spec:** `docs/superpowers/specs/2026-10-30-hkg-climate-page-design.md` (approved 2026-10-30)

## Global Constraints

- Target: 2023 US Ioniq 5, `ro.build.product` prefix `NEEV23.USA.`, firmware `260507`.
- The kit is **read-only**: it writes only to the USB drive (and nothing under `/system` or `/data` except reading). No vehicle commands. The only binder calls are the four getters listed in Task 2.
- Never collect per-car secrets: no VIN, serials, accounts, keys, paired-phone data, Wi-Fi passwords, `/data` contents.
- No manufacturer files in the repo. Stock files stay in the owner's private tree (currently `~/code/LEAFIA/private/stock`, collected output under `~/code/HKGCLIMATE`); `.gitignore` blocks `*.apk`, `*.odex`, `*.jar` and the kit output folder.
- Kit shell: no `printf`, `tail`, `seq`, `[[ ]]`; use `echo`, `read` loops, `[ ]`.
- Licence GPL-3.0. Credits: HMG Leafia Plus (GPL-3.0: kit approach, `lpatch`), `hkmc-coldstart-heat` by eclipseempire (MIT: seat call), NEMESIS by xwtk (AGPL-3.0).
- Commit messages describe the change in plain words, ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. No links to Claude sessions.

## Review Focus

1. **Drive too small or missing files:** a file that can't be copied (no space, not present) is recorded as `FAIL`/`missing` in `collected.txt` and the kit carries on and still shows its finish message. Test in Task 2.
2. **Kit run on the wrong car or firmware:** it still collects (useful for research) but `summary.txt` says `car: other` so nobody mistakes it for the target. Test in Task 2.
3. **`settings` command missing or failing:** the page-list lines read `unavailable` instead of aborting. Test in Task 2.
4. **The probe uid can't be used** (`nemesis root 10999` refused): the probe file records the error text; the kit continues. Test in Task 2.
5. **Re-running the kit on the same drive:** old output is replaced, not mixed with the new run. Test in Task 2.

---

### Task 1: Repository foundation

**Files:**
- Create: `README.md`, `NOTICE.md`, `.gitignore`, `CLAUDE.md`
- Already present (uncommitted): `LICENSE` (GPL-3.0), `docs/superpowers/specs/2026-10-30-hkg-climate-page-design.md`, this plan

**Interfaces:**
- Produces: the rules file `CLAUDE.md` later tasks follow; `.gitignore` entries `dist/`, `HKGCLIMATE/`, `*.apk`, `*.odex`, `*.jar`, `*.dex`.

- [ ] **Step 1: Write `README.md`**: one paragraph on what the plugin will do (climate page on the right split screen: seat heat/cool buttons, outside temperature, presets; settings app), status "research, not installable yet", target car, link to the spec.
- [ ] **Step 2: Write `NOTICE.md`** with the three credits from Global Constraints, each with its licence and what we use from it.
- [ ] **Step 3: Write `CLAUDE.md`**: the "Rules carried over" list from the spec verbatim, the private-file rule, the kit shell limits, the commit rules above, and where stock files live.
- [ ] **Step 4: Write `.gitignore`** with the entries above.
- [ ] **Step 5: Verify nothing private is staged**

Run: `git add -A && git status --short`
Expected: only `.gitignore CLAUDE.md LICENSE NOTICE.md README.md docs/superpowers/specs/... docs/superpowers/plans/...`; no `.apk/.odex/.jar`.

- [ ] **Step 6: Commit**

```bash
git commit -m "Start hkg-climate-page: design, plan, licence and project rules"
```

### Task 2: Read-only collection kit

**Files:**
- Create: `kit/NEMESIS.data`, `kit/NEMESIS.sh`, `kit/FILES`, `kit/README.txt`, `tools/build-kit.sh`
- Test: `tests/kit/run.sh`, `tests/kit/stubs/` (`getprop`, `logcat`, `settings`, `service`, `nemesis`, `md5`, `df`, `am`)

**Interfaces:**
- Produces (on the drive, folder `HKGCLIMATE/`):
  - `summary.txt`: `kit hkg-climate 1`, `date ...`, `product <ro.build.product>`, `car target|other`, `df` output, `/proc/meminfo` first 4 lines, `ss_setting <value|unavailable>`, `ss_setting_dr1 ...`, `ss_setting_dr2 ...`.
  - `collected.txt`: one line per `kit/FILES` entry, `ok   <path>` / `FAIL <path>` / `missing <path>`.
  - `probe.txt`: for each getter, the `service call` output as uid 0 and as uid 10999.
  - `stock/app/`, `stock/priv-app/`, `stock/init.environ.rc`, `stock/app-listing.txt`, `stock/priv-app-listing.txt`.
  - `logcat-full.txt` (`logcat -d -v time`), written first.
- `tools/build-kit.sh [out.zip]` -> default `dist/hkg-climate-kit.zip` containing `NEMESIS.data NEMESIS.sh FILES README.txt`, deterministic (`zip -qrX`).

`kit/NEMESIS.data` (same format as Leafia's kit):

```
[nemesis]
version=1.0
type=execute
path=NEMESIS.sh
```

`kit/FILES` (one absolute path per line, `#` comments allowed):

```
/system/app/RightScreenHome.apk
/system/app/RightScreenHome.odex
/system/app/DisplaySettings.apk
/system/app/DisplaySettings.odex
/system/app/Settings_W.apk
/system/app/Settings_W.odex
/system/app/SettingsCommon.apk
/system/app/SettingsCommon.odex
```

Probe getters (read the service's cache only; verified in the stock `BCMService`/`USMService`):

| Binder | Code | Method |
|---|---|---|
| `com.hkmc.micom.bcm` | 21 | `getDrvSeatHeatState` |
| `com.hkmc.micom.bcm` | 23 | `getAstSeatHeatState` |
| `com.hkmc.micom.usm` | 583 | `getFrontSeatHeat_DrvState` |
| `com.hkmc.micom.usm` | 584 | `getFrontSeatHeat_AstState` |

Probe command form: `service call <binder> <code>` (as root) and `nemesis root 10999 -c 'service call <binder> <code>'`.

- [ ] **Step 1: Write the failing host test `tests/kit/run.sh`**

It builds a fake root in a temp dir (`$T/system/app/RightScreenHome.apk` etc. with small dummy contents, `DisplaySettings.odex` deliberately absent, `$T/init.environ.rc`, `$T/proc/meminfo`), puts `tests/kit/stubs` first on `PATH`, runs `kit/NEMESIS.sh` from a fake drive dir with `KIT_SYSROOT=$T`, and asserts:

```sh
check "summary has kit line"       grep -q '^kit hkg-climate 1$' "$D/HKGCLIMATE/summary.txt"
check "target car detected"        grep -q '^car target$' "$D/HKGCLIMATE/summary.txt"
check "apk copied"                 cmp -s "$T/system/app/RightScreenHome.apk" "$D/HKGCLIMATE/stock/app/RightScreenHome.apk"
check "missing file recorded"      grep -q '^missing /system/app/DisplaySettings.odex$' "$D/HKGCLIMATE/collected.txt"
check "ss_setting recorded"        grep -q '^ss_setting 10,13,2$' "$D/HKGCLIMATE/summary.txt"
check "probe has 8 calls"          test "$(grep -c '^call ' "$D/HKGCLIMATE/probe.txt")" = 8
check "logcat written"             test -s "$D/HKGCLIMATE/logcat-full.txt"
# Review Focus cases, each a separate run:
# FAKE_PRODUCT=ECOHMC.USA.0000.V015.012.260507   -> grep -q '^car other$'
# FAKE_SETTINGS_FAIL=1                            -> grep -q '^ss_setting unavailable$'
# FAKE_NEMESIS_UID_FAIL=1                         -> probe.txt contains the stub's error text, finish line present
# FAKE_CP_FAIL=RightScreenHome.apk                -> grep -q '^FAIL /system/app/RightScreenHome.apk$', finish line present
# second run after touching $D/HKGCLIMATE/stale  -> stale file gone
```

Stubs echo canned output (`getprop ro.build.product` -> `${FAKE_PRODUCT:-NEEV23.USA.0000.V015.012.260507}`; `settings get system ss_setting` -> `10,13,2`, `_dr1`/`_dr2` -> `null`; `service call` -> `Result: Parcel(00000000 00000002 '........')`; `nemesis root 10999 -c ...` -> runs the command, or prints `nemesis: uid refused` when `FAKE_NEMESIS_UID_FAIL=1`). The finish line is the last line of `summary.txt`: `done`.

- [ ] **Step 2: Run it and verify it fails**

Run: `sh tests/kit/run.sh`
Expected: FAIL (`kit/NEMESIS.sh` missing).

- [ ] **Step 3: Write `kit/NEMESIS.sh`**

Structure, in this order: header comment (read-only, what is and isn't collected); `SYS=${KIT_SYSROOT:-}` prefix on every `/system`, `/proc`, `/init.environ.rc` read; `PATH` set as in the Leafia kit with `${KIT_TEST_PATH}` allowed in front; `KIT=$(pwd)`, `OUT=$KIT/HKGCLIMATE`, `rm -r "$OUT"` then create folders; logcat first; then summary, page settings, probe, file copies from `FILES`, listings, `init.environ.rc`; `done` last; then the stock toast (`am broadcast -a com.hkmc.intent.action.TOAST -e com.hkmc.intent.extra.TOAST_MESSAGE "hkg-climate kit finished, unplug the drive" --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true`). `car target` when the product starts with `NEEV23.USA.` and ends with `.260507`, else `car other`. Copy with `cp`, recording `ok`/`FAIL`/`missing` like Leafia's `grab`.

- [ ] **Step 4: Run the test and verify it passes**

Run: `sh tests/kit/run.sh`
Expected: every `check` prints `ok`, exit 0.

- [ ] **Step 5: Write `kit/README.txt`**: drive needs ≥ 400 MB free (exFAT/FAT32); copy the zip's contents to the drive root, no other NEMESIS files; car on, plug in, tap Yes, wait for the toast; the folder holds Hyundai files and logs: keep private, never post; nothing on the car is changed.
- [ ] **Step 6: Write `tools/build-kit.sh` and check it is deterministic**

Run: `tools/build-kit.sh /tmp/a.zip && tools/build-kit.sh /tmp/b.zip && cmp /tmp/a.zip /tmp/b.zip && unzip -l /tmp/a.zip`
Expected: no `cmp` output; the zip lists exactly `NEMESIS.data NEMESIS.sh FILES README.txt`.

- [ ] **Step 7: Commit**

```bash
git add kit tools/build-kit.sh tests/kit
git commit -m "Read-only USB kit: collect the split-screen apps, free space, page settings and a seat-state read check"
```

### Task 3: Collection run on the owner's car

Manual; the owner runs it. No code.

- [ ] **Step 1:** Build `dist/hkg-climate-kit.zip` (`tools/build-kit.sh`) and give the owner its path plus `kit/README.txt` steps.
- [ ] **Step 2:** Owner copies the resulting `HKGCLIMATE` folder to `~/code/HKGCLIMATE` (outside every repo).
- [ ] **Step 3: Check the run**

Run: `grep -c '^ok ' ~/code/HKGCLIMATE/collected.txt; grep -E '^(car|ss_setting)' ~/code/HKGCLIMATE/summary.txt; tail -1 ~/code/HKGCLIMATE/summary.txt`
Expected: `8` (or the missing files explained), `car target`, the three `ss_setting` lines, `done`. If not, fix the kit (back to Task 2) and re-run.

### Task 4: Split-screen research and go/no-go report

Research, not TDD. Work only in the scratchpad or a private folder; the report contains names, ids, sizes and conclusions, never decompiled code beyond short identifiers.

**Files:**
- Create: `docs/research/2026-10-30-split-screen.md`

**Interfaces:**
- Consumes: `~/code/HKGCLIMATE/` (Task 3), the framework in `~/code/LEAFIA/private/stock/framework`.
- Produces: the report, with a table `Criterion | Result | Evidence` and a final line `Decision: GO` or `Decision: NO-GO`, plus, for GO, the facts Plan 2 needs (listed in Step 4).

- [ ] **Step 1: Deodex** `RightScreenHome.odex`, `DisplaySettings.odex`, `Settings_W.odex`, `SettingsCommon.odex` with `baksmali x -a 18 -d <framework dir>` (BOOTCLASSPATH order from `stock/init.environ.rc`). Note any class that fails.
- [ ] **Step 2: Answer the criteria** and fill the table:

| # | Criterion | Pass when |
|---|---|---|
| G1 | Panel app | `RightScreenHome` declares package `com.mobis.splitscreen.right` and creates the `TYPE_SPLIT_WINDOW_RIGHT` window / `SplitScreenHome` binder |
| G2 | Page mechanism | The code mapping view ids -> page views and the paging over `ss_setting` are identified (class + method names) |
| G3 | Code-only page | A new page can be built entirely in Java code (views created programmatically, no new resources, no `AndroidManifest.xml` change), reachable by adding a view id to the list the panel pages through |
| G4 | Space | From `summary.txt` `df`: `/data` free ≥ 2 × `RightScreenHome.apk` size (backup + staging) and `/system` free ≥ (new APK size − old APK size − old odex size) + 10 MB margin |
| G5 | Page list | Where `ss_setting` is edited (which app/class) and whether the panel shows an id that list's settings screen doesn't offer (decides whether the settings app needs a patch) |
| G6 | Climate id 4 | Whether `RightScreenHome` already implements CLIMATE (4), and what it shows |
| G7 | Read access | `probe.txt`: uid 10999 results equal uid 0 results for all four getters (a normal app can read seat state) |
| G8 | Tooling | `lpatch zipadd` holds the whole APK in memory (`MAXOUT` 12 MB); record the streaming change Plan 2 needs (not a blocker) |

**GO** when G1–G4 pass. G5–G8 shape Plan 2 and never block on their own.

- [ ] **Step 3: Cross-check** each pass/fail against a second piece of evidence where one exists (a log line in `logcat-full.txt` naming the panel package, a resource name, a string). Mark single-source results as such.
- [ ] **Step 4: For GO, record what Plan 2 needs:** the class/method to hook for a new page and for the page list, the view id to use (reuse CLIMATE 4 vs a new id ≥ 0x17), the panel's page size and background colour/fonts used by stock pages, how a page learns it is shown/hidden, the free-space numbers, and the streaming `zipadd` requirement.
- [ ] **Step 5: Commit the report**

```bash
git add docs/research/2026-10-30-split-screen.md
git commit -m "Split-screen research: page mechanism, space, read check and go/no-go"
```

### Task 5: Owner decision

- [ ] **Step 1:** Summarise the report for the owner in plain words: the decision, the reasons, and for GO, what Plan 2 will build first (our app's seat logic and service, then the page patch).
- [ ] **Step 2:** NO-GO: stop, per the spec. GO: write Plan 2 from the spec and the report.

# hkg-climate-page

A NEMESIS plugin for Hyundai/Kia/Genesis Gen5W head units that adds a **climate page** to
the right-side split screen: one-tap driver and passenger seat buttons (heating below a
switch temperature, cooling above it), the outside temperature, and preset buttons that
apply several climate settings at once. A small settings app sets the switch temperature
and builds the presets.

**Status: version 1: seat buttons send the approved command; install from dist/hkg-climate-usb.zip.**
Built, not yet tried on the car. The owner's on-car checklist is
[docs/testing/2026-10-30-version-1.md](docs/testing/2026-10-30-version-1.md). Earlier checklists:
[test build 2](docs/testing/2026-10-30-test-build-2.md),
[test build 1](docs/testing/2026-10-30-test-build-1.md).

## Build

Prerequisites (Linux host):

- Android SDK at `~/Android/sdk` (or set `ANDROID_SDK`) with platform `android-19` and
  build-tools `30.0.3`
- JDK 11 or newer (`javac`, `java`, `keytool`)
- `clang` and `ld.lld`
- `qemu-user` (`qemu-arm`), for the tests
- for `tools/build-panel.sh` only: a Python venv at `private/toolchain/venv` with
  `bsdiff4==1.3.1`
- optional: `mksh`, so the package tests run the hooks under the head unit's shell

Then:

1. `tools/toolchain.sh fetch` (smali, baksmali and JUnit jars into `private/toolchain/`)
2. `tools/toolchain.sh check`: every line should say `ok` (the venv line only matters for the
   panel build)
3. `tools/build-app.sh`, then `tools/build-package.sh` (writes `dist/hkg-climate-page.zip`)
4. Optional: `tools/build-kit.sh` and `tools/build-usb.sh` (writes `dist/hkg-climate-usb.zip`:
   the package plus the read-only kit on one USB stick; the install runs on insert, the kit
   from NEMESIS Manager's "Run USB NEMESIS.sh")

The first `build-app.sh` run creates the signing key in `private/keys/`: back it up. The panel
patch files are committed, so `tools/build-panel.sh` is not needed to package.

## About

Target car: 2023 US Hyundai Ioniq 5 (`NEEV23.USA.`, firmware 260507).

- Design: [docs/superpowers/specs/2026-10-30-hkg-climate-page-design.md](docs/superpowers/specs/2026-10-30-hkg-climate-page-design.md)
- Current plan: [docs/superpowers/plans/2026-10-30-version-1.md](docs/superpowers/plans/2026-10-30-version-1.md)

Licence: GPL-3.0 (see `LICENSE`). Credits: `NOTICE.md`.

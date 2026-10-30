# hkg-climate-page: notes for Claude

A NEMESIS plugin for Gen5W head units (Android 4.4.2, Dalvik): a climate page on the
right-side split screen, plus a settings app. Design and plans: `docs/superpowers/`.
Target: 2023 US Ioniq 5, `NEEV23.USA.`, firmware 260507.

## Rules (adopted 2026-10-30)

- Vehicle commands only through the stock services.
- Each new command (setting + values) is confirmed from a log of the stock controls on the
  owner's car, written up (service, method, values, caller uid), and approved by the owner
  before any build sends it. The first build carrying a new command logs
  `would set <setting> <value>` instead of sending, until the owner approves it.
- No per-car secrets collected or published (VIN, serials, accounts, keys, `/data`).
- Anything written to `/system`: stock checksum gate, backup, uninstall restores exact bytes.
- Only the newest firmware per region is supported (US 260507); other firmware is refused
  with a message to update.
- Never send a value we haven't seen the stock controls send: the USM seat service passes
  any number straight to the car with no range check.

## Private files

- No manufacturer apps, libraries, jars or decompiled code in this repo: only our code,
  delta patches, checksums, and research notes naming classes/ids.
- Stock files and logs are never committed. They live in `private/` in this repo
  (git-ignored; this plugin's kit output is `private/HKGCLIMATE`) or outside it
  (`~/code/LEAFIA/private/stock`, from the first kit runs). Deodex scratch goes in a
  scratch folder.

## Kit shell

The unit's shell is mksh/toolbox: no `printf`, `tail`, `head`, `seq`, `[[ ]]`. Use `echo`,
`read` loops and `[ ]`. Remount only via `nemesis remount rw|ro`.

## Commits

Describe the change in plain words. End with
`Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. No links to Claude chats or
sessions.

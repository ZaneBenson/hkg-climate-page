# Version 1.1.0: climate presets, on-car checklist

Release v1.0.0 was the previous public release (presets of the two seats). Version 1.1.0 lets a
preset also set the steering-wheel heat, eco mode, the climate (off, auto or manual), one
cabin temperature, fan speed, A/C, airflow and sync, using the commands you approved
(`docs/notes/2026-10-30-status-and-findings.md`, "Climate preset commands"). The preset editor
now uses a dropdown for every item, and Save checks the write and shows any error in full.
Presets you already saved still load, with every new item on "Leave as is".

Package: `dist/hkg-climate-usb.zip` (the package and the kit on one stick; see its
`README-USB.txt`). Capture tool: `dist/hkg-climate-capture.zip` (see
`docs/testing/2026-10-31-climate-capture.md`). Only US firmware 260507 is supported.

This installs over the build that's on the car now. Have the car on (ready to drive) for the
tests. Please note anything odd, even if it seems small. Nothing is sent at boot, when you
open settings, or as a retry: only a tap on a preset (or a seat button) sends.

## Steps

1. Install 1.1.0 from the combined stick. If the install says there isn't enough space on
   /system, restart the head unit and plug the stick in again (the update has to finish
   removing the old build first). Check that your old presets are still there.
2. In the Climate Page settings, add or edit a preset and look at the editor: every item is a
   dropdown. Set Climate to Auto: fan, A/C and airflow grey out. Set it to Off: temperature,
   fan, A/C, airflow and sync grey out too. Seats, steering wheel and eco are never greyed.
   Tap Save: the editor closes. If it says "could not save: ...", write down the full text,
   leave the editor, and run the kit at once.
3. With the car on, build two presets:
   - "Cold": driver and passenger seat heat high, steering wheel on, climate Auto,
     temperature 70 °F.
   - "Hot": driver and passenger seat cool high, climate Manual, A/C on, fan 6, airflow face,
     temperature 68 °F.
4. Start the capture tool now, as in the capture checklist (first "Run USB NEMESIS.sh" starts
   it; it stops by itself after 30 minutes, or on the second run). Keep it running through
   step 8.
5. With the climate off, tap "Cold". Write down what you see: the seats go to heat high, the
   wheel warms, the climate comes on in auto, then the temperature steps down or up to 70.
   Open the settings screen: "Last command" says what was done, and names anything it skipped
   or that did not reach its target.
6. Tap "Hot": A/C on, airflow face, fan 6, temperature 68 °F, seats to cool high; the car
   leaves auto.
7. Make a preset with Climate: Off (and, for example, the seats set to Off and the wheel
   off). Tap it: the climate turns off, and the seats and wheel still apply. Nothing wakes the
   climate again.
8. Eco: make a preset with Eco on and one with Eco off. Tap each: the eco mode follows, and
   it matches what Vehicle Settings shows.
9. Stop the capture tool (second run), then run the kit from Manager ("Run USB NEMESIS.sh" on
   the combined stick) before you edit anything or restart. The kit keeps only about the last minute of the
   Android log, and a restart wipes it. Send the `CAPTURE` folder and the `HKGCLIMATE`
   folder, with notes of what you saw for each tap (what moved, what did not, how long it
   took).
10. Restart: the presets are still there, and nothing changes by itself.

## If something goes wrong

- The "Last command" line names an item that did not follow (for example `no change`,
  `send refused`, `send failed`, or an item skipped because its state was unknown): stop
  tapping, run the kit right away and send the folder. Several results are joined with "; ".
- The climate is off or the car isn't on (ready to drive) and a preset only sets the seats
  and wheel: this is expected for items that need the climate. Turn the car on and tap again.
- A preset keeps pressing a temperature or fan step and the value does not move: it stops
  after a short wait and reports it. Do not keep tapping; send the kit and the capture.
- The steering wheel ends up in the wrong state (warm when the preset says off, or the
  reverse): write down what it was before the tap, and send the kit.
- The climate does something you did not ask for (turns off, leaves auto, or a value jumps):
  stop tapping, run the kit and the capture, and describe it.
- The right-hand screen goes blank or keeps restarting: uninstall HKG Climate Page in NEMESIS
  (it puts the original panel files back), then restart the unit.
- A message says the install could not be fully undone: do not restart the unit. Look at the
  NEMESIS log and tell us what it says.

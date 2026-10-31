# Climate capture: on-car checklist

This session records which command and value each stock climate control sends, so presets
can use the same ones. The capture tool (`dist/hkg-climate-capture.zip`) is **read-only**: it
records the Android log and reads the climate state, and sends nothing to the car. Each new
command still needs your approval before any build sends it.

## Setup

1. Unzip `hkg-climate-capture.zip` onto the root of a USB stick. Use a stick **without** a
   `NEMESIS.data` (not the install stick).
2. Car on (ready to drive), stick in. In NEMESIS Manager, choose **Run USB NEMESIS.sh**. Wait
   for "capture 1 started".
3. Write down the head unit's clock time.

## Presses

Press one control at a time, about **5 seconds apart**. Write each one down in order: which
control, which screen (the head unit screen, the physical climate panel, or voice), and
anything odd. If something isn't on your car, write "not there" and go on.

1. **Temperature (screen):**
   - Driver up 3 times, then down 3 times. Then the passenger the same way.
   - If you can drag a temperature bar instead, drag it once and note where you let go.
   - Sync (or "dual") on, then off. Driver only on, then off, if your car has it.
2. **Fan (screen):** up 2 times, down 2 times, then fan off, then Auto.
3. **A/C (screen):** A/C off, then on.
4. **Airflow (screen):** open the airflow choices. Write down how many there are, then pick each
   one once, in order, ending on the one you started with.
5. **Heat button (screen):** if there's a "HEAT" button, press it on, then off.
6. **Steering-wheel heat (screen):** on, then off. If it has levels, go through them.
7. **Climate eco mode:** find it (it may be in the vehicle or climate settings). Turn it on, then
   off, and write down where it was.
8. **Voice:** for each pair, wait for the car to answer, and write down what it said.
   - "Rear defroster on", then "Rear defroster off". Also note whether the mirror-heat light
     comes on with it.
   - "Front defroster on", then "Front defroster off".
   - "Recirculation on", then "Recirculation off" (or "fresh air").
9. **Physical panel:** press rear defrost, front defrost and recirculation, each on then off.

## Finish

10. In NEMESIS Manager, choose **Run USB NEMESIS.sh** again. Wait for "capture 1 stopped and
    saved", then unplug the stick. If it says the log "stopped early", note that.
11. Send the `CAPTURE` folder and your notes. It holds the Android log, so keep it private.

The capture stops by itself after 30 minutes. If that happens, the second run still saves it.

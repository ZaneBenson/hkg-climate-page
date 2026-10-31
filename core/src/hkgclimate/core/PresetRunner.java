package hkgclimate.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies the steering-wheel, eco and cabin items of a preset (the seats are ClimateCore's), one
 * item at a time in the apply order: wheel, eco, climate off (which ends the cabin part), auto,
 * A/C, airflow, fan, sync, driver temperature, passenger temperature. For each item it reads the
 * car, decides, sends at most one command through the CheckedCabinSender, then reads every
 * POLL_MS until the item's value changes or STEP_WAIT_MS passes. A stepped item (fan,
 * temperature) decides its next press from the new value, up to a cap.
 *
 * Two commands are never closer than COMMAND_GAP_MS (the car silently ignored a temperature
 * press sent 100 ms after the previous one; the stock screen's presses are about 450 ms apart):
 * when a command is due sooner, the runner waits out the gap, then reads the car again and
 * decides again. Only a temperature or fan step that did not change its value RETRY_WAIT_MS
 * after the press is pressed again, at most MAX_RETRIES times for that step, each press counted
 * toward the item's cap; a forced press (leaving auto) and every other item are sent once and
 * reported if they did not change. Nothing is sent for an item whose state is unknown.
 * Single-threaded: call it, and run the scheduler's tasks, on one thread.
 */
public final class PresetRunner {
    public static final long POLL_MS = 100;
    public static final long STEP_WAIT_MS = 1500;
    /** The least time between two commands of a run (and before its first, after a seat send). */
    public static final long COMMAND_GAP_MS = 400;
    /** How long a temperature or fan press may leave the value unchanged before it is pressed again. */
    public static final long RETRY_WAIT_MS = 500;
    /** Extra presses of one temperature or fan step that did not change anything. */
    public static final int MAX_RETRIES = 2;
    public static final int FAN_CAP = 10;
    public static final int TEMP_CAP = 25;

    private static final String NO_CHANGE = "no change after 1.5 s";
    private static final String NO_CHANGE_RETRIED = "no change after ";
    private static final String UNKNOWN_STATE = "unknown state";

    // the items, in the apply order
    private static final int WHEEL = 0;
    private static final int ECO = 1;
    private static final int CLIMATE_OFF = 2;
    private static final int AUTO = 3;
    private static final int AC = 4;
    private static final int AIRFLOW = 5;
    private static final int FAN = 6;
    /** Sends nothing: reports whether the car left auto after the manual items. */
    private static final int MANUAL = 7;
    private static final int SYNC = 8;
    private static final int DRIVER_TEMP = 9;
    private static final int PASSENGER_TEMP = 10;

    // what decide() found
    private static final int DONE = 0;
    /** Nothing to do and nothing to name (the passenger follows sync, is not shown, ...). */
    private static final int QUIET = 1;
    /** Not done; the reason is in why. */
    private static final int NOT_DONE = 2;
    /** Send the command in sendFlag and sendValue. */
    private static final int SEND = 3;
    /** Send nothing, but wait for the value to change (the passenger following sync). */
    private static final int WAIT = 4;

    private static final int WHEEL_FLAG = -1;
    private static final int ECO_FLAG = -2;

    private final CheckedCabinSender sender;
    private final CabinReader reader;
    private final Scheduler scheduler;
    private final SendLog log;

    private boolean busy;
    private String last;

    // the run in progress
    private Preset preset;
    private int[] items;
    private int index;
    private int presses;
    private boolean waited;
    private boolean sentThisWait;
    /** Retries used by the current step, and whether the call now deciding is one. */
    private int retries;
    private boolean retrying;
    /** Whether the last press was a forced one (leaving auto): never retried. */
    private boolean pressForced;
    /** Time since the last command, as the runner counts it; negative while a wheel release is due. */
    private long quietMs;
    /** How long the scheduled gap wait lasts. */
    private long gapWaitMs;
    private String before;
    private int polls;
    private int unit = CabinState.UNKNOWN;
    private final List<String> done = new ArrayList<String>();
    private final List<String> notDone = new ArrayList<String>();

    // decide()'s outputs
    private int sendFlag;
    private int sendValue;
    private String why;

    public PresetRunner(CheckedCabinSender sender, CabinReader reader, Scheduler scheduler,
            SendLog log) {
        this.sender = sender;
        this.reader = reader;
        this.scheduler = scheduler;
        this.log = log;
    }

    /**
     * Starts applying the preset's wheel, eco and cabin items; the first command, if any, is sent
     * before this returns. False (and nothing read or sent) while a run is busy, or for a preset
     * that is null, fails Preset.check, or has none of these items.
     */
    public boolean start(Preset p) { return start(p, 0); }

    /**
     * Like start(Preset), but with firstDelayMs above zero nothing is read or sent before that
     * time has passed (the caller has just sent seat commands); true while the run is waiting.
     */
    public boolean start(Preset p, long firstDelayMs) {
        if (busy || p == null || !p.hasRunnerItems() || Preset.check(p) != null) return false;
        preset = p;
        items = itemsOf(p);
        index = 0;
        done.clear();
        notDone.clear();
        unit = CabinState.UNKNOWN;
        quietMs = COMMAND_GAP_MS;
        retries = 0;
        retrying = false;
        pressForced = false;
        busy = true;
        if (firstDelayMs > 0) {
            try {
                scheduler.after(firstDelayMs, new Runnable() {
                    @Override public void run() {
                        if (busy) begin();
                    }
                });
            } catch (Throwable t) {
                stop(t);
            }
            return true;
        }
        begin();
        return true;
    }

    /** True from start() until the last item is finished. */
    public boolean busy() { return busy; }

    /**
     * One line about the last finished run: what reached its target and what didn't and why, like
     * "Cold: wheel on, auto, 70 °F (3 steps); not done: A/C on (no change after 1.5 s)". While a
     * run is busy, "<name>: applying"; null before any run.
     */
    public String lastResult() {
        return busy ? preset.name + ": applying" : last;
    }

    private static int[] itemsOf(Preset p) {
        int[] all = new int[11];
        int n = 0;
        if (p.wheel != Preset.LEAVE) all[n++] = WHEEL;
        if (p.eco != Preset.LEAVE) all[n++] = ECO;
        if (p.climate == Preset.CLIMATE_OFF) {
            // climate off ends the cabin part (the preset's other cabin items are LEAVE anyway)
            all[n++] = CLIMATE_OFF;
        } else {
            if (p.climate == Preset.CLIMATE_AUTO) all[n++] = AUTO;
            if (p.ac != Preset.LEAVE) all[n++] = AC;
            if (p.airflow != Preset.LEAVE) all[n++] = AIRFLOW;
            if (p.fan != Preset.LEAVE) all[n++] = FAN;
            if (p.climate == Preset.CLIMATE_MANUAL) all[n++] = MANUAL;
            if (p.sync != Preset.LEAVE) all[n++] = SYNC;
            if (p.tempF != Preset.LEAVE) {
                all[n++] = DRIVER_TEMP;
                all[n++] = PASSENGER_TEMP;
            }
        }
        int[] out = new int[n];
        System.arraycopy(all, 0, out, 0, n);
        return out;
    }

    /** Starts the item at index, or ends the run after the last. */
    private void begin() {
        while (index < items.length) {
            presses = 0;
            retries = 0;
            retrying = false;
            waited = false;
            try {
                if (decideAndAct()) return; // waiting for the car
            } catch (Throwable t) {
                stop(t);
                return;
            }
            index++;
        }
        end();
    }

    /**
     * Reads, decides and acts for the current item. True when it is now waiting for the car (a
     * poll is scheduled); false when the item is finished.
     */
    private boolean decideAndAct() {
        int item = items[index];
        CabinState s = read();
        int d = decide(item, s);
        if (d == DONE) {
            done.add(label(item) + stepsText(item));
            return false;
        }
        if (d == QUIET) return false;
        if (d == NOT_DONE) {
            notDone.add(label(item) + " (" + why + ")");
            return false;
        }
        if (d == WAIT) {
            if (waited) {
                notDone.add(label(item) + " (did not follow sync)");
                return false;
            }
            waited = true;
            sentThisWait = false;
        } else {
            int cap = cap(item);
            if (presses >= cap) {
                notDone.add(label(item) + " ("
                        + (cap == 1 ? "not reached" : "stopped after " + cap + " presses") + ")");
                return false;
            }
            if (quietMs < COMMAND_GAP_MS) {
                // too soon after the last command: wait out the gap, then read and decide again
                gapWaitMs = COMMAND_GAP_MS - quietMs;
                scheduleGap();
                return true;
            }
            if (!send()) {
                notDone.add(label(item) + " (not sent)");
                return false;
            }
            presses++;
            if (retrying) retries++;
            retrying = false;
            sentThisWait = true;
            // the wheel's release is a command too, WHEEL_RELEASE_MS after the press
            quietMs = item == WHEEL ? -CheckedCabinSender.WHEEL_RELEASE_MS : 0;
        }
        before = key(item, s);
        polls = 0;
        schedulePoll();
        return true;
    }

    private void scheduleGap() {
        scheduler.after(gapWaitMs, new Runnable() {
            @Override public void run() { gapElapsed(); }
        });
    }

    /** The gap is over: the item reads the car again and decides again. */
    private void gapElapsed() {
        try {
            if (!busy) return;
            quietMs += gapWaitMs;
            if (decideAndAct()) return;
        } catch (Throwable t) {
            stop(t);
            return;
        }
        index++;
        begin();
    }

    private void schedulePoll() {
        scheduler.after(POLL_MS, new Runnable() {
            @Override public void run() { poll(); }
        });
    }

    /** One read while waiting: on a change the item decides again; after STEP_WAIT_MS it ends. */
    private void poll() {
        try {
            if (!busy) return;
            int item = items[index];
            polls++;
            quietMs += POLL_MS;
            CabinState s = read();
            boolean changed = !key(item, s).equals(before)
                    && !(item == WHEEL && sender.wheelPending());
            boolean canRetry = canRetry(item);
            if (changed) {
                retries = 0; // the press landed; the next step has its own retries
                if (decideAndAct()) return;
            } else if (canRetry && polls * POLL_MS >= RETRY_WAIT_MS) {
                // the press changed nothing: read again (decideAndAct does) and press again
                retrying = true;
                boolean waiting = decideAndAct();
                if (waiting) return;
                retrying = false;
            } else if (!canRetry && polls * POLL_MS >= STEP_WAIT_MS) {
                // a forced press (manual in auto) that changed nothing still leaves the item at target
                int d = decide(item, s);
                if (d == DONE || (d == QUIET && presses > 0)) {
                    done.add(label(item) + stepsText(item));
                } else if (d != QUIET) {
                    String reason = !sentThisWait ? "did not follow sync"
                            : retries > 0 ? NO_CHANGE_RETRIED + presses + " presses" : NO_CHANGE;
                    notDone.add(label(item) + " (" + reason + ")");
                }
            } else {
                schedulePoll();
                return;
            }
        } catch (Throwable t) {
            stop(t);
            return;
        }
        index++;
        begin();
    }

    /**
     * Whether a press of this item that changed nothing may be pressed again: an ordinary
     * temperature or fan step (not a forced press) with retries left.
     */
    private boolean canRetry(int item) {
        return (item == FAN || item == DRIVER_TEMP || item == PASSENGER_TEMP)
                && sentThisWait && !pressForced && retries < MAX_RETRIES;
    }

    /** Ends the run at the current item after an unexpected failure (for example the scheduler). */
    private void stop(Throwable t) {
        String text;
        try {
            text = String.valueOf(t);
        } catch (Throwable e) {
            text = t.getClass().getName();
        }
        notDone.add(label(items[index]) + " (stopped: " + text + ")");
        end();
    }

    private void end() {
        busy = false;
        StringBuilder sb = new StringBuilder(preset.name).append(':');
        if (!done.isEmpty()) sb.append(' ').append(join(done));
        if (!notDone.isEmpty()) {
            sb.append(done.isEmpty() ? " " : "; ").append("not done: ").append(join(notDone));
        }
        if (done.isEmpty() && notDone.isEmpty()) sb.append(" nothing to do");
        last = sb.toString();
        try {
            log.line(last);
        } catch (Throwable t) {
            // the result is kept
        }
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p);
        }
        return sb.toString();
    }

    private CabinState read() {
        CabinState s;
        try {
            s = reader.read();
        } catch (Throwable t) {
            s = null;
        }
        if (s == null) s = CabinState.NONE;
        if (s.unit == CabinState.UNIT_CELSIUS || s.unit == CabinState.UNIT_FAHRENHEIT) unit = s.unit;
        return s;
    }

    private boolean send() {
        if (sendFlag == WHEEL_FLAG) return sender.wheelPress(scheduler);
        if (sendFlag == ECO_FLAG) return sender.eco(sendValue);
        return sender.climate(sendFlag, sendValue);
    }

    /** Sets sendFlag and sendValue (for SEND) or why (for NOT_DONE). */
    private int decide(int item, CabinState s) {
        Preset p = preset;
        pressForced = false;
        switch (item) {
            case WHEEL:
                if (!onOff(s.wheel)) return unknown();
                if (s.wheel == p.wheel) return DONE;
                return send(WHEEL_FLAG, 1);
            case ECO:
                if (!onOff(s.eco)) return unknown();
                if (s.eco == p.eco) return DONE;
                return send(ECO_FLAG, p.eco == 1 ? CheckedCabinSender.ECO_SEND_ON
                        : CheckedCabinSender.ECO_SEND_OFF);
            case CLIMATE_OFF:
                if (!s.cabinKnown()) return unknown();
                if (s.opState == CabinState.OP_OFF) return DONE;
                return send(CheckedCabinSender.FLAG_CLIMATE_OFF, 1);
            case AUTO:
                if (!s.cabinKnown()) return unknown();
                if (s.opState == CabinState.OP_ON && s.auto == 1) return DONE;
                // from off one press turns it on in auto; never a second press when auto is on
                if (s.opState == CabinState.OP_OFF || s.auto == 0) {
                    return send(CheckedCabinSender.FLAG_AUTO, 1);
                }
                return unknown();
            case AC:
                if (!s.cabinKnown() || !onOff(s.ac)) return unknown();
                if (s.ac == p.ac && !forced(s)) return DONE;
                return send(CheckedCabinSender.FLAG_AC, p.ac);
            case AIRFLOW:
                if (!s.cabinKnown() || s.airflow < 0 || s.airflow > Preset.AIRFLOW_MAX) return unknown();
                if (s.airflow == p.airflow && !forced(s)) return DONE;
                return send(CheckedCabinSender.FLAG_AIRFLOW, p.airflow);
            case FAN:
                return decideFan(s);
            case MANUAL:
                if (!s.cabinKnown()) return unknown();
                if (s.opState == CabinState.OP_OFF) return notDone("climate off");
                if (s.auto == 1) return notDone("still in auto");
                if (s.auto == 0) return DONE;
                return unknown();
            case SYNC:
                if (!s.cabinKnown() || !onOff(s.sync)) return unknown();
                if (s.sync == p.sync) return DONE;
                // 2 turns it on (sent only when off), 1 turns it off (sent only when on)
                return send(CheckedCabinSender.FLAG_SYNC, p.sync == 1 ? 2 : 1);
            case DRIVER_TEMP:
                if (!tempKnown(s) || !shown(s.driverTemp)) return unknown();
                return step(CheckedCabinSender.FLAG_DRIVER_TEMP, s.driverTemp, s.unit);
            case PASSENGER_TEMP:
                return decidePassenger(s);
            default:
                return notDone("unknown item");
        }
    }

    private int decideFan(CabinState s) {
        if (!s.cabinKnown() || s.fanRaw < 1 || s.fanRaw > Preset.FAN_MAX + 1) return unknown();
        int target = preset.fan + 1;
        // from auto (raw 1) one press goes to speed 1; the next presses step from there
        if (s.fanRaw == 1) return send(CheckedCabinSender.FLAG_FAN, CheckedCabinSender.UP);
        if (s.fanRaw == target) {
            if (!forced(s)) return DONE;
            pressForced = true;
            // one press to leave auto: at an end it changes nothing, elsewhere the next steps undo it
            return send(CheckedCabinSender.FLAG_FAN, target == Preset.FAN_MIN + 1
                    ? CheckedCabinSender.DOWN : CheckedCabinSender.UP);
        }
        return send(CheckedCabinSender.FLAG_FAN,
                s.fanRaw < target ? CheckedCabinSender.UP : CheckedCabinSender.DOWN);
    }

    /**
     * After the driver side: nothing when the passenger is at the target, not shown, or driver-only
     * is on; a wait (no press) while sync is on, since a passenger press would turn sync off;
     * otherwise steps. When the driver side could not be read, it was named there already.
     */
    private int decidePassenger(CabinState s) {
        if (!tempKnown(s) || !shown(s.driverTemp) || !shown(s.passengerTemp)) return QUIET;
        if (stepsOf(s.passengerTemp, s.unit) == targetSteps(s.unit)) return presses > 0 ? DONE : QUIET;
        if (s.sync == 1) return WAIT;
        if (s.sync != 0) return unknown();
        if (s.driverOnly == 1) return QUIET;
        if (s.driverOnly != 0) return unknown();
        return step(CheckedCabinSender.FLAG_PASSENGER_TEMP, s.passengerTemp, s.unit);
    }

    /** One press towards the target, or DONE when the temperature is there. */
    private int step(int flag, float now, int unit) {
        int diff = targetSteps(unit) - stepsOf(now, unit);
        if (diff == 0) return DONE;
        return send(flag, diff > 0 ? CheckedCabinSender.UP : CheckedCabinSender.DOWN);
    }

    /**
     * The target in steps of the display unit (1 °F, or 0.5 °C as twice the Celsius value),
     * clamped to the stock range 62..82 °F or 17.0..27.0 °C.
     */
    private int targetSteps(int unit) {
        if (unit == CabinState.UNIT_CELSIUS) {
            int half = (int) Math.round(PresetChoices.celsiusHalf(preset.tempF) * 2);
            return Math.max(34, Math.min(54, half));
        }
        return Math.max(Preset.TEMP_MIN_F, Math.min(Preset.TEMP_MAX_F, preset.tempF));
    }

    private static int stepsOf(float t, int unit) {
        return unit == CabinState.UNIT_CELSIUS ? Math.round(t * 2) : Math.round(t);
    }

    /** Manual items are sent once even if they match while the car is in auto, to leave it. */
    private boolean forced(CabinState s) {
        return preset.climate == Preset.CLIMATE_MANUAL && presses == 0
                && s.opState == CabinState.OP_ON && s.auto == 1;
    }

    private static boolean tempKnown(CabinState s) {
        return s.cabinKnown()
                && (s.unit == CabinState.UNIT_CELSIUS || s.unit == CabinState.UNIT_FAHRENHEIT);
    }

    private static boolean shown(float t) { return !Float.isNaN(t) && t > 0; }

    private static boolean onOff(int v) { return v == 0 || v == 1; }

    private int send(int flag, int value) {
        sendFlag = flag;
        sendValue = value;
        return SEND;
    }

    private int unknown() { return notDone(UNKNOWN_STATE); }

    private int notDone(String reason) {
        why = reason;
        return NOT_DONE;
    }

    private static int cap(int item) {
        if (item == FAN) return FAN_CAP;
        if (item == DRIVER_TEMP || item == PASSENGER_TEMP) return TEMP_CAP;
        return 1;
    }

    /** What changes when the car takes the item's command (the climate waking counts). */
    private static String key(int item, CabinState s) {
        switch (item) {
            case WHEEL: return String.valueOf(s.wheel);
            case ECO: return String.valueOf(s.eco);
            case CLIMATE_OFF: return String.valueOf(s.opState);
            case AUTO: return s.opState + "/" + s.auto;
            case AC: return s.opState + "/" + s.auto + "/" + s.ac;
            case AIRFLOW: return s.opState + "/" + s.auto + "/" + s.airflow;
            case FAN: return s.opState + "/" + s.auto + "/" + s.fanRaw;
            case SYNC: return s.opState + "/" + s.sync;
            case DRIVER_TEMP: return s.opState + "/" + s.unit + "/" + s.driverTemp;
            case PASSENGER_TEMP: return s.opState + "/" + s.unit + "/" + s.passengerTemp + "/" + s.sync;
            default: return "";
        }
    }

    private String label(int item) {
        Preset p = preset;
        switch (item) {
            case WHEEL: return "wheel " + onOffWords(p.wheel);
            case ECO: return "eco " + onOffWords(p.eco);
            case CLIMATE_OFF: return "climate off";
            case AUTO: return "auto";
            case AC: return "A/C " + onOffWords(p.ac);
            case AIRFLOW: return "airflow " + PresetChoices.airflow()[p.airflow].label.toLowerCase();
            case FAN: return "fan " + p.fan;
            case MANUAL: return "manual";
            case SYNC: return "sync " + onOffWords(p.sync);
            case DRIVER_TEMP: return temperatureWords();
            case PASSENGER_TEMP: return "passenger " + temperatureWords();
            default: return "item " + item;
        }
    }

    /** The target as the car shows it: "70 °F", or "21.0 °C" on a Celsius car. */
    private String temperatureWords() {
        if (unit == CabinState.UNIT_CELSIUS) return targetSteps(unit) / 2.0 + " °C";
        return targetSteps(CabinState.UNIT_FAHRENHEIT) + " °F";
    }

    /** " (n steps)" for a stepped item that sent presses. */
    private String stepsText(int item) {
        if (cap(item) == 1 || presses == 0) return "";
        return " (" + presses + (presses == 1 ? " step)" : " steps)");
    }

    private static String onOffWords(int v) { return v == 1 ? "on" : "off"; }
}

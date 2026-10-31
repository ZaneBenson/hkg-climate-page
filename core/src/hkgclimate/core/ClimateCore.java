package hkgclimate.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What the climate page knows and what a tap does. Single-threaded: call everything from the
 * main looper. Feeding it state or presets never sends a command; only onTap, onSeatOff and
 * onPresetTap do, once per request id, and none of them while the preset runner is busy. A swap
 * sends nothing.
 *
 * The applied-preset highlight: the last preset tapped is active from the tap, while its run is
 * busy, and afterwards while every item that reached its target still reads at it (see
 * PresetMatch); the first check that finds one that differs or is unknown clears it for good.
 * The tap's seats are not checked until SEAT_GRACE_MS after the tap. Another preset tap or a
 * change to the presets clears it too.
 */
public final class ClimateCore {
    /** How long after a preset tap the seats may read differently before it counts as drift. */
    public static final long SEAT_GRACE_MS = 3000;
    /** How often the app reads the cabin, wheel and eco for onHighlightCheck after a run. */
    public static final long HIGHLIGHT_POLL_MS = 2000;

    private static final int SEATS = Seat.values().length;
    private static final String DOT = " · ";
    private static final int UNIT_FAHRENHEIT = 2;
    private static final int NO_VALUE = -1;

    private final SeatCommander sender;
    private final PresetRunner runner;
    private final Clock clock;
    private final TapGate gate = new TapGate();
    private final int[] seatValue = new int[SEATS];
    private final int[] seatOption = new int[SEATS];
    /** The seat's next tap uses the other mode (set by a swap while the seat is off). */
    private final boolean[] swapped = new boolean[SEATS];
    private List<Preset> presets = new ArrayList<Preset>();

    // the highlight
    private int active = -1;
    private Preset activePreset;
    private long tapAtMs;
    /** The tap's seats that count: sent to, or already at, the preset's target. */
    private final boolean[] seatCounts = new boolean[SEATS];
    /** The tap started the runner, so its doneItems() are this preset's. */
    private boolean runStarted;
    /** The last cabin reading given after the run (null: none since the tap). */
    private CabinState lastCabin;

    private int switchTempF;
    private boolean ready;
    private OutsideTemp outside = OutsideTemp.UNKNOWN;
    private int displayUnit = UNIT_FAHRENHEIT;
    private Runnable listener;
    private PageState last;

    /**
     * runner applies the wheel, eco and cabin items of a preset; null runs only seat items.
     * clock times the seat grace after a preset tap (the app gives SystemClock.elapsedRealtime).
     */
    public ClimateCore(SeatCommander sender, int switchTempF, PresetRunner runner, Clock clock) {
        if (clock == null) throw new IllegalArgumentException("clock");
        this.sender = sender;
        this.runner = runner;
        this.clock = clock;
        this.switchTempF = switchTempF;
        for (int i = 0; i < SEATS; i++) seatValue[i] = NO_VALUE;
        last = build();
    }

    public void onSeatsReady(boolean ready) {
        this.ready = ready;
        checkHighlight(clock.now());
        changed();
    }

    /** A seat's new state; a swap ends once the seat is no longer off. */
    public void onSeatState(Seat s, int value) {
        int i = s.ordinal();
        seatValue[i] = value;
        if (value != SeatValues.OFF) swapped[i] = false;
        checkHighlight(clock.now());
        changed();
    }

    public void onSeatOption(Seat s, int option) { seatOption[s.ordinal()] = option; changed(); }

    public void onOutside(OutsideTemp t) {
        outside = t == null ? OutsideTemp.UNKNOWN : t;
        changed();
    }

    public void onDisplayUnit(int rawUnit) { displayUnit = rawUnit; changed(); }

    public void setSwitchTempF(int f) { switchTempF = f; changed(); }

    /** The last value reported for the seat, as is (even if unknown); -1 before any. */
    public int seatValue(Seat s) { return seatValue[s.ordinal()]; }

    /** Called after every change that alters pageState(); not called for the initial state. */
    public void setListener(Runnable onChange) { listener = onChange; }

    /**
     * Handles one tap. True when a command was issued: the request id is new, the seats are
     * ready, no preset is being applied, and the seat's current value is one we know how to step
     * from.
     */
    public boolean onTap(Seat s, long requestId) {
        if (!gate.accept(requestId)) return false;
        if (runnerBusy()) return false;
        if (!ready) return false;
        int i = s.ordinal();
        int next = SeatStepper.next(seatValue[i], nextMode(s));
        if (next == -1) return false;
        sender.set(s, next);
        return true;
    }

    /**
     * Flips the mode an off seat's next tap turns it on in (heat and cool). Sends nothing. True
     * when it flipped: the request id is new, the seats are ready, no preset is being applied, the
     * seat is off, and it has the heat and cool option. Otherwise nothing changes.
     */
    public boolean onSeatSwap(Seat s, long requestId) {
        if (!gate.accept(requestId)) return false;
        if (runnerBusy()) return false;
        if (!ready) return false;
        int i = s.ordinal();
        if (seatValue[i] != SeatValues.OFF) return false;
        if (seatOption[i] != SeatValues.HEAT_AND_COOL_OPTION) return false;
        swapped[i] = !swapped[i];
        changed();
        return true;
    }

    /**
     * Turns an on seat off: one command, SeatValues.OFF, through the seat commander. True when it
     * was sent: the request id is new, the seats are ready, no preset is being applied, and the
     * seat is on (heat or cool).
     */
    public boolean onSeatOff(Seat s, long requestId) {
        if (!gate.accept(requestId)) return false;
        if (runnerBusy()) return false;
        if (!ready) return false;
        if (SeatValues.modeOf(seatValue[s.ordinal()]) == null) return false;
        sender.set(s, SeatValues.OFF);
        return true;
    }

    /** The slot of the applied preset the page highlights, or -1. */
    public int activePreset() { return active; }

    /**
     * The app's highlight check: s is the cabin, wheel and eco as read now, nowMs the clock's time.
     * While the preset's run is busy it changes nothing (the reading is not kept); after it, the
     * reading is kept for seat changes and the highlight clears if a counted item drifted.
     */
    public void onHighlightCheck(CabinState s, long nowMs) {
        if (active < 0 || activeRunBusy()) return;
        lastCabin = s == null ? CabinState.NONE : s;
        checkHighlight(nowMs);
        changed();
    }

    /**
     * Replaces the saved presets (null is none); only the first PresetList.MAX are kept. Any
     * change (an edit, a delete, a new order) clears the highlight; the same presets keep it.
     */
    public void setPresets(List<Preset> list) {
        List<Preset> copy = new ArrayList<Preset>();
        if (list != null) {
            for (Preset p : list) {
                if (p != null && copy.size() < PresetList.MAX) copy.add(p);
            }
        }
        if (!copy.equals(presets)) clearHighlight();
        presets = copy;
        changed();
    }

    /**
     * Applies the preset in slot index. Seats first, when the seats are ready: for the driver, then
     * the passenger, one command for each seat that has a target, is in a state we know, is not
     * already at the target, and, for a cool target, has the heat and cool option. Then the
     * runner starts on the wheel, eco and cabin items, if the preset has any (its first command
     * waits PresetRunner.COMMAND_GAP_MS after seat commands). Returns how many
     * seat commands were sent, plus 1 if the runner started; 0 for a request id seen before, a
     * bad or empty slot, a preset that fails Preset.check, or while the runner is busy.
     *
     * The tapped preset becomes the highlighted one when its seats were considered (the seats are
     * ready) or its runner started, even if nothing needed sending; a tap that could apply
     * nothing clears the highlight. A refused tap leaves it as it was.
     */
    public int onPresetTap(int index, long requestId) {
        if (!gate.accept(requestId)) return 0;
        if (runnerBusy()) return 0;
        if (index < 0 || index >= presets.size()) return 0;
        Preset p = presets.get(index);
        // a preset that does not check (built by hand, not through the editor) sends nothing at all
        if (Preset.check(p) != null) {
            clearHighlight();
            changed();
            return 0;
        }
        int sent = 0;
        boolean[] counts = new boolean[SEATS];
        boolean seatsConsidered = ready && p.hasSeatItems();
        if (seatsConsidered) {
            for (Seat s : Seat.values()) {
                int i = s.ordinal();
                int target = p.target(s);
                if (target == Preset.LEAVE || !SeatValues.isKnown(target)) continue;
                if (!SeatValues.isKnown(seatValue[i])) continue;
                if (seatValue[i] == target) {
                    counts[i] = true;
                    continue;
                }
                if (SeatValues.modeOf(target) == Mode.COOL
                        && seatOption[i] != SeatValues.HEAT_AND_COOL_OPTION) continue;
                sender.set(s, target);
                counts[i] = true;
                sent++;
            }
        }
        // the runner's first command comes at least COMMAND_GAP_MS after the seat commands
        long firstDelay = sent > 0 ? PresetRunner.COMMAND_GAP_MS : 0;
        boolean started = runner != null && p.hasRunnerItems() && runner.start(p, firstDelay);
        if (started) sent++;
        if (seatsConsidered || started) {
            active = index;
            activePreset = p;
            tapAtMs = clock.now();
            System.arraycopy(counts, 0, seatCounts, 0, SEATS);
            runStarted = started;
            lastCabin = null;
        } else {
            clearHighlight();
        }
        changed();
        return sent;
    }

    private boolean runnerBusy() { return runner != null && runner.busy(); }

    private boolean activeRunBusy() { return runStarted && runnerBusy(); }

    private void clearHighlight() {
        active = -1;
        activePreset = null;
        runStarted = false;
        lastCabin = null;
        for (int i = 0; i < SEATS; i++) seatCounts[i] = false;
    }

    /**
     * Clears the highlight when a counted item drifted: the tap's counted seats once SEAT_GRACE_MS
     * has passed, and the run's done items against the last cabin reading taken after the run.
     * Nothing while the run is busy.
     */
    private void checkHighlight(long nowMs) {
        if (active < 0 || activeRunBusy()) return;
        Set<String> items = new HashSet<String>();
        if (runStarted && lastCabin != null) items.addAll(runner.doneItems());
        if (nowMs - tapAtMs >= SEAT_GRACE_MS) {
            if (seatCounts[Seat.DRIVER.ordinal()]) items.add(PresetMatch.DRIVER_SEAT);
            if (seatCounts[Seat.PASSENGER.ordinal()]) items.add(PresetMatch.PASSENGER_SEAT);
        }
        int[] seats = new int[SEATS];
        for (int i = 0; i < SEATS; i++) seats[i] = ready ? seatValue[i] : NO_VALUE;
        if (PresetMatch.drifted(activePreset, seats, lastCabin, items)) clearHighlight();
    }

    public PageState pageState() { return build(); }

    private Mode currentMode(Seat s) {
        return SeatStepper.chooseMode(usableOutside(), switchTempF, seatOption[s.ordinal()]);
    }

    /** The mode an off seat's next tap turns it on in: the automatic choice, or the other after a swap. */
    private Mode nextMode(Seat s) {
        int i = s.ordinal();
        Mode m = currentMode(s);
        if (!swapped[i] || seatOption[i] != SeatValues.HEAT_AND_COOL_OPTION) return m;
        return m == Mode.COOL ? Mode.HEAT : Mode.COOL;
    }

    /** The reading, only when the page can show it in the display unit; otherwise unknown (heat). */
    private OutsideTemp usableOutside() {
        return outside.display(displayUnit) == null ? OutsideTemp.UNKNOWN : outside;
    }

    private void changed() {
        PageState now = build();
        if (now.equals(last)) return;
        last = now;
        if (listener != null) listener.run();
    }

    private PageState build() {
        String[] text = new String[SEATS];
        int[] level = new int[SEATS];
        int[] kind = new int[SEATS];
        int[] tapKind = new int[SEATS];
        boolean[] enabled = new boolean[SEATS];
        boolean[] swap = new boolean[SEATS];
        boolean[] off = new boolean[SEATS];
        for (Seat s : Seat.values()) {
            int i = s.ordinal();
            String name = s == Seat.DRIVER ? "Driver" : "Passenger";
            String words = ready ? SeatValues.words(seatValue[i]) : null;
            if (words == null) {
                text[i] = name + DOT + "not available";
                continue;
            }
            text[i] = name + DOT + words;
            level[i] = SeatValues.level(seatValue[i]);
            Mode m = SeatValues.modeOf(seatValue[i]);
            kind[i] = m == Mode.HEAT ? 1 : m == Mode.COOL ? 2 : 0;
            // what the next tap does: an on seat steps within its mode, an off seat turns on as onTap would
            Mode next = m != null ? m : nextMode(s);
            tapKind[i] = next == Mode.COOL ? 2 : 1;
            enabled[i] = true;
            swap[i] = m == null && seatOption[i] == SeatValues.HEAT_AND_COOL_OPTION;
            off[i] = m != null;
        }
        String temp = outside.display(displayUnit);
        String top = temp == null ? "Outside temperature unknown" : "Outside " + temp;
        String[] presetNames = new String[PresetList.MAX];
        boolean[] presetEnabled = new boolean[PresetList.MAX];
        for (int i = 0; i < presets.size() && i < PresetList.MAX; i++) {
            presetNames[i] = presets.get(i).name;
            Preset p = presets.get(i);
            presetEnabled[i] = (runner != null && p.hasRunnerItems()) || (ready && p.hasSeatItems());
        }
        return new PageState(top, text, level, kind, tapKind, enabled, presetNames, presetEnabled,
                active, swap, off);
    }
}

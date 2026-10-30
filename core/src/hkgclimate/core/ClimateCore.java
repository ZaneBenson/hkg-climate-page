package hkgclimate.core;

/**
 * What the climate page knows and what a tap does. Single-threaded: call everything from the
 * main looper. Feeding it state, or changing the approval, never sends a command; only onTap
 * does, once per request id: to the sender when approved, otherwise to the log-only commander.
 */
public final class ClimateCore {
    private static final int SEATS = Seat.values().length;
    private static final String DOT = " · ";
    private static final int UNIT_FAHRENHEIT = 2;
    private static final int NO_VALUE = -1;

    private final SeatCommander sender;
    private final SeatCommander logOnly;
    private final TapGate gate = new TapGate();
    private final int[] seatValue = new int[SEATS];
    private final int[] seatOption = new int[SEATS];

    private int switchTempF;
    private boolean ready;
    private boolean approved;
    private OutsideTemp outside = OutsideTemp.UNKNOWN;
    private int displayUnit = UNIT_FAHRENHEIT;
    private Runnable listener;
    private PageState last;

    public ClimateCore(SeatCommander sender, SeatCommander logOnly, int switchTempF) {
        this.sender = sender;
        this.logOnly = logOnly;
        this.switchTempF = switchTempF;
        for (int i = 0; i < SEATS; i++) seatValue[i] = NO_VALUE;
        last = build();
    }

    public void onSeatsReady(boolean ready) { this.ready = ready; changed(); }

    public void onSeatState(Seat s, int value) { seatValue[s.ordinal()] = value; changed(); }

    public void onSeatOption(Seat s, int option) { seatOption[s.ordinal()] = option; changed(); }

    public void onOutside(OutsideTemp t) {
        outside = t == null ? OutsideTemp.UNKNOWN : t;
        changed();
    }

    public void onDisplayUnit(int rawUnit) { displayUnit = rawUnit; changed(); }

    public void setSwitchTempF(int f) { switchTempF = f; changed(); }

    /** Off by default. While on, taps go to the sender; while off, to the log-only commander. */
    public void setApproved(boolean approved) { this.approved = approved; changed(); }

    /** The last value reported for the seat, as is (even if unknown); -1 before any. */
    public int seatValue(Seat s) { return seatValue[s.ordinal()]; }

    /** Called after every change that alters pageState(); not called for the initial state. */
    public void setListener(Runnable onChange) { listener = onChange; }

    /**
     * Handles one tap. True when a command was issued: the request id is new, the seats are
     * ready and the seat's current value is one we know how to step from.
     */
    public boolean onTap(Seat s, long requestId) {
        if (!gate.accept(requestId)) return false;
        if (!ready) return false;
        int i = s.ordinal();
        int next = SeatStepper.next(seatValue[i], currentMode(s));
        if (next == -1) return false;
        (approved ? sender : logOnly).set(s, next);
        return true;
    }

    public PageState pageState() { return build(); }

    private Mode currentMode(Seat s) {
        return SeatStepper.chooseMode(usableOutside(), switchTempF, seatOption[s.ordinal()]);
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
        boolean[] enabled = new boolean[SEATS];
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
            enabled[i] = true;
        }
        String temp = outside.display(displayUnit);
        String tap = currentMode(Seat.DRIVER) == Mode.COOL ? "taps will cool" : "taps will heat";
        String top = (temp == null ? "Outside temperature unknown" : "Outside " + temp) + DOT + tap;
        return new PageState(top, text, level, kind, enabled, !approved);
    }
}

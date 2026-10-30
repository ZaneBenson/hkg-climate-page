package hkgclimate.core;

/**
 * Sends a seat step through the stock service, but only a value the stock controls are known to
 * send (2..8) for a known seat. Anything else is refused with no call. Failures are logged, never
 * thrown, and never retried. This is the safety boundary for the send: nothing it calls (the log,
 * the clock, the watch) can make set() throw or make the stock call twice.
 */
public final class CheckedSeatSender implements SeatCommander {
    private final UsmCalls calls;
    private final SendLog log;
    private final SendWatch watch;
    private final Clock clock;
    // volatile: a reader may be on another thread than the sender
    private volatile String last;

    public CheckedSeatSender(UsmCalls calls, SendLog log, SendWatch watch, Clock clock) {
        this.calls = calls;
        this.log = log;
        this.watch = watch;
        this.clock = clock;
    }

    /** The stock service method name for a seat; the one place this mapping lives. */
    public static String methodName(Seat seat) {
        return seat == Seat.DRIVER ? "setFrontSeatHeatDrvStep" : "setFrontSeatHeatAstStep";
    }

    @Override
    public void set(Seat seat, int value) {
        try {
            send(seat, value);
        } catch (Throwable t) {
            // only the log text, the clock or the watch can get here, after the one call (if any):
            // never call again
        }
    }

    private void send(Seat seat, int value) {
        if (seat == null) {
            record("send refused <unknown seat> " + value);
            return;
        }
        String method = methodName(seat);
        if (!SeatValues.isKnown(value)) {
            record("send refused " + method + " " + value);
            return;
        }
        try {
            if (seat == Seat.DRIVER) calls.driver(value);
            else calls.passenger(value);
        } catch (Throwable t) {
            record("send failed " + method + " " + value + ": " + describe(t));
            return;
        }
        record("sent " + method + " " + value);
        watch.sent(seat, value, clock.now());
    }

    /** The last text this sender logged (sent, refused or failed), or null before any. */
    public String lastResult() {
        return last;
    }

    /** The throwable's text, or its class name when even that throws. */
    private static String describe(Throwable t) {
        try {
            return String.valueOf(t);
        } catch (Throwable e) {
            return t.getClass().getName();
        }
    }

    /** Keeps the text as lastResult() and logs it; a failing log is ignored. */
    private void record(String text) {
        last = text;
        try {
            log.line(text);
        } catch (Throwable t) {
            // the result is kept; a broken log must not stop the watch or reach the caller
        }
    }
}

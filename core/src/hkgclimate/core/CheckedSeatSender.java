package hkgclimate.core;

/**
 * Sends a seat step through the stock service, but only a value the stock controls are known to
 * send (2..8). Anything else is refused with no call. Failures are logged, never thrown, and
 * never retried.
 */
public final class CheckedSeatSender implements SeatCommander {
    private final UsmCalls calls;
    private final SendLog log;
    private final SendWatch watch;
    private final Clock clock;
    private String last;

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
        String method = methodName(seat);
        if (!SeatValues.isKnown(value)) {
            record("send refused " + method + " " + value);
            return;
        }
        try {
            if (seat == Seat.DRIVER) calls.driver(value);
            else calls.passenger(value);
        } catch (Throwable t) {
            record("send failed " + method + " " + value + ": " + t);
            return;
        }
        record("sent " + method + " " + value);
        watch.sent(seat, value, clock.now());
    }

    /** The last text this sender logged (sent, refused or failed), or null before any. */
    public String lastResult() {
        return last;
    }

    private void record(String text) {
        last = text;
        log.line(text);
    }
}

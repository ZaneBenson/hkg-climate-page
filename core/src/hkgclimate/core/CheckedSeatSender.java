package hkgclimate.core;

/**
 * Sends a seat step through the stock service, but only a value the stock controls are known to
 * send (2..8) for a known seat. Anything else is refused with no call. Failures are logged, never
 * thrown, and never retried. A sent value is handed to the watch just before the stock call
 * (and taken off it if the call fails). This is the safety boundary for the send: nothing it
 * calls (the log, the clock, the watch) can make set() throw or make the stock call twice.
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
            // the log, the clock and the watch are guarded in send(); anything else that gets
            // here is never followed by another call
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
        // pending before the call: the seat state can report the value before the call returns
        watchSend(seat, value);
        try {
            if (seat == Seat.DRIVER) calls.driver(value);
            else calls.passenger(value);
        } catch (Throwable t) {
            forgetSend(seat);
            record("send failed " + method + " " + value + ": " + describe(t));
            return;
        }
        record("sent " + method + " " + value);
    }

    /** Hands the send to the watch; a failing clock or watch is ignored and the send goes ahead. */
    private void watchSend(Seat seat, int value) {
        try {
            watch.sent(seat, value, clock.now());
        } catch (Throwable t) {
            // the send is just not watched
        }
    }

    /** Takes a failed send off the watch; a failing watch is ignored. */
    private void forgetSend(Seat seat) {
        try {
            watch.clear(seat);
        } catch (Throwable t) {
            // nothing more to do
        }
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

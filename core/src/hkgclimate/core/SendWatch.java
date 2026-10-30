package hkgclimate.core;

/**
 * Notices a send the car ignored: a sent value is pending until the seat state reports it, and
 * is overdue if that has not happened within WAIT_MS. It only reports; it never resends.
 */
public final class SendWatch {
    public static final long WAIT_MS = 3000;

    private final boolean[] pending = new boolean[Seat.values().length];
    private final int[] value = new int[pending.length];
    private final long[] at = new long[pending.length];

    public synchronized void sent(Seat seat, int sentValue, long now) {
        int i = seat.ordinal();
        pending[i] = true;
        value[i] = sentValue;
        at[i] = now;
    }

    /** Forgets the seat's pending send, e.g. when the stock call for it failed. */
    public synchronized void clear(Seat seat) {
        pending[seat.ordinal()] = false;
    }

    public synchronized void onState(Seat seat, int stateValue) {
        int i = seat.ordinal();
        if (pending[i] && value[i] == stateValue) pending[i] = false;
    }

    /** Text for the oldest send waiting at least WAIT_MS (and forgets it), else null. */
    public synchronized String overdue(long now) {
        int oldest = -1;
        for (int i = 0; i < pending.length; i++) {
            if (!pending[i] || now - at[i] < WAIT_MS) continue;
            if (oldest < 0 || at[i] < at[oldest]) oldest = i;
        }
        if (oldest < 0) return null;
        pending[oldest] = false;
        return "no change after 3 s: "
                + CheckedSeatSender.methodName(Seat.values()[oldest]) + " " + value[oldest];
    }
}

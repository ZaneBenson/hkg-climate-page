package hkgclimate.core;

/** Lets each tap request id through once, remembering the last 64 ids it accepted. */
public final class TapGate {
    private static final int SIZE = 64;

    private final long[] ids = new long[SIZE];
    private int count;
    private int next;

    /** True and remembered if id is not among the last 64 accepted ids; false (and not recorded) if it is. */
    public boolean accept(long requestId) {
        for (int i = 0; i < count; i++) {
            if (ids[i] == requestId) return false;
        }
        ids[next] = requestId;
        next = (next + 1) % SIZE;
        if (count < SIZE) count++;
        return true;
    }
}

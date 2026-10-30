package hkgclimate.core;

import java.util.HashMap;
import java.util.Map;

/** Allows one action per key per window; the caller supplies the time. */
public final class ReAddLimiter {
    private final long windowMs;
    private final Map<String, Long> last = new HashMap<String, Long>();

    public ReAddLimiter(long windowMs) {
        this.windowMs = windowMs;
    }

    /**
     * 0 when the key may be acted on now (this counts as its action), else the milliseconds to
     * wait until the window since its last action is over (nothing is counted).
     */
    public long acquire(String key, long nowMs) {
        Long at = last.get(key);
        if (at != null) {
            long wait = at + windowMs - nowMs;
            if (wait > 0 && wait <= windowMs) return wait;
        }
        last.put(key, nowMs);
        return 0;
    }

    /** Forgets every key's last action. */
    public void clear() {
        last.clear();
    }
}

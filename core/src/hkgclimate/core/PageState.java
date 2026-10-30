package hkgclimate.core;

import java.util.Arrays;

/** Everything the climate page shows. Per-seat arrays are indexed by Seat.ordinal(). */
public final class PageState {
    public final String top;
    public final String[] seatText;
    /** 0 to 3. */
    public final int[] seatLevel;
    /** 0 off, 1 heat, 2 cool. */
    public final int[] seatKind;
    public final boolean[] seatEnabled;
    /** True while taps are only logged (approval off); false when they are sent. */
    public final boolean testMode;

    public PageState(String top, String[] seatText, int[] seatLevel, int[] seatKind,
            boolean[] seatEnabled, boolean testMode) {
        this.top = top;
        this.seatText = seatText;
        this.seatLevel = seatLevel;
        this.seatKind = seatKind;
        this.seatEnabled = seatEnabled;
        this.testMode = testMode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PageState)) return false;
        PageState p = (PageState) o;
        return testMode == p.testMode
                && (top == null ? p.top == null : top.equals(p.top))
                && Arrays.equals(seatText, p.seatText)
                && Arrays.equals(seatLevel, p.seatLevel)
                && Arrays.equals(seatKind, p.seatKind)
                && Arrays.equals(seatEnabled, p.seatEnabled);
    }

    @Override
    public int hashCode() {
        int h = top == null ? 0 : top.hashCode();
        h = 31 * h + Arrays.hashCode(seatText);
        h = 31 * h + Arrays.hashCode(seatLevel);
        h = 31 * h + Arrays.hashCode(seatKind);
        h = 31 * h + Arrays.hashCode(seatEnabled);
        return 31 * h + (testMode ? 1 : 0);
    }
}

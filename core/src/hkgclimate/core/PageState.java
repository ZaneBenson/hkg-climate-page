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
    /** 1 the seat's next tap heats, 2 it cools, 0 the seat is unavailable. */
    public final int[] seatTapKind;
    public final boolean[] seatEnabled;

    public PageState(String top, String[] seatText, int[] seatLevel, int[] seatKind,
            int[] seatTapKind, boolean[] seatEnabled) {
        this.top = top;
        this.seatText = seatText;
        this.seatLevel = seatLevel;
        this.seatKind = seatKind;
        this.seatTapKind = seatTapKind;
        this.seatEnabled = seatEnabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PageState)) return false;
        PageState p = (PageState) o;
        return (top == null ? p.top == null : top.equals(p.top))
                && Arrays.equals(seatText, p.seatText)
                && Arrays.equals(seatLevel, p.seatLevel)
                && Arrays.equals(seatKind, p.seatKind)
                && Arrays.equals(seatTapKind, p.seatTapKind)
                && Arrays.equals(seatEnabled, p.seatEnabled);
    }

    @Override
    public int hashCode() {
        int h = top == null ? 0 : top.hashCode();
        h = 31 * h + Arrays.hashCode(seatText);
        h = 31 * h + Arrays.hashCode(seatLevel);
        h = 31 * h + Arrays.hashCode(seatKind);
        h = 31 * h + Arrays.hashCode(seatTapKind);
        return 31 * h + Arrays.hashCode(seatEnabled);
    }
}

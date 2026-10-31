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
    /** 1 the seat's next tap heats, 2 it cools (a swap included), 0 the seat is unavailable. */
    public final int[] seatTapKind;
    public final boolean[] seatEnabled;
    /** PresetList.MAX entries: the name of the preset in each slot, null for an empty slot. */
    public final String[] presetNames;
    /** PresetList.MAX entries: true when the slot has a preset that a tap can apply now. */
    public final boolean[] presetEnabled;
    /** The slot of the applied preset to highlight, or -1 for none. */
    public final int presetActive;
    /** True when the seat's sub-button offers swap (the seat is off and has heat and cool). */
    public final boolean[] seatSwap;
    /** True when the seat's sub-button offers off (the seat is on). */
    public final boolean[] seatOff;

    public PageState(String top, String[] seatText, int[] seatLevel, int[] seatKind,
            int[] seatTapKind, boolean[] seatEnabled, String[] presetNames,
            boolean[] presetEnabled, int presetActive, boolean[] seatSwap, boolean[] seatOff) {
        this.top = top;
        this.seatText = seatText;
        this.seatLevel = seatLevel;
        this.seatKind = seatKind;
        this.seatTapKind = seatTapKind;
        this.seatEnabled = seatEnabled;
        this.presetNames = presetNames;
        this.presetEnabled = presetEnabled;
        this.presetActive = presetActive;
        this.seatSwap = seatSwap;
        this.seatOff = seatOff;
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
                && Arrays.equals(seatEnabled, p.seatEnabled)
                && Arrays.equals(presetNames, p.presetNames)
                && Arrays.equals(presetEnabled, p.presetEnabled)
                && presetActive == p.presetActive
                && Arrays.equals(seatSwap, p.seatSwap)
                && Arrays.equals(seatOff, p.seatOff);
    }

    @Override
    public int hashCode() {
        int h = top == null ? 0 : top.hashCode();
        h = 31 * h + Arrays.hashCode(seatText);
        h = 31 * h + Arrays.hashCode(seatLevel);
        h = 31 * h + Arrays.hashCode(seatKind);
        h = 31 * h + Arrays.hashCode(seatTapKind);
        h = 31 * h + Arrays.hashCode(seatEnabled);
        h = 31 * h + Arrays.hashCode(presetNames);
        h = 31 * h + Arrays.hashCode(presetEnabled);
        h = 31 * h + presetActive;
        h = 31 * h + Arrays.hashCode(seatSwap);
        return 31 * h + Arrays.hashCode(seatOff);
    }
}

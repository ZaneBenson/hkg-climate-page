package hkgclimate.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SeatChoicesTest {
    private static final int[] OPTIONS = {-1, 0, 1, 2, 5, 6, 7};

    @Test
    public void labelsAndValuesMatchTheirOrder() {
        assertEquals(8, SeatChoices.LABELS.length);
        assertEquals(8, SeatChoices.VALUES.length);
        assertEquals("Leave as is", SeatChoices.LABELS[0]);
        assertEquals(Preset.LEAVE, SeatChoices.VALUES[0]);
        assertEquals("Heat high", SeatChoices.LABELS[2]);
        assertEquals(SeatValues.HEAT_HIGH, SeatChoices.VALUES[2]);
        assertEquals("Cool low", SeatChoices.LABELS[7]);
        assertEquals(SeatValues.COOL_LOW, SeatChoices.VALUES[7]);
    }

    @Test
    public void unknownOrSixShowsEveryChoice() {
        for (int opt : new int[] {-1, 6}) {
            for (int pos = 0; pos < 8; pos++) {
                assertTrue(SeatChoices.offered(pos, opt, Preset.LEAVE));
                assertEquals(SeatChoices.LABELS[pos], SeatChoices.label(pos, opt));
            }
        }
    }

    @Test
    public void heatOnlySeatHidesCoolChoicesWhenNothingCoolIsStored() {
        for (int pos = 0; pos < 5; pos++) assertTrue(SeatChoices.offered(pos, 2, SeatValues.OFF));
        for (int pos = 5; pos < 8; pos++) assertFalse(SeatChoices.offered(pos, 2, SeatValues.OFF));
    }

    @Test
    public void aStoredCoolChoiceIsStillOfferedAndLabelledNotAvailable() {
        assertTrue(SeatChoices.offered(5, 2, SeatValues.COOL_HIGH));
        assertFalse(SeatChoices.offered(6, 2, SeatValues.COOL_HIGH));
        assertFalse(SeatChoices.offered(7, 2, SeatValues.COOL_HIGH));
        assertEquals("Cool high (not available on this seat)", SeatChoices.label(5, 2));
    }

    @Test
    public void anUntouchedSeatKeepsItsStoredValueForEveryOptionAndValue() {
        for (int opt : OPTIONS) {
            for (int v : SeatChoices.VALUES) {
                int pos = SeatChoices.positionOf(v);
                assertTrue("stored " + v + " offered with option " + opt, SeatChoices.offered(pos, opt, v));
                assertEquals(v, SeatChoices.VALUES[pos]);
            }
        }
    }

    @Test
    public void positionOfANonChoiceIsLeaveAsIs() {
        assertEquals(0, SeatChoices.positionOf(99));
    }
}

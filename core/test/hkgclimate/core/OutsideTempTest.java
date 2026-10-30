package hkgclimate.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OutsideTempTest {
    @Test
    public void validWhenBothStatesAreFFFF() {
        OutsideTemp t = OutsideTemp.of(18f, 0xffff, 65f, 0xffff);
        assertTrue(t.valid());
        assertEquals(65f, t.fahrenheit(), 0f);
    }

    @Test
    public void notValidForOtherFahrenheitStates() {
        assertFalse(OutsideTemp.of(18f, 0xffff, 65f, 0xff).valid());
        assertFalse(OutsideTemp.of(18f, 0xffff, 65f, 0xfe).valid());
        assertFalse(OutsideTemp.of(18f, 0xffff, 65f, 0).valid());
    }

    @Test
    public void notValidForSentinelValues() {
        assertFalse(OutsideTemp.of(18f, 0xffff, 100000f, 0xffff).valid());
        assertFalse(OutsideTemp.of(18f, 0xffff, -40f, 0xffff).valid());
    }

    @Test
    public void unknownIsNotValid() {
        assertFalse(OutsideTemp.UNKNOWN.valid());
        assertNull(OutsideTemp.UNKNOWN.display(2));
        assertNull(OutsideTemp.UNKNOWN.display(1));
    }

    @Test
    public void displayFollowsTheUnit() {
        OutsideTemp t = OutsideTemp.of(18f, 0xffff, 65f, 0xffff);
        assertEquals("65 °F", t.display(2));
        assertEquals("18 °C", t.display(1));
    }

    @Test
    public void anyOtherUnitShowsFahrenheit() {
        OutsideTemp t = OutsideTemp.of(18f, 0xffff, 65f, 0xffff);
        assertEquals("65 °F", t.display(-1));
        assertEquals("65 °F", t.display(0));
        assertEquals("65 °F", t.display(3));
    }

    @Test
    public void displayTakesTheFloor() {
        OutsideTemp t = OutsideTemp.of(17.9f, 0xffff, 65.9f, 0xffff);
        assertEquals("65 °F", t.display(2));
        assertEquals("17 °C", t.display(1));
        OutsideTemp neg = OutsideTemp.of(-0.5f, 0xffff, 20.5f, 0xffff);
        assertEquals("-1 °C", neg.display(1));
    }

    @Test
    public void celsiusDisplayNeedsTheCelsiusPairValidToo() {
        OutsideTemp t = OutsideTemp.of(18f, 0xfe, 65f, 0xffff);
        assertTrue(t.valid());
        assertNull(t.display(1));
        assertEquals("65 °F", t.display(2));
        assertNull(OutsideTemp.of(100000f, 0xffff, 65f, 0xffff).display(1));
        assertNull(OutsideTemp.of(-40f, 0xffff, 65f, 0xffff).display(1));
    }

    @Test
    public void invalidFahrenheitPairGivesNoDisplayInAnyUnit() {
        OutsideTemp t = OutsideTemp.of(18f, 0xffff, 65f, 0);
        assertNull(t.display(2));
        assertNull(t.display(1));
    }

    @Test
    public void nanAndInfiniteValuesAreInvalidInBothPairs() {
        float[] bad = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (float v : bad) {
            assertFalse(OutsideTemp.of(18f, 0xffff, v, 0xffff).valid());
            assertNull(OutsideTemp.of(18f, 0xffff, v, 0xffff).display(2));
            assertNull(OutsideTemp.of(18f, 0xffff, v, 0xffff).display(1));
            assertTrue(OutsideTemp.of(v, 0xffff, 65f, 0xffff).valid());
            assertNull(OutsideTemp.of(v, 0xffff, 65f, 0xffff).display(1));
            assertEquals("65 \u00b0F", OutsideTemp.of(v, 0xffff, 65f, 0xffff).display(2));
        }
    }
}

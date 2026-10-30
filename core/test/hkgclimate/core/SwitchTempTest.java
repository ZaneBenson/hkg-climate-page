package hkgclimate.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SwitchTempTest {
    @Test
    public void defaultAndRange() {
        assertEquals(60, SwitchTemp.DEFAULT_F);
        assertEquals(40, SwitchTemp.MIN_F);
        assertEquals(85, SwitchTemp.MAX_F);
    }

    @Test
    public void toDisplayConvertsForCelsius() {
        assertEquals(16, SwitchTemp.toDisplay(60, 1));
        assertEquals(4, SwitchTemp.toDisplay(40, 1));
        assertEquals(29, SwitchTemp.toDisplay(85, 1));
    }

    @Test
    public void toDisplayLeavesFahrenheit() {
        assertEquals(60, SwitchTemp.toDisplay(60, 2));
        assertEquals(60, SwitchTemp.toDisplay(60, -1));
        assertEquals(60, SwitchTemp.toDisplay(60, 0));
    }

    @Test
    public void fromDisplayConvertsBackToWholeFahrenheit() {
        assertEquals(61, SwitchTemp.fromDisplay(16, 1));
        assertEquals(60, SwitchTemp.fromDisplay(60, 2));
        assertEquals(60, SwitchTemp.fromDisplay(60, -1));
    }

    @Test
    public void fromDisplayClampsToTheRange() {
        assertEquals(40, SwitchTemp.fromDisplay(0, 1));
        assertEquals(85, SwitchTemp.fromDisplay(40, 1));
        assertEquals(40, SwitchTemp.fromDisplay(10, 2));
        assertEquals(85, SwitchTemp.fromDisplay(200, 2));
    }

    @Test
    public void everyWholeFahrenheitSurvivesAFahrenheitRoundTrip() {
        for (int f = SwitchTemp.MIN_F; f <= SwitchTemp.MAX_F; f++) {
            assertEquals(f, SwitchTemp.fromDisplay(SwitchTemp.toDisplay(f, 2), 2));
        }
    }
}

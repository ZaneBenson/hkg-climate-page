package hkgclimate.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SeatStepperTest {
    private static final OutsideTemp COLD = OutsideTemp.of(9f, 0xffff, 48f, 0xffff);
    private static final OutsideTemp AT_SWITCH = OutsideTemp.of(16f, 0xffff, 60f, 0xffff);
    private static final OutsideTemp WARM = OutsideTemp.of(24f, 0xffff, 75f, 0xffff);

    @Test
    public void offTurnsOnToHighOfTheChosenMode() {
        assertEquals(8, SeatStepper.next(2, Mode.HEAT));
        assertEquals(5, SeatStepper.next(2, Mode.COOL));
    }

    @Test
    public void heatStepsDownToOff() {
        assertEquals(7, SeatStepper.next(8, Mode.COOL)); // on in heat stays heat
        assertEquals(6, SeatStepper.next(7, Mode.COOL));
        assertEquals(2, SeatStepper.next(6, Mode.COOL));
        assertEquals(7, SeatStepper.next(8, Mode.HEAT));
        assertEquals(6, SeatStepper.next(7, Mode.HEAT));
        assertEquals(2, SeatStepper.next(6, Mode.HEAT));
    }

    @Test
    public void coolStepsDownToOff() {
        assertEquals(4, SeatStepper.next(5, Mode.HEAT)); // on in cool stays cool
        assertEquals(3, SeatStepper.next(4, Mode.HEAT));
        assertEquals(2, SeatStepper.next(3, Mode.HEAT));
        assertEquals(4, SeatStepper.next(5, Mode.COOL));
        assertEquals(3, SeatStepper.next(4, Mode.COOL));
        assertEquals(2, SeatStepper.next(3, Mode.COOL));
    }

    @Test
    public void unknownCurrentValueGivesMinusOne() {
        assertEquals(-1, SeatStepper.next(0, Mode.HEAT));
        assertEquals(-1, SeatStepper.next(1, Mode.HEAT));
        assertEquals(-1, SeatStepper.next(9, Mode.HEAT));
        assertEquals(-1, SeatStepper.next(-1, Mode.HEAT));
        assertEquals(-1, SeatStepper.next(255, Mode.COOL));
    }

    @Test
    public void coolHighSetElsewhereWithColdOutsideStillStepsDown() {
        // Review focus 1: the mode choice only matters when turning on.
        Mode m = SeatStepper.chooseMode(COLD, 60, 6);
        assertEquals(Mode.HEAT, m);
        assertEquals(4, SeatStepper.next(5, m));
    }

    @Test
    public void chooseModeHeatsBelowTheSwitchTemperature() {
        assertEquals(Mode.HEAT, SeatStepper.chooseMode(COLD, 60, 6));
    }

    @Test
    public void chooseModeCoolsAtTheSwitchTemperature() {
        assertEquals(Mode.COOL, SeatStepper.chooseMode(AT_SWITCH, 60, 6));
        assertEquals(Mode.COOL, SeatStepper.chooseMode(WARM, 60, 6));
    }

    @Test
    public void chooseModeHeatsWhenNoVentilationFitted() {
        assertEquals(Mode.HEAT, SeatStepper.chooseMode(WARM, 60, 0));
        assertEquals(Mode.HEAT, SeatStepper.chooseMode(WARM, 60, 7));
    }

    @Test
    public void chooseModeHeatsWhenTemperatureUnknown() {
        assertEquals(Mode.HEAT, SeatStepper.chooseMode(OutsideTemp.UNKNOWN, 60, 6));
    }

    @Test
    public void chooseModeFollowsTheSwitchTemperature() {
        assertEquals(Mode.COOL, SeatStepper.chooseMode(COLD, 45, 6));
        assertEquals(Mode.HEAT, SeatStepper.chooseMode(WARM, 80, 6));
    }

    @Test
    public void seatValuesKnownRange() {
        assertFalse(SeatValues.isKnown(1));
        for (int v = 2; v <= 8; v++) assertTrue(SeatValues.isKnown(v));
        assertFalse(SeatValues.isKnown(9));
        assertFalse(SeatValues.isKnown(-1));
        assertFalse(SeatValues.isKnown(0));
    }

    @Test
    public void seatValuesLevelAndMode() {
        assertEquals(0, SeatValues.level(2));
        assertEquals(3, SeatValues.level(8));
        assertEquals(2, SeatValues.level(7));
        assertEquals(1, SeatValues.level(6));
        assertEquals(3, SeatValues.level(5));
        assertEquals(2, SeatValues.level(4));
        assertEquals(1, SeatValues.level(3));
        assertEquals(0, SeatValues.level(99));
        assertNull(SeatValues.modeOf(2));
        assertNull(SeatValues.modeOf(99));
        assertEquals(Mode.HEAT, SeatValues.modeOf(8));
        assertEquals(Mode.HEAT, SeatValues.modeOf(6));
        assertEquals(Mode.COOL, SeatValues.modeOf(5));
        assertEquals(Mode.COOL, SeatValues.modeOf(3));
    }

    @Test
    public void seatValuesWords() {
        assertEquals("off", SeatValues.words(2));
        assertEquals("heat high", SeatValues.words(8));
        assertEquals("heat mid", SeatValues.words(7));
        assertEquals("heat low", SeatValues.words(6));
        assertEquals("cool high", SeatValues.words(5));
        assertEquals("cool mid", SeatValues.words(4));
        assertEquals("cool low", SeatValues.words(3));
        assertNull(SeatValues.words(0));
        assertNull(SeatValues.words(9));
    }

    @Test
    public void seatValueConstants() {
        assertEquals(2, SeatValues.OFF);
        assertEquals(8, SeatValues.HEAT_HIGH);
        assertEquals(7, SeatValues.HEAT_MID);
        assertEquals(6, SeatValues.HEAT_LOW);
        assertEquals(5, SeatValues.COOL_HIGH);
        assertEquals(4, SeatValues.COOL_MID);
        assertEquals(3, SeatValues.COOL_LOW);
        assertEquals(6, SeatValues.HEAT_AND_COOL_OPTION);
    }
}

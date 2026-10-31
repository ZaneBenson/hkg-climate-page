package hkgclimate.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PresetSummaryTest {
    @Test
    public void bothSeats() {
        assertEquals("Cold: Driver heat high · Passenger heat high",
                PresetSummary.of(new Preset("Cold", 8, 8)));
    }

    @Test
    public void passengerLeftAsIsIsOmitted() {
        assertEquals("Warm: Driver cool low", PresetSummary.of(new Preset("Warm", 3, Preset.LEAVE)));
    }

    @Test
    public void driverLeftAsIsIsOmitted() {
        assertEquals("Off: Passenger off", PresetSummary.of(new Preset("Off", Preset.LEAVE, 2)));
    }

    @Test
    public void everyValueUsesItsWords() {
        assertEquals("X: Driver heat mid · Passenger cool mid", PresetSummary.of(new Preset("X", 7, 4)));
    }
}

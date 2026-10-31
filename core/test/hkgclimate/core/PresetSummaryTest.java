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

    @Test
    public void fullPresetNamesEverySetItem() {
        assertEquals("Cold: Driver heat high · Passenger heat high · Wheel on · Eco off · Manual"
                + " · 70 °F · Fan 3 · A/C on · Airflow face and floor · Sync on",
                PresetSummary.of(new Preset("Cold", 8, 8, 1, 0, Preset.CLIMATE_MANUAL, 70, 3, 1, 2, 1)));
    }

    @Test
    public void climateModesAndOffItems() {
        assertEquals("Cold: Driver heat high · Passenger heat high · Wheel on · Auto · 70 °F",
                PresetSummary.of(new Preset("Cold", 8, 8, 1, Preset.LEAVE, Preset.CLIMATE_AUTO, 70,
                        Preset.LEAVE, Preset.LEAVE, Preset.LEAVE, Preset.LEAVE)));
        assertEquals("Go: Climate off",
                PresetSummary.of(new Preset("Go", -1, -1, -1, -1, Preset.CLIMATE_OFF, 70, 3, 1, 2, 1)));
        assertEquals("Fresh: A/C off · Airflow floor and defrost · Sync off",
                PresetSummary.of(new Preset("Fresh", -1, -1, -1, -1, Preset.LEAVE, -1, -1, 0, 4, 0)));
    }
}

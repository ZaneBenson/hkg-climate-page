package hkgclimate.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PresetChoicesTest {
    private static String describe(PresetChoices.Choice[] c) {
        StringBuilder b = new StringBuilder();
        for (PresetChoices.Choice x : c) b.append(x.value).append('=').append(x.label).append(';');
        return b.toString();
    }

    @Test
    public void onOffItemsOfferLeaveOnOff() {
        String want = "-1=Leave as is;1=On;0=Off;";
        assertEquals(want, describe(PresetChoices.wheel()));
        assertEquals(want, describe(PresetChoices.eco()));
        assertEquals(want, describe(PresetChoices.ac()));
        assertEquals(want, describe(PresetChoices.sync()));
    }

    @Test
    public void climateFanAndAirflowChoices() {
        assertEquals("-1=Leave as is;1=Off;2=Auto;3=Manual;", describe(PresetChoices.climate()));
        assertEquals("-1=Leave as is;1=1;2=2;3=3;4=4;5=5;6=6;7=7;8=8;", describe(PresetChoices.fan()));
        assertEquals("-1=Leave as is;1=Face;2=Face and floor;3=Floor;4=Floor and defrost;",
                describe(PresetChoices.airflow()));
    }

    @Test
    public void everyItemStartsWithLeaveAsIs() {
        PresetChoices.Choice[][] all = {PresetChoices.wheel(), PresetChoices.eco(), PresetChoices.climate(),
                PresetChoices.temperatureChoices(2), PresetChoices.temperatureChoices(1),
                PresetChoices.fan(), PresetChoices.ac(), PresetChoices.airflow(), PresetChoices.sync()};
        for (PresetChoices.Choice[] c : all) {
            assertEquals(Preset.LEAVE, c[0].value);
            assertEquals("Leave as is", c[0].label);
        }
    }

    @Test
    public void fahrenheitTemperatureChoices() {
        PresetChoices.Choice[] c = PresetChoices.temperatureChoices(2);
        assertEquals(22, c.length);
        assertEquals(62, c[1].value);
        assertEquals("62 °F", c[1].label);
        assertEquals(82, c[21].value);
        assertEquals("82 °F", c[21].label);
        assertEquals("70 °F", c[PresetChoices.positionOf(c, 70)].label);
        // an unknown unit is shown in Fahrenheit
        assertEquals("62 °F", PresetChoices.temperatureChoices(-1)[1].label);
    }

    @Test
    public void celsiusTemperatureChoicesStoreFahrenheit() {
        PresetChoices.Choice[] c = PresetChoices.temperatureChoices(1);
        assertEquals(22, c.length);
        assertEquals(62, c[1].value);
        assertEquals("16.5 °C", c[1].label);
        assertEquals(70, c[9].value);
        assertEquals("21.0 °C", c[9].label);
        assertEquals(82, c[21].value);
        assertEquals("28.0 °C", c[21].label);
        for (int i = 2; i < c.length; i++) assertFalse(c[i].label.equals(c[i - 1].label));
    }

    @Test
    public void celsiusHalfIsTheNearestHalfDegree() {
        assertEquals(21.0, PresetChoices.celsiusHalf(70), 0.0);
        assertEquals(16.5, PresetChoices.celsiusHalf(62), 0.0);
        assertEquals(28.0, PresetChoices.celsiusHalf(82), 0.0);
        assertEquals(22.0, PresetChoices.celsiusHalf(72), 0.0);
        assertEquals(20.5, PresetChoices.celsiusHalf(69), 0.0);
    }

    @Test
    public void positionOfFindsAValueOrFallsBackToLeave() {
        assertEquals(3, PresetChoices.positionOf(PresetChoices.climate(), Preset.CLIMATE_MANUAL));
        assertEquals(0, PresetChoices.positionOf(PresetChoices.climate(), 9));
        assertEquals(0, PresetChoices.positionOf(PresetChoices.fan(), Preset.LEAVE));
    }

    private static final String[] ITEMS = {PresetChoices.ITEM_WHEEL, PresetChoices.ITEM_ECO,
            PresetChoices.ITEM_CLIMATE, PresetChoices.ITEM_DRIVER_TEMP,
            PresetChoices.ITEM_PASSENGER_TEMP, PresetChoices.ITEM_FAN,
            PresetChoices.ITEM_AC, PresetChoices.ITEM_AIRFLOW, PresetChoices.ITEM_SYNC};

    private static boolean greyedIn(String item, int climate) {
        return PresetChoices.greyed(item, climate, Preset.LEAVE);
    }

    private static boolean greyedWithSync(String item, int climate, int sync) {
        return PresetChoices.greyed(item, climate, sync);
    }

    @Test
    public void nothingIsGreyedWhenClimateIsLeftOrManual() {
        for (int climate : new int[] {Preset.LEAVE, Preset.CLIMATE_MANUAL}) {
            for (String item : ITEMS) assertFalse(item + " " + climate, greyedIn(item, climate));
        }
    }

    @Test
    public void autoGreysFanAcAndAirflowOnly() {
        for (String item : ITEMS) {
            boolean want = item.equals("fan") || item.equals("ac") || item.equals("airflow");
            assertEquals(item, want, greyedIn(item, Preset.CLIMATE_AUTO));
        }
    }

    @Test
    public void offGreysTemperatureFanAcAirflowAndSync() {
        for (String item : ITEMS) {
            boolean want = item.equals("driverTemp") || item.equals("passengerTemp")
                    || item.equals("fan") || item.equals("ac")
                    || item.equals("airflow") || item.equals("sync");
            assertEquals(item, want, greyedIn(item, Preset.CLIMATE_OFF));
        }
        assertTrue(greyedIn(PresetChoices.ITEM_SYNC, Preset.CLIMATE_OFF));
    }

    @Test
    public void syncOnGreysThePassengerTemperatureOnly() {
        for (int climate : new int[] {Preset.LEAVE, Preset.CLIMATE_AUTO, Preset.CLIMATE_MANUAL}) {
            for (String item : ITEMS) {
                boolean want = item.equals("passengerTemp")
                        || (climate == Preset.CLIMATE_AUTO && (item.equals("fan")
                        || item.equals("ac") || item.equals("airflow")));
                assertEquals(item + " " + climate, want, greyedWithSync(item, climate, 1));
            }
        }
        // sync off or left leaves the passenger temperature enabled (unless climate is off)
        assertFalse(greyedWithSync("passengerTemp", Preset.CLIMATE_MANUAL, 0));
        assertFalse(greyedWithSync("passengerTemp", Preset.CLIMATE_MANUAL, Preset.LEAVE));
        assertTrue(greyedWithSync("passengerTemp", Preset.CLIMATE_OFF, 0));
    }

    @Test
    public void seatsAndUnknownItemsAreNeverGreyed() {
        for (int climate : new int[] {Preset.LEAVE, 1, 2, 3}) {
            assertFalse(greyedIn("driver", climate));
            assertFalse(greyedIn("passenger", climate));
            assertFalse(greyedIn(null, climate));
        }
    }

    @Test
    public void greyedMatchesWhatThePresetStores() {
        // an item the editor greys is exactly an item the Preset constructor drops
        for (int climate : new int[] {Preset.LEAVE, 1, 2, 3}) {
            for (int sync : new int[] {Preset.LEAVE, 0, 1}) {
                Preset p = new Preset("x", 8, 8, 1, 1, climate, 70, 72, 3, 1, 2, sync);
                assertEquals(greyedWithSync("driverTemp", climate, sync),
                        p.driverTempF == Preset.LEAVE);
                assertEquals(greyedWithSync("passengerTemp", climate, sync),
                        p.passengerTempF == Preset.LEAVE);
                assertEquals(greyedWithSync("fan", climate, sync), p.fan == Preset.LEAVE);
                assertEquals(greyedWithSync("ac", climate, sync), p.ac == Preset.LEAVE);
                assertEquals(greyedWithSync("airflow", climate, sync), p.airflow == Preset.LEAVE);
                // sync is itself dropped only by Off (a value of Leave stays Leave)
                assertEquals(climate == 1 || sync == Preset.LEAVE, p.sync == Preset.LEAVE);
                assertEquals(1, p.wheel);
                assertEquals(1, p.eco);
            }
        }
    }
}

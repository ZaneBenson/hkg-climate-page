package hkgclimate.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PresetTest {
    private static final int L = Preset.LEAVE;
    private static final String MANUAL = "manual needs a fan speed, A/C or airflow";
    private static final String NOTHING = "choose at least one setting";

    /**
     * name x, no seats, with the items in field order: wheel, eco, climate, temperature (both
     * sides), fan, ac, airflow, sync.
     */
    private static Preset items(int wheel, int eco, int climate, int tempF, int fan, int ac,
            int airflow, int sync) {
        return new Preset("x", L, L, wheel, eco, climate, tempF, tempF, fan, ac, airflow, sync);
    }

    @Test
    public void seatOnlyConstructorLeavesEveryNewItem() {
        Preset p = new Preset("a", 8, 5);
        assertEquals(L, p.wheel);
        assertEquals(L, p.eco);
        assertEquals(L, p.climate);
        assertEquals(L, p.driverTempF);
        assertEquals(L, p.passengerTempF);
        assertEquals(L, p.fan);
        assertEquals(L, p.ac);
        assertEquals(L, p.airflow);
        assertEquals(L, p.sync);
        assertEquals(8, p.target(Seat.DRIVER));
        assertEquals(5, p.target(Seat.PASSENGER));
    }

    @Test
    public void autoDropsFanAcAndAirflowAndKeepsTheRest() {
        Preset p = new Preset("a", 8, 5, 1, 0, Preset.CLIMATE_AUTO, 70, 70, 4, 1, 3, 1);
        assertEquals(L, p.fan);
        assertEquals(L, p.ac);
        assertEquals(L, p.airflow);
        assertEquals(70, p.driverTempF);
        assertEquals(1, p.sync);
        assertEquals(8, p.driver);
        assertEquals(5, p.passenger);
        assertEquals(1, p.wheel);
        assertEquals(0, p.eco);
        assertEquals(Preset.CLIMATE_AUTO, p.climate);
    }

    @Test
    public void offDropsTemperatureFanAcAirflowAndSyncAndKeepsTheRest() {
        Preset p = new Preset("a", 8, 5, 1, 0, Preset.CLIMATE_OFF, 70, 70, 4, 1, 3, 1);
        assertEquals(L, p.driverTempF);
        assertEquals(L, p.passengerTempF);
        assertEquals(L, p.fan);
        assertEquals(L, p.ac);
        assertEquals(L, p.airflow);
        assertEquals(L, p.sync);
        assertEquals(8, p.driver);
        assertEquals(5, p.passenger);
        assertEquals(1, p.wheel);
        assertEquals(0, p.eco);
        assertEquals(Preset.CLIMATE_OFF, p.climate);
    }

    @Test
    public void manualAndLeaveKeepEveryItem() {
        for (int climate : new int[] {Preset.CLIMATE_MANUAL, L}) {
            Preset p = items(1, 1, climate, 70, 4, 1, 3, 0);
            assertEquals(70, p.driverTempF);
            assertEquals(70, p.passengerTempF);
            assertEquals(4, p.fan);
            assertEquals(1, p.ac);
            assertEquals(3, p.airflow);
            assertEquals(0, p.sync);
        }
    }

    @Test
    public void autoWithSyncOnKeepsTheDriverTemperatureAndDropsThePassengers() {
        Preset p = new Preset("a", L, L, L, L, Preset.CLIMATE_AUTO, 70, 72, L, L, L, 1);
        assertEquals(70, p.driverTempF);
        assertEquals(L, p.passengerTempF);
        assertEquals(1, p.sync);
    }

    @Test
    public void syncOnDropsThePassengerTemperatureForEveryClimateMode() {
        for (int climate : new int[] {Preset.CLIMATE_AUTO, Preset.CLIMATE_MANUAL, L}) {
            Preset p = new Preset("a", L, L, L, L, climate, 70, 72, L, L, L, 1);
            assertEquals(70, p.driverTempF);
            assertEquals(L, p.passengerTempF);
        }
    }

    @Test
    public void syncOffOrLeftKeepsBothTemperatures() {
        for (int sync : new int[] {0, L}) {
            Preset p = new Preset("a", L, L, L, L, L, 70, 72, L, L, L, sync);
            assertEquals(70, p.driverTempF);
            assertEquals(72, p.passengerTempF);
        }
    }

    @Test
    public void offDropsBothTemperatures() {
        Preset p = new Preset("a", 8, 5, L, L, Preset.CLIMATE_OFF, 70, 72, L, L, L, L);
        assertEquals(L, p.driverTempF);
        assertEquals(L, p.passengerTempF);
    }

    @Test
    public void eachTemperatureIsCheckedAndCountsAsASetting() {
        String bad = "bad setting value";
        assertEquals(bad, Preset.check(new Preset("x", L, L, L, L, L, 70, 83, L, L, L, L)));
        assertEquals(bad, Preset.check(new Preset("x", L, L, L, L, L, 61, 70, L, L, L, L)));
        assertEquals(bad, Preset.check(new Preset("x", L, L, L, L, L, L, 61, L, L, L, L)));
        assertNull(Preset.check(new Preset("x", L, L, L, L, L, 62, 82, L, L, L, L)));
        assertNull(Preset.check(new Preset("x", L, L, L, L, L, L, 72, L, L, L, L)));
        assertNull(Preset.check(new Preset("x", L, L, L, L, L, 72, L, L, L, L, L)));
        assertTrue(new Preset("x", L, L, L, L, L, L, 72, L, L, L, L).hasRunnerItems());
        assertTrue(new Preset("x", L, L, L, L, L, 72, L, L, L, L, L).hasRunnerItems());
    }

    @Test
    public void checkAcceptsEveryRangeEdge() {
        assertNull(Preset.check(items(L, L, L, 62, L, L, L, L)));
        assertNull(Preset.check(items(L, L, L, 82, L, L, L, L)));
        assertNull(Preset.check(items(L, L, L, L, 1, L, L, L)));
        assertNull(Preset.check(items(L, L, L, L, 8, L, L, L)));
        assertNull(Preset.check(items(L, L, L, L, L, L, 1, L)));
        assertNull(Preset.check(items(L, L, L, L, L, L, 4, L)));
        assertNull(Preset.check(items(0, L, L, L, L, L, L, L)));
        assertNull(Preset.check(items(L, 1, L, L, L, L, L, L)));
    }

    @Test
    public void checkRefusesOutOfRangeItems() {
        String bad = "bad setting value";
        assertEquals(bad, Preset.check(items(L, L, L, 61, L, L, L, L)));
        assertEquals(bad, Preset.check(items(L, L, L, 83, L, L, L, L)));
        assertEquals(bad, Preset.check(items(L, L, L, L, 0, L, L, L)));
        assertEquals(bad, Preset.check(items(L, L, L, L, 9, L, L, L)));
        assertEquals(bad, Preset.check(items(L, L, L, L, L, L, 0, L)));
        assertEquals(bad, Preset.check(items(L, L, L, L, L, L, 5, L)));
        assertEquals(bad, Preset.check(items(2, L, L, L, L, L, L, L)));
        assertEquals(bad, Preset.check(items(L, 2, L, L, L, L, L, L)));
        assertEquals(bad, Preset.check(items(L, L, L, L, L, 2, L, L)));
        assertEquals(bad, Preset.check(items(L, L, L, L, L, L, L, 2)));
        assertEquals(bad, Preset.check(items(L, L, 0, L, L, L, L, L)));
        assertEquals(bad, Preset.check(items(L, L, 4, L, L, L, L, L)));
    }

    @Test
    public void checkNeedsAtLeastOneSetting() {
        assertEquals(NOTHING, Preset.check(items(L, L, L, L, L, L, L, L)));
        assertNull(Preset.check(new Preset("x", 8, L)));
        assertNull(Preset.check(items(L, L, Preset.CLIMATE_OFF, L, L, L, L, L)));
        assertNull(Preset.check(items(L, L, Preset.CLIMATE_AUTO, L, L, L, L, L)));
    }

    @Test
    public void checkRefusesBadNamesAndSeats() {
        assertEquals("name 1-10 characters", Preset.check(new Preset("", 8, 8)));
        assertEquals("name 1-10 characters", Preset.check(new Preset("01234567890", 8, 8)));
        assertEquals("name has a tab or line break", Preset.check(new Preset("a\tb", 8, 8)));
        assertEquals("bad seat value", Preset.check(new Preset("x", 1, 8)));
    }

    @Test
    public void manualNeedsFanAcOrAirflow() {
        assertEquals(MANUAL, Preset.check(items(L, L, Preset.CLIMATE_MANUAL, L, L, L, L, L)));
        assertEquals(MANUAL, Preset.check(items(L, L, Preset.CLIMATE_MANUAL, 70, L, L, L, L)));
        assertEquals(MANUAL, Preset.check(items(L, L, Preset.CLIMATE_MANUAL, L, L, L, L, 1)));
        assertEquals(MANUAL, Preset.check(new Preset("x", 8, 8, 1, 1, Preset.CLIMATE_MANUAL, 70, 70, L, L, L, 1)));
        assertNull(Preset.check(items(L, L, Preset.CLIMATE_MANUAL, L, L, 1, L, L)));
        assertNull(Preset.check(items(L, L, Preset.CLIMATE_MANUAL, L, L, 0, L, L)));
        assertNull(Preset.check(items(L, L, Preset.CLIMATE_MANUAL, L, 3, L, L, L)));
        assertNull(Preset.check(items(L, L, Preset.CLIMATE_MANUAL, L, L, L, 2, L)));
    }

    @Test
    public void seatOnlyCheckIsUnchanged() {
        assertNull(Preset.check("Cold", 8, 8));
        assertEquals("choose at least one seat", Preset.check("x", L, L));
        assertEquals("bad seat value", Preset.check("x", 1, 8));
    }

    @Test
    public void seatAndRunnerItems() {
        assertTrue(new Preset("a", 8, L).hasSeatItems());
        assertTrue(new Preset("a", L, 2).hasSeatItems());
        assertFalse(new Preset("a", L, L).hasSeatItems());
        assertFalse(new Preset("a", 8, 8).hasRunnerItems());
        assertTrue(items(1, L, L, L, L, L, L, L).hasRunnerItems());
        assertTrue(items(L, 0, L, L, L, L, L, L).hasRunnerItems());
        assertTrue(items(L, L, Preset.CLIMATE_OFF, L, L, L, L, L).hasRunnerItems());
        assertTrue(items(L, L, L, 70, L, L, L, L).hasRunnerItems());
        assertTrue(items(L, L, L, L, 2, L, L, L).hasRunnerItems());
        assertTrue(items(L, L, L, L, L, 0, L, L).hasRunnerItems());
        assertTrue(items(L, L, L, L, L, L, 3, L).hasRunnerItems());
        assertTrue(items(L, L, L, L, L, L, L, 1).hasRunnerItems());
        assertFalse(items(L, L, L, L, L, L, L, L).hasRunnerItems());
    }

    @Test
    public void equalityCoversEveryItem() {
        Preset base = new Preset("a", 8, 5, 1, 0, L, 70, 70, 3, 1, 2, 1);
        assertEquals(base, new Preset(" a", 8, 5, 1, 0, L, 70, 70, 3, 1, 2, 1));
        assertEquals(base.hashCode(), new Preset("a", 8, 5, 1, 0, L, 70, 70, 3, 1, 2, 1).hashCode());
        assertNotEquals(base, new Preset("a", 8, 5, 0, 0, L, 70, 70, 3, 1, 2, 1));
        assertNotEquals(base, new Preset("a", 8, 5, 1, 1, L, 70, 70, 3, 1, 2, 1));
        assertNotEquals(base, new Preset("a", 8, 5, 1, 0, 3, 70, 70, 3, 1, 2, 1));
        assertNotEquals(base, new Preset("a", 8, 5, 1, 0, L, 71, 70, 3, 1, 2, 1));
        // with sync on the passenger temperature is not kept, so it can't tell presets apart
        assertEquals(base, new Preset("a", 8, 5, 1, 0, L, 70, 71, 3, 1, 2, 1));
        Preset own = new Preset("a", 8, 5, 1, 0, L, 70, 72, 3, 1, 2, 0);
        assertNotEquals(own, new Preset("a", 8, 5, 1, 0, L, 70, 73, 3, 1, 2, 0));
        assertNotEquals(own, new Preset("a", 8, 5, 1, 0, L, 70, L, 3, 1, 2, 0));
        assertEquals(own.hashCode(), new Preset("a", 8, 5, 1, 0, L, 70, 72, 3, 1, 2, 0).hashCode());
        assertNotEquals(base, new Preset("a", 8, 5, 1, 0, L, 70, 70, 4, 1, 2, 1));
        assertNotEquals(base, new Preset("a", 8, 5, 1, 0, L, 70, 70, 3, 0, 2, 1));
        assertNotEquals(base, new Preset("a", 8, 5, 1, 0, L, 70, 70, 3, 1, 3, 1));
        assertNotEquals(base, new Preset("a", 8, 5, 1, 0, L, 70, 70, 3, 1, 2, 0));
    }
}

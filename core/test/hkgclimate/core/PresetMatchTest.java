package hkgclimate.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class PresetMatchTest {
    private static final int L = Preset.LEAVE;
    private static final int[] SEATS_OFF = {2, 2};

    /** unit F, on, auto 0, A/C 1, airflow 2, fan raw 4 (speed 3), sync 0, both 70, wheel 1, eco 0. */
    private static CabinState car(int opState, int auto, int ac, int airflow, int fanRaw, int sync,
            float driver, float passenger, int wheel, int eco) {
        return CabinState.of(2, opState, auto, ac, airflow, fanRaw, sync, 0, driver, passenger,
                wheel, eco);
    }

    private static final CabinState MATCH = car(1, 0, 1, 2, 4, 0, 70f, 72f, 1, 0);

    /** Manual, both temperatures, fan 3, A/C on, airflow 2, sync off, wheel on, eco off, seats 8/5. */
    private static final Preset ALL = new Preset("All", 8, 5, 1, 0, Preset.CLIMATE_MANUAL, 70, 72,
            3, 1, 2, 0);

    private static Set<String> names(String... n) { return new HashSet<String>(Arrays.asList(n)); }

    private static final Set<String> ALL_ITEMS = names(PresetMatch.DRIVER_SEAT,
            PresetMatch.PASSENGER_SEAT, PresetMatch.WHEEL, PresetMatch.ECO, PresetMatch.AC,
            PresetMatch.AIRFLOW, PresetMatch.FAN, PresetMatch.MANUAL, PresetMatch.SYNC,
            PresetMatch.DRIVER_TEMP, PresetMatch.PASSENGER_TEMP);

    @Test
    public void everyDoneItemAtItsTargetIsNoDrift() {
        assertFalse(PresetMatch.drifted(ALL, new int[] {8, 5}, MATCH, ALL_ITEMS));
    }

    @Test
    public void eachDoneItemThatReadsDifferentlyIsDrift() {
        int[] seats = {8, 5};
        assertTrue(PresetMatch.drifted(ALL, new int[] {7, 5}, MATCH, ALL_ITEMS));
        assertTrue(PresetMatch.drifted(ALL, new int[] {8, 2}, MATCH, ALL_ITEMS));
        assertTrue(PresetMatch.drifted(ALL, seats, car(1, 0, 1, 2, 4, 0, 70f, 72f, 0, 0), ALL_ITEMS));
        assertTrue(PresetMatch.drifted(ALL, seats, car(1, 0, 1, 2, 4, 0, 70f, 72f, 1, 1), ALL_ITEMS));
        assertTrue(PresetMatch.drifted(ALL, seats, car(1, 0, 0, 2, 4, 0, 70f, 72f, 1, 0), ALL_ITEMS));
        assertTrue(PresetMatch.drifted(ALL, seats, car(1, 0, 1, 3, 4, 0, 70f, 72f, 1, 0), ALL_ITEMS));
        assertTrue(PresetMatch.drifted(ALL, seats, car(1, 0, 1, 2, 5, 0, 70f, 72f, 1, 0), ALL_ITEMS));
        assertTrue(PresetMatch.drifted(ALL, seats, car(1, 1, 1, 2, 4, 0, 70f, 72f, 1, 0), ALL_ITEMS));
        assertTrue(PresetMatch.drifted(ALL, seats, car(1, 0, 1, 2, 4, 1, 70f, 72f, 1, 0), ALL_ITEMS));
        assertTrue(PresetMatch.drifted(ALL, seats, car(1, 0, 1, 2, 4, 0, 71f, 72f, 1, 0), ALL_ITEMS));
        assertTrue(PresetMatch.drifted(ALL, seats, car(1, 0, 1, 2, 4, 0, 70f, 73f, 1, 0), ALL_ITEMS));
        // the climate turned off: manual no longer holds
        assertTrue(PresetMatch.drifted(ALL, seats, car(2, 0, 1, 2, 4, 0, 70f, 72f, 1, 0), ALL_ITEMS));
    }

    @Test
    public void unknownReadingsAreDrift() {
        int[] seats = {8, 5};
        assertTrue(PresetMatch.drifted(ALL, new int[] {-1, 5}, MATCH, ALL_ITEMS));
        assertTrue(PresetMatch.drifted(ALL, new int[] {8, 9}, MATCH, ALL_ITEMS));
        assertTrue(PresetMatch.drifted(ALL, null, MATCH, names(PresetMatch.DRIVER_SEAT)));
        assertTrue(PresetMatch.drifted(ALL, seats, CabinState.NONE, names(PresetMatch.WHEEL)));
        assertTrue(PresetMatch.drifted(ALL, seats, CabinState.NONE, names(PresetMatch.ECO)));
        assertTrue(PresetMatch.drifted(ALL, seats, CabinState.NONE, names(PresetMatch.AC)));
        assertTrue(PresetMatch.drifted(ALL, seats, CabinState.NONE, names(PresetMatch.FAN)));
        assertTrue(PresetMatch.drifted(ALL, seats, null, names(PresetMatch.DRIVER_TEMP)));
        assertTrue(PresetMatch.drifted(ALL, seats,
                car(1, 0, 1, 2, 4, 0, Float.NaN, 72f, 1, 0), names(PresetMatch.DRIVER_TEMP)));
        assertTrue(PresetMatch.drifted(ALL, seats,
                CabinState.of(-1, 1, 0, 1, 2, 4, 0, 0, 70f, 72f, 1, 0), names(PresetMatch.DRIVER_TEMP)));
    }

    @Test
    public void onlyDoneItemsCount() {
        CabinState nothingMatches = car(1, 1, 0, 3, 9, 1, 80f, 80f, 0, 1);
        assertFalse(PresetMatch.drifted(ALL, new int[] {2, 2}, nothingMatches,
                Collections.<String>emptySet()));
        assertFalse(PresetMatch.drifted(ALL, new int[] {8, 2}, nothingMatches,
                names(PresetMatch.DRIVER_SEAT)));
        assertTrue(PresetMatch.drifted(ALL, new int[] {8, 2}, nothingMatches,
                names(PresetMatch.DRIVER_SEAT, PresetMatch.WHEEL)));
    }

    @Test
    public void anItemNameItDoesNotKnowIsDrift() {
        assertTrue(PresetMatch.drifted(ALL, new int[] {8, 5}, MATCH, names("wheel on")));
    }

    @Test
    public void autoAndClimateOff() {
        Preset auto = new Preset("A", L, L, L, L, Preset.CLIMATE_AUTO, L, L, L, L, L, L);
        Set<String> a = names(PresetMatch.AUTO);
        assertFalse(PresetMatch.drifted(auto, SEATS_OFF, car(1, 1, 1, 0, 1, 0, 70f, 70f, 0, 0), a));
        assertTrue(PresetMatch.drifted(auto, SEATS_OFF, car(1, 0, 1, 0, 1, 0, 70f, 70f, 0, 0), a));
        assertTrue(PresetMatch.drifted(auto, SEATS_OFF, car(2, 1, 1, 0, 1, 0, 70f, 70f, 0, 0), a));
        Preset off = new Preset("O", L, L, L, L, Preset.CLIMATE_OFF, L, L, L, L, L, L);
        Set<String> o = names(PresetMatch.CLIMATE_OFF);
        assertFalse(PresetMatch.drifted(off, SEATS_OFF, car(2, 0, 0, 0, 0, 0, Float.NaN, Float.NaN, 0, 0), o));
        assertTrue(PresetMatch.drifted(off, SEATS_OFF, car(1, 0, 0, 0, 0, 0, 70f, 70f, 0, 0), o));
    }

    @Test
    public void aSyncedCarIsJudgedByTheDriversReadingForThePassenger() {
        Set<String> p = names(PresetMatch.PASSENGER_TEMP);
        Preset t = new Preset("T", L, L, L, L, L, L, 72, L, L, L, L);
        // synced: the passenger's own reading lags; the driver's is what it follows
        assertFalse(PresetMatch.drifted(t, SEATS_OFF, car(1, 0, 1, 2, 4, 1, 72f, 70f, 0, 0), p));
        assertTrue(PresetMatch.drifted(t, SEATS_OFF, car(1, 0, 1, 2, 4, 1, 70f, 72f, 0, 0), p));
        assertFalse(PresetMatch.drifted(t, SEATS_OFF, car(1, 0, 1, 2, 4, 0, 70f, 72f, 0, 0), p));
    }

    @Test
    public void celsiusTemperaturesCompareInHalfDegrees() {
        // 70 F is 21.0 C on the car's half-degree scale
        Preset t = new Preset("T", L, L, L, L, L, 70, L, L, L, L, L);
        Set<String> d = names(PresetMatch.DRIVER_TEMP);
        assertFalse(PresetMatch.drifted(t, SEATS_OFF,
                CabinState.of(1, 1, 0, 1, 2, 4, 0, 0, 21.0f, 21.0f, 0, 0), d));
        assertTrue(PresetMatch.drifted(t, SEATS_OFF,
                CabinState.of(1, 1, 0, 1, 2, 4, 0, 0, 21.5f, 21.0f, 0, 0), d));
    }

    // the runner's item names are the ones PresetMatch checks

    private final PresetRunnerTest.FakeCar car = new PresetRunnerTest.FakeCar();
    private final PresetRunnerTest.ManualScheduler sched = new PresetRunnerTest.ManualScheduler();
    private final CheckedSeatSenderTest.ListLog log = new CheckedSeatSenderTest.ListLog();
    private final PresetRunner runner =
            new PresetRunner(new CheckedCabinSender(car, log), car, sched, log);

    private void run(Preset p) {
        assertTrue(runner.start(p));
        sched.runAll();
        assertFalse(runner.busy());
    }

    @Test
    public void runnerDoneItemsNameWhatReachedItsTargetAndMatchTheCar() {
        car.allOff();
        Preset p = new Preset("Cold", L, L, 1, 1, Preset.CLIMATE_AUTO, 70, L, L, L, L, 1);
        run(p);
        assertEquals(names(PresetMatch.WHEEL, PresetMatch.ECO, PresetMatch.AUTO,
                PresetMatch.SYNC, PresetMatch.DRIVER_TEMP), runner.doneItems());
        assertFalse(runner.lastRunNeededNothing());
        assertFalse(PresetMatch.drifted(p, SEATS_OFF, car.read(), runner.doneItems()));
        car.eco = 0;
        assertTrue(PresetMatch.drifted(p, SEATS_OFF, car.read(), runner.doneItems()));
    }

    @Test
    public void manualRunNamesItsItems() {
        car.auto = 1;
        Preset p = new Preset("M", L, L, L, L, Preset.CLIMATE_MANUAL, L, L, 3, 1, 2, L);
        run(p);
        assertEquals(names(PresetMatch.AC, PresetMatch.AIRFLOW, PresetMatch.FAN,
                PresetMatch.MANUAL), runner.doneItems());
        assertFalse(PresetMatch.drifted(p, SEATS_OFF, car.read(), runner.doneItems()));
    }

    @Test
    public void climateOffRunNamesIt() {
        Preset p = new Preset("O", L, L, L, L, Preset.CLIMATE_OFF, L, L, L, L, L, L);
        run(p);
        assertEquals(names(PresetMatch.CLIMATE_OFF), runner.doneItems());
        assertFalse(PresetMatch.drifted(p, SEATS_OFF, car.read(), runner.doneItems()));
    }

    @Test
    public void notDoneItemsAreNotNamed() {
        car.deaf = true;
        run(new Preset("W", L, L, 1, 0, L, L, L, L, L, L, L));
        // eco was already off; the wheel press changed nothing
        assertEquals(names(PresetMatch.ECO), runner.doneItems());
        assertFalse(runner.lastRunNeededNothing());
    }

    @Test
    public void aSkippedPassengerIsNotNamed() {
        car.sync = 1;
        run(new Preset("T", L, L, L, L, L, 70, 74, L, L, L, L));
        assertEquals(names(PresetMatch.DRIVER_TEMP), runner.doneItems());
    }

    @Test
    public void aPassengerAlreadyAtItsTargetOnAnUnsyncedCarIsNamed() {
        car.passengerTemp = 74f;
        run(new Preset("T", L, L, L, L, L, 70, 74, L, L, L, L));
        assertEquals(names(PresetMatch.DRIVER_TEMP, PresetMatch.PASSENGER_TEMP), runner.doneItems());
        // with driver-only on the passenger is left alone
        car.driverOnly = 1;
        run(new Preset("T", L, L, L, L, L, 70, 74, L, L, L, L));
        assertEquals(names(PresetMatch.DRIVER_TEMP), runner.doneItems());
    }

    @Test
    public void aRunThatNeededNothingSaysSoAndNamesItsItems() {
        car.wheel = 1;
        run(new Preset("W", L, L, 1, 0, L, L, L, L, L, L, L));
        assertTrue(car.calls.isEmpty());
        assertTrue(runner.lastRunNeededNothing());
        assertEquals(names(PresetMatch.WHEEL, PresetMatch.ECO), runner.doneItems());
    }

    @Test
    public void beforeAnyRunNothingIsNamed() {
        assertTrue(runner.doneItems().isEmpty());
        assertFalse(runner.lastRunNeededNothing());
    }
}

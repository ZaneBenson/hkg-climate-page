package hkgclimate.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class PresetRunnerTest {
    /** A scheduler the test drives: tasks run in time order (same time: in the order scheduled). */
    static final class ManualScheduler implements Scheduler {
        private static final class Task {
            final long at;
            final long seq;
            final Runnable r;

            Task(long at, long seq, Runnable r) {
                this.at = at;
                this.seq = seq;
                this.r = r;
            }
        }

        private final List<Task> tasks = new ArrayList<Task>();
        private long seq;
        long now;

        public void after(long delayMs, Runnable r) { tasks.add(new Task(now + delayMs, seq++, r)); }

        void advance(long ms) {
            long end = now + ms;
            while (true) {
                Task next = null;
                for (Task t : tasks) {
                    if (t.at <= end && (next == null || t.at < next.at
                            || (t.at == next.at && t.seq < next.seq))) next = t;
                }
                if (next == null) break;
                tasks.remove(next);
                now = next.at;
                next.r.run();
            }
            now = end;
        }

        /** Runs everything, including what the tasks schedule, for at most a minute of time. */
        void runAll() {
            for (int i = 0; i < 600 && !tasks.isEmpty(); i++) advance(100);
        }

        boolean idle() { return tasks.isEmpty(); }
    }

    /**
     * The car's climate, steering wheel and eco mode, reacting at once to each command roughly as
     * the owner's car did in the captures. The order list also records seat sends for core tests.
     */
    static final class FakeCar implements CabinCalls, CabinReader {
        final List<String> calls = new ArrayList<String>();
        int unit = 2;
        int opState = 1;
        int auto = 0;
        int ac = 0;
        int airflow = 1;
        int fanRaw = 2;
        int sync = 0;
        int driverOnly = 0;
        int wheel = 0;
        int eco = 0;
        float driverTemp = 72f;
        float passengerTemp = 72f;
        /** What the climate shows after a command wakes it. */
        float wokenTemp = 71f;
        int wokenFanRaw = 1;
        boolean ignoreFan;
        boolean wrongWay;
        /** Each fan press moves the fan the opposite way, wrapping from 2 to 9 and back. */
        boolean fanWrongWay;
        boolean staysInAuto;
        Exception climateThrows;
        int reads;

        void allOff() {
            opState = 2;
            auto = 0;
            ac = 0;
            fanRaw = 0;
            sync = 0;
            wheel = 0;
            eco = 0;
            driverTemp = Float.NaN;
            passengerTemp = Float.NaN;
        }

        public CabinState read() {
            reads++;
            return CabinState.of(unit, opState, auto, ac, airflow, fanRaw, sync, driverOnly,
                    driverTemp, passengerTemp, wheel, eco);
        }

        public void climate(int flag, int value) throws Exception {
            calls.add("climate " + flag + " " + value);
            if (climateThrows != null) throw climateThrows;
            if (flag == 51) {
                allOffClimate();
                return;
            }
            if (opState == 2) {
                // any command wakes the climate, in auto with A/C (capture 1)
                opState = 1;
                auto = 1;
                ac = 1;
                fanRaw = wokenFanRaw;
                driverTemp = wokenTemp;
                passengerTemp = wokenTemp;
                return;
            }
            float step = unit == 1 ? 0.5f : 1f;
            switch (flag) {
                case 8: auto = 1; break;
                case 9: ac = value; if (value == 0 && !staysInAuto) auto = 0; break;
                case 6: airflow = value; if (!staysInAuto) auto = 0; break;
                case 4:
                    if (ignoreFan) break;
                    if (fanRaw == 1) fanRaw = 2;
                    else if (fanWrongWay) fanRaw = value == 1 ? (fanRaw == 2 ? 9 : fanRaw - 1)
                            : (fanRaw == 9 ? 2 : fanRaw + 1);
                    else fanRaw = Math.max(2, Math.min(9, fanRaw + (value == 1 ? 1 : -1)));
                    if (!staysInAuto) auto = 0;
                    break;
                case 22:
                    if (value == 2) {
                        sync = 1;
                        driverOnly = 0;
                        passengerTemp = driverTemp;
                    } else {
                        sync = 0;
                    }
                    break;
                case 50:
                    driverTemp += ((value == 1) != wrongWay) ? step : -step;
                    if (sync == 1) passengerTemp = driverTemp;
                    break;
                case 49:
                    passengerTemp += value == 1 ? step : -step;
                    sync = 0;
                    break;
                default: break;
            }
        }

        private void allOffClimate() {
            opState = 2;
            auto = 0;
            fanRaw = 0;
            driverTemp = Float.NaN;
            passengerTemp = Float.NaN;
        }

        public void wheel(int value) {
            calls.add("wheel " + value);
            if (value == 1) wheel = 1 - wheel;
        }

        public void eco(int value) {
            calls.add("eco " + value);
            eco = value == 2 ? 1 : 0;
        }
    }

    private static final int L = Preset.LEAVE;

    private final FakeCar car = new FakeCar();
    private final ManualScheduler sched = new ManualScheduler();
    private final CheckedSeatSenderTest.ListLog log = new CheckedSeatSenderTest.ListLog();
    private final PresetRunner runner =
            new PresetRunner(new CheckedCabinSender(car, log), car, sched, log);

    static Preset preset(int wheel, int eco, int climate, int tempF, int fan, int ac, int airflow,
            int sync) {
        return new Preset("Cold", L, L, wheel, eco, climate, tempF, fan, ac, airflow, sync);
    }

    private void run(Preset p) {
        assertTrue(runner.start(p));
        sched.runAll();
        assertFalse(runner.busy());
        assertTrue(sched.idle());
    }

    @Test
    public void coldPresetFromAllOffFollowsTheApplyOrder() {
        car.allOff();
        run(preset(1, 1, Preset.CLIMATE_AUTO, 70, L, L, L, 1));
        assertEquals(Arrays.asList("wheel 1", "wheel 0", "eco 2", "climate 8 1", "climate 22 2",
                "climate 50 2"), car.calls);
        assertEquals(70f, car.driverTemp, 0f);
        assertEquals(70f, car.passengerTemp, 0f);
        assertEquals("Cold: wheel on, eco on, auto, sync on, 70 °F (1 step)", runner.lastResult());
        assertEquals(runner.lastResult(), log.lines.get(log.lines.size() - 1));
    }

    @Test
    public void wheelPressIsReleasedBeforeTheNextItem() {
        assertTrue(runner.start(preset(1, 1, L, L, L, L, L, L)));
        assertEquals(Arrays.asList("wheel 1"), car.calls);
        sched.advance(199);
        assertEquals(Arrays.asList("wheel 1"), car.calls);
        sched.advance(1);
        assertEquals(Arrays.asList("wheel 1", "wheel 0", "eco 2"), car.calls);
        sched.runAll();
        assertEquals("Cold: wheel on, eco on", runner.lastResult());
    }

    @Test
    public void climateOffAtTheStartIsWokenByTheFirstSendAndLaterItemsUseFreshReads() {
        car.allOff();
        car.wokenTemp = 70f;
        car.wokenFanRaw = -1; // still unknown after waking
        run(preset(L, L, Preset.CLIMATE_MANUAL, 72, 3, 1, L, L));
        assertEquals(Arrays.asList("climate 9 1", "climate 50 1", "climate 50 1",
                "climate 49 1", "climate 49 1"), car.calls);
        assertEquals(1, car.opState);
        assertEquals(72f, car.driverTemp, 0f);
        assertEquals(72f, car.passengerTemp, 0f);
        assertEquals("Cold: A/C on, 72 °F (2 steps), passenger 72 °F (2 steps); not done: "
                + "fan 3 (unknown state), manual (still in auto)", runner.lastResult());
    }

    @Test
    public void climateOffWithEverythingUnknownSendsNothingAndNamesTheItems() {
        car.allOff();
        car.ac = -1;
        run(preset(L, L, Preset.CLIMATE_MANUAL, 72, 3, 1, L, L));
        assertTrue(car.calls.isEmpty());
        assertEquals("Cold: not done: A/C on (unknown state), fan 3 (unknown state), "
                + "manual (climate off), 72 °F (unknown state)", runner.lastResult());
    }

    @Test
    public void aFanPressWithNoChangeStopsTheFanAfterTheWaitAndIsReported() {
        car.ignoreFan = true;
        assertTrue(runner.start(preset(L, L, L, L, 3, L, L, 1)));
        assertEquals(Arrays.asList("climate 4 1"), car.calls);
        sched.advance(PresetRunner.STEP_WAIT_MS - 1);
        assertEquals(Arrays.asList("climate 4 1"), car.calls);
        sched.advance(1);
        // no second fan press: the next item goes ahead
        assertEquals(Arrays.asList("climate 4 1", "climate 22 2"), car.calls);
        sched.runAll();
        assertEquals("Cold: sync on; not done: fan 3 (no change after 1.5 s)", runner.lastResult());
    }

    @Test
    public void celsiusCarStepsHalfDegreesToTheNearestHalf() {
        car.unit = 1;
        car.driverTemp = 22.5f;
        car.passengerTemp = 22.5f;
        car.sync = 1;
        Preset p = preset(L, L, L, 70, L, L, L, L);
        run(p);
        assertEquals(Arrays.asList("climate 50 2", "climate 50 2", "climate 50 2"), car.calls);
        assertEquals(21.0f, car.driverTemp, 0f);
        assertEquals(21.0f, car.passengerTemp, 0f);
        assertEquals(70, p.tempF);
        assertEquals("Cold: 21.0 °C (3 steps)", runner.lastResult());
    }

    @Test
    public void celsiusTargetsAreClampedToTheStockRange() {
        car.unit = 1;
        car.driverTemp = 18.0f;
        car.sync = 1;
        car.passengerTemp = 18.0f;
        run(preset(L, L, L, 62, L, L, L, L)); // 16.5 °C by conversion
        assertEquals(Arrays.asList("climate 50 2", "climate 50 2"), car.calls);
        assertEquals(17.0f, car.driverTemp, 0f);
        assertEquals("Cold: 17.0 °C (2 steps)", runner.lastResult());

        car.calls.clear();
        car.driverTemp = 26.0f;
        run(preset(L, L, L, 82, L, L, L, L)); // 28.0 °C by conversion
        assertEquals(Arrays.asList("climate 50 1", "climate 50 1"), car.calls);
        assertEquals(27.0f, car.driverTemp, 0f);
    }

    @Test
    public void climateOffWhenAlreadyOffSendsNoCabinCommandButStillAppliesWheelAndEco() {
        car.allOff();
        car.eco = 1;
        run(preset(1, 0, Preset.CLIMATE_OFF, L, L, L, L, L));
        assertEquals(Arrays.asList("wheel 1", "wheel 0", "eco 1"), car.calls);
        assertEquals("Cold: wheel on, eco off, climate off", runner.lastResult());
    }

    @Test
    public void climateOffWhenOnSendsOneOff() {
        run(preset(L, L, Preset.CLIMATE_OFF, L, L, L, L, L));
        assertEquals(Arrays.asList("climate 51 1"), car.calls);
        assertEquals(2, car.opState);
        assertEquals("Cold: climate off", runner.lastResult());
    }

    @Test
    public void autoWhenAlreadyAutoSendsNoAutoPress() {
        car.auto = 1;
        run(preset(L, L, Preset.CLIMATE_AUTO, 72, L, L, L, L));
        assertTrue(car.calls.isEmpty());
        assertEquals("Cold: auto, 72 °F", runner.lastResult());
    }

    @Test
    public void autoFromManualIsOnePress() {
        run(preset(L, L, Preset.CLIMATE_AUTO, L, L, L, L, L));
        assertEquals(Arrays.asList("climate 8 1"), car.calls);
        assertEquals("Cold: auto", runner.lastResult());
    }

    @Test
    public void manualWhileAutoSendsEachChosenItemOnceEvenIfEqual() {
        car.auto = 1;
        car.staysInAuto = true;
        car.ac = 1;
        car.airflow = 2;
        run(preset(L, L, Preset.CLIMATE_MANUAL, L, L, 1, 2, L));
        assertEquals(Arrays.asList("climate 9 1", "climate 6 2"), car.calls);
        assertEquals("Cold: A/C on, airflow face and floor; not done: manual (still in auto)",
                runner.lastResult());
    }

    @Test
    public void manualOutsideAutoSkipsItemsAlreadyThere() {
        car.ac = 1;
        car.airflow = 2;
        run(preset(L, L, Preset.CLIMATE_MANUAL, L, L, 1, 2, L));
        assertTrue(car.calls.isEmpty());
        assertEquals("Cold: A/C on, airflow face and floor, manual", runner.lastResult());
    }

    @Test
    public void manualLeavesAutoWhenTheCarDoes() {
        car.auto = 1;
        car.ac = 1;
        run(preset(L, L, Preset.CLIMATE_MANUAL, L, L, 0, 3, L));
        assertEquals(Arrays.asList("climate 9 0", "climate 6 3"), car.calls);
        assertEquals("Cold: A/C off, airflow floor, manual", runner.lastResult());
    }

    @Test
    public void fanFromAutoRawOneIsOnePressThenSteps() {
        car.auto = 1;
        car.fanRaw = 1;
        run(preset(L, L, Preset.CLIMATE_MANUAL, L, 3, L, L, L));
        assertEquals(Arrays.asList("climate 4 1", "climate 4 1", "climate 4 1"), car.calls);
        assertEquals(4, car.fanRaw);
        assertEquals("Cold: fan 3 (3 steps), manual", runner.lastResult());
    }

    @Test
    public void fanStepsDown() {
        car.fanRaw = 9;
        run(preset(L, L, L, L, 6, L, L, L));
        assertEquals(Collections.nCopies(2, "climate 4 2"), car.calls);
        assertEquals("Cold: fan 6 (2 steps)", runner.lastResult());
    }

    @Test
    public void syncOffIsSentAsOne() {
        car.sync = 1;
        run(preset(L, L, L, L, L, L, L, 0));
        assertEquals(Arrays.asList("climate 22 1"), car.calls);
        assertEquals("Cold: sync off", runner.lastResult());
    }

    @Test
    public void driverOnlyOnSendsNoPassengerStep() {
        car.driverOnly = 1;
        car.passengerTemp = 0f; // not shown while driver-only is on
        run(preset(L, L, L, 70, L, L, L, L));
        assertEquals(Collections.nCopies(2, "climate 50 2"), car.calls);
        assertEquals("Cold: 70 °F (2 steps)", runner.lastResult());

        car.calls.clear();
        car.driverOnly = 1;
        car.passengerTemp = 75f;
        run(preset(L, L, L, 72, L, L, L, L));
        assertEquals(Collections.nCopies(2, "climate 50 1"), car.calls);
    }

    @Test
    public void passengerNotShownSendsNoPassengerStep() {
        car.passengerTemp = Float.NaN;
        run(preset(L, L, L, 71, L, L, L, L));
        assertEquals(Arrays.asList("climate 50 2"), car.calls);
    }

    @Test
    public void passengerIsNotSteppedWhileSyncIsOnEvenIfItLags() {
        car.sync = 1;
        car.passengerTemp = 75f; // the car has not caught up
        run(preset(L, L, L, 72, L, L, L, L));
        assertTrue(car.calls.isEmpty());
        assertEquals("Cold: 72 °F; not done: passenger 72 °F (did not follow sync)",
                runner.lastResult());

        // it catches up while the runner waits
        assertTrue(runner.start(preset(L, L, L, 72, L, L, L, L)));
        sched.after(300, new Runnable() {
            public void run() { car.passengerTemp = 72f; }
        });
        sched.runAll();
        assertTrue(car.calls.isEmpty());
        assertEquals("Cold: 72 °F", runner.lastResult());
    }

    @Test
    public void aTemperatureMovingTheWrongWayStopsAtTheCap() {
        assertEquals(25, PresetRunner.TEMP_CAP);
        car.wrongWay = true;
        car.driverOnly = 1;
        car.passengerTemp = 0f;
        run(preset(L, L, L, 70, L, L, L, L));
        assertEquals(Collections.nCopies(25, "climate 50 2"), car.calls);
        assertEquals("Cold: not done: 70 °F (stopped after 25 presses)", runner.lastResult());
    }

    @Test
    public void aFanMovingTheWrongWayStopsAtTheCap() {
        assertEquals(10, PresetRunner.FAN_CAP);
        car.fanWrongWay = true;
        car.fanRaw = 5;
        run(preset(L, L, L, L, 6, L, L, L));
        assertEquals(10, car.calls.size());
        assertEquals(Collections.nCopies(4, "climate 4 1"), car.calls.subList(0, 4));
        assertEquals("Cold: not done: fan 6 (stopped after 10 presses)", runner.lastResult());
    }

    @Test
    public void manualInAutoAtAMidRangeFanMatchPressesUpThenDownAndEndsAtTarget() {
        car.auto = 1;
        car.fanRaw = 5; // fan 4, already the target
        run(preset(L, L, Preset.CLIMATE_MANUAL, L, 4, L, L, L));
        assertEquals(Arrays.asList("climate 4 1", "climate 4 2"), car.calls);
        assertEquals(5, car.fanRaw);
        assertEquals(0, car.auto);
        assertEquals("Cold: fan 4 (2 steps), manual", runner.lastResult());
    }

    @Test
    public void unknownWheelAndEcoAreSkippedAndNamed() {
        car.wheel = -1;
        car.eco = 5;
        run(preset(1, 0, L, L, L, L, L, L));
        assertTrue(car.calls.isEmpty());
        assertEquals("Cold: not done: wheel on (unknown state), eco off (unknown state)",
                runner.lastResult());
    }

    @Test
    public void unknownCabinStateSendsNothing() {
        car.opState = -1;
        run(preset(L, L, Preset.CLIMATE_AUTO, 70, L, L, L, 1));
        assertTrue(car.calls.isEmpty());
        assertEquals("Cold: not done: auto (unknown state), sync on (unknown state), "
                + "70 °F (unknown state)", runner.lastResult());
    }

    @Test
    public void unknownUnitOrTemperatureSendsNoStep() {
        car.unit = -1;
        run(preset(L, L, L, 70, L, L, L, L));
        car.unit = 2;
        car.driverTemp = Float.NaN;
        run(preset(L, L, L, 70, L, L, L, L));
        assertTrue(car.calls.isEmpty());
        assertEquals("Cold: not done: 70 °F (unknown state)", runner.lastResult());
    }

    @Test
    public void aFailingSendIsNotRetriedAndTheRunGoesOn() {
        car.climateThrows = new IllegalStateException("refused");
        run(preset(1, L, L, L, L, 1, L, L));
        assertEquals(Arrays.asList("wheel 1", "wheel 0", "climate 9 1"), car.calls);
        assertEquals("Cold: wheel on; not done: A/C on (not sent)", runner.lastResult());
    }

    @Test
    public void busyWhileRunningAndASecondStartIsRefused() {
        assertNull(runner.lastResult());
        assertFalse(runner.busy());
        assertTrue(runner.start(preset(L, L, L, 70, L, L, L, L)));
        assertTrue(runner.busy());
        assertFalse(runner.start(preset(1, L, L, L, L, L, L, L)));
        sched.runAll();
        assertFalse(runner.busy());
        assertFalse(car.calls.contains("wheel 1"));
        assertTrue(runner.start(preset(1, L, L, L, L, L, L, L)));
        sched.runAll();
        assertTrue(car.calls.contains("wheel 1"));
    }

    @Test
    public void startRefusesNullSeatOnlyAndUncheckedPresets() {
        assertFalse(runner.start(null));
        assertFalse(runner.start(new Preset("A", 8, 8)));
        assertFalse(runner.start(preset(L, L, L, L, 9, L, L, L)));
        assertFalse(runner.start(preset(2, L, L, L, L, L, L, L)));
        assertFalse(runner.start(new Preset("", L, L, 1, L, L, L, L, L, L, L)));
        assertFalse(runner.busy());
        assertTrue(car.calls.isEmpty());
        assertEquals(0, car.reads);
    }

    @Test
    public void aThrowingReaderCountsAsUnknown() {
        PresetRunner r = new PresetRunner(new CheckedCabinSender(car, log), new CabinReader() {
            public CabinState read() { throw new IllegalStateException("gone"); }
        }, sched, log);
        assertTrue(r.start(preset(1, L, L, 70, L, L, L, L)));
        sched.runAll();
        assertFalse(r.busy());
        assertTrue(car.calls.isEmpty());
        assertEquals("Cold: not done: wheel on (unknown state), 70 °F (unknown state)", r.lastResult());
    }

    @Test
    public void aFailingSchedulerEndsTheRunWithoutGettingStuck() {
        PresetRunner r = new PresetRunner(new CheckedCabinSender(car, log), car, new Scheduler() {
            public void after(long delayMs, Runnable task) { throw new IllegalStateException("gone"); }
        }, log);
        assertTrue(r.start(preset(L, L, L, 70, L, 1, L, L)));
        assertFalse(r.busy());
        assertEquals(Arrays.asList("climate 9 1"), car.calls);
        assertEquals("Cold: not done: A/C on (stopped: java.lang.IllegalStateException: gone)",
                r.lastResult());
    }
}

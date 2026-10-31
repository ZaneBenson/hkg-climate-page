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
        /** The car ignores every command (they are still recorded). */
        boolean deaf;
        /** The next this-many temperature and fan presses are ignored (recorded, no effect). */
        int dropFirst;
        /** Every temperature or fan press that lands is preceded by this many ignored ones. */
        int dropPerLanded;
        private int dropRun;
        Exception climateThrows;
        int reads;
        /** When set, the scheduler time of each call is kept in times (one per entry of calls). */
        ManualScheduler clock;
        final List<Long> times = new ArrayList<Long>();

        private void note(String call) {
            calls.add(call);
            if (clock != null) times.add(clock.now);
        }

        private boolean dropped() {
            if (dropFirst > 0) {
                dropFirst--;
                return true;
            }
            if (dropPerLanded > 0) {
                if (dropRun < dropPerLanded) {
                    dropRun++;
                    return true;
                }
                dropRun = 0;
            }
            return false;
        }

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
            note("climate " + flag + " " + value);
            if (climateThrows != null) throw climateThrows;
            if (deaf) return;
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
                    if (ignoreFan || dropped()) break;
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
                    if (dropped()) break;
                    driverTemp += ((value == 1) != wrongWay) ? step : -step;
                    if (sync == 1) passengerTemp = driverTemp;
                    break;
                case 49:
                    if (dropped()) break;
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
            note("wheel " + value);
            if (deaf) return;
            if (value == 1) wheel = 1 - wheel;
        }

        public void eco(int value) {
            note("eco " + value);
            if (deaf) return;
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
        return new Preset("Cold", L, L, wheel, eco, climate, tempF, tempF, fan, ac, airflow, sync);
    }

    /** A preset with only the two temperatures and Sync. */
    static Preset temps(int driverTempF, int passengerTempF, int sync) {
        return new Preset("Cold", L, L, L, L, L, driverTempF, passengerTempF, L, L, L, sync);
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
        // the release goes out at 200; the next command waits 400 ms after it
        assertEquals(Arrays.asList("wheel 1", "wheel 0"), car.calls);
        sched.advance(399);
        assertEquals(Arrays.asList("wheel 1", "wheel 0"), car.calls);
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
        // two retries, 500 ms apart (see the retry tests), then the last press waits the full time
        sched.advance(1000 + PresetRunner.STEP_WAIT_MS - 1);
        assertEquals(Collections.nCopies(3, "climate 4 1"), car.calls);
        sched.advance(1);
        // no further fan press: the next item goes ahead
        assertEquals(Arrays.asList("climate 4 1", "climate 4 1", "climate 4 1", "climate 22 2"),
                car.calls);
        sched.runAll();
        assertEquals("Cold: sync on; not done: fan 3 (no change after 3 presses)",
                runner.lastResult());
    }

    private static List<Long> times(long... t) {
        List<Long> out = new ArrayList<Long>();
        for (long x : t) out.add(x);
        return out;
    }

    @Test
    public void constantsAreTheApprovedOnes() {
        assertEquals(400, PresetRunner.COMMAND_GAP_MS);
        assertEquals(500, PresetRunner.RETRY_WAIT_MS);
        assertEquals(2, PresetRunner.MAX_RETRIES);
    }

    @Test
    public void consecutiveCommandsAreAtLeast400MsApartAndTheGapEndsWithAReRead() {
        car.clock = sched;
        run(preset(L, L, Preset.CLIMATE_MANUAL, 70, L, 1, L, L));
        assertEquals(Arrays.asList("climate 9 1", "climate 50 2", "climate 50 2", "climate 49 2",
                "climate 49 2"), car.calls);
        // the car reported each press within 100 ms; the next press still waited out the 400 ms
        assertEquals(times(0, 400, 800, 1200, 1600), car.times);
        assertEquals("Cold: A/C on, manual, 70 °F (2 steps), passenger 70 °F (2 steps)",
                runner.lastResult());
    }

    @Test
    public void theCommandAfterTheGapDecidesFromAFreshRead() {
        car.clock = sched;
        assertTrue(runner.start(preset(L, L, L, 70, L, L, L, L)));
        sched.advance(100); // the first press was reported; the next waits for 400 ms
        assertEquals(1, car.calls.size());
        int readsBefore = car.reads;
        // the temperature is moved by someone else during the gap: 71 is one press from 70
        car.driverTemp = 71f;
        car.passengerTemp = 71f;
        sched.advance(299);
        assertEquals(readsBefore, car.reads);
        assertEquals(1, car.calls.size());
        sched.advance(1);
        assertTrue(car.reads > readsBefore);
        assertEquals(2, car.calls.size());
        sched.runAll();
        assertEquals(70f, car.driverTemp, 0f);
    }

    @Test
    public void aFirstDelayHoldsBackTheFirstCommandAndZeroSendsAtOnce() {
        car.clock = sched;
        assertTrue(runner.start(preset(1, L, L, L, L, L, L, L), PresetRunner.COMMAND_GAP_MS));
        assertTrue(runner.busy());
        assertEquals("Cold: applying", runner.lastResult());
        assertTrue(car.calls.isEmpty());
        sched.advance(399);
        assertTrue(car.calls.isEmpty());
        sched.advance(1);
        assertEquals(Arrays.asList("wheel 1"), car.calls);
        sched.runAll();
        assertFalse(runner.busy());
        assertEquals("Cold: wheel on", runner.lastResult());

        car.calls.clear();
        car.wheel = 0;
        assertTrue(runner.start(preset(1, L, L, L, L, L, L, L), 0));
        assertEquals(Arrays.asList("wheel 1"), car.calls);
        sched.runAll();
    }

    @Test
    public void aFirstDelayStillReadsTheCarOnlyAfterTheDelay() {
        assertTrue(runner.start(preset(1, L, L, L, L, L, L, L), 400));
        assertEquals(0, car.reads);
        car.wheel = 1; // someone turned it on meanwhile
        sched.runAll();
        assertTrue(car.calls.isEmpty());
        assertEquals("Cold: wheel on", runner.lastResult());
    }

    @Test
    public void aFailingSchedulerOnADelayedStartEndsTheRun() {
        PresetRunner r = new PresetRunner(new CheckedCabinSender(car, log), car, new Scheduler() {
            public void after(long delayMs, Runnable task) { throw new IllegalStateException("gone"); }
        }, log);
        assertTrue(r.start(preset(1, L, L, L, L, L, L, L), 400));
        assertFalse(r.busy());
        assertTrue(car.calls.isEmpty());
        assertEquals("Cold: not done: wheel on (stopped: java.lang.IllegalStateException: gone)",
                r.lastResult());
    }

    @Test
    public void aDroppedTemperaturePressIsPressedAgainAfter500Ms() {
        car.clock = sched;
        car.driverOnly = 1;
        car.passengerTemp = 0f;
        car.dropFirst = 1;
        run(preset(L, L, L, 71, L, L, L, L));
        assertEquals(Collections.nCopies(2, "climate 50 2"), car.calls);
        assertEquals(times(0, 500), car.times);
        assertEquals(71f, car.driverTemp, 0f);
        assertEquals("Cold: 71 °F (2 steps)", runner.lastResult());
    }

    @Test
    public void aTemperaturePressDroppedTwiceLandsOnTheThirdTry() {
        car.clock = sched;
        car.driverOnly = 1;
        car.passengerTemp = 0f;
        car.dropFirst = 2;
        run(preset(L, L, L, 71, L, L, L, L));
        assertEquals(times(0, 500, 1000), car.times);
        assertEquals(71f, car.driverTemp, 0f);
        assertEquals("Cold: 71 °F (3 steps)", runner.lastResult());
    }

    @Test
    public void aTemperaturePressDroppedEveryTimeIsSentThreeTimesThenReportedAndTheRunGoesOn() {
        car.clock = sched;
        car.driverOnly = 1;
        car.passengerTemp = 0f;
        car.dropFirst = 99;
        run(preset(L, L, Preset.CLIMATE_MANUAL, 71, L, 1, L, L));
        assertEquals(Arrays.asList("climate 9 1", "climate 50 2", "climate 50 2", "climate 50 2"),
                car.calls);
        assertEquals(times(0, 400, 900, 1400), car.times);
        assertEquals(72f, car.driverTemp, 0f);
        assertEquals("Cold: A/C on, manual; not done: 71 °F (no change after 3 presses)",
                runner.lastResult());
    }

    @Test
    public void theNoChangeCountIsThePressesOfTheLastStepNotOfTheWholeItem() {
        car.clock = sched;
        car.driverOnly = 1;
        car.passengerTemp = 0f;
        assertTrue(runner.start(preset(L, L, L, 70, L, L, L, L)));
        car.dropFirst = 99; // the first press landed (72 -> 71); every later one is dropped
        sched.runAll();
        assertFalse(runner.busy());
        assertEquals(Collections.nCopies(4, "climate 50 2"), car.calls);
        assertEquals(times(0, 400, 900, 1400), car.times);
        assertEquals("Cold: not done: 70 °F (no change after 3 presses)", runner.lastResult());
    }

    @Test
    public void aPressThatLandsIsNeverRepeatedEvenWhenTheNextOneIsDropped() {
        car.clock = sched;
        car.driverOnly = 1;
        car.passengerTemp = 0f;
        car.dropPerLanded = 1; // each step: one ignored press, then one that lands
        run(preset(L, L, L, 70, L, L, L, L));
        assertEquals(4, car.calls.size());
        assertEquals(70f, car.driverTemp, 0f);
        // landing resets the retries: each step gets its own two
        assertEquals("Cold: 70 °F (4 steps)", runner.lastResult());
    }

    @Test
    public void aDroppedPassengerStepIsRetriedToo() {
        car.clock = sched;
        car.driverTemp = 71f;
        car.passengerTemp = 72f;
        car.dropFirst = 1;
        run(preset(L, L, L, 71, L, L, L, L));
        assertEquals(Arrays.asList("climate 49 2", "climate 49 2"), car.calls);
        assertEquals(times(0, 500), car.times);
        assertEquals(71f, car.passengerTemp, 0f);
        assertEquals("Cold: 71 °F, passenger 71 °F (2 steps)", runner.lastResult());
    }

    @Test
    public void aDroppedFanPressIsRetriedAndLandsOnce() {
        car.clock = sched;
        car.fanRaw = 3;
        car.dropFirst = 2;
        run(preset(L, L, L, L, 3, L, L, L)); // fan 3 is raw 4
        assertEquals(Collections.nCopies(3, "climate 4 1"), car.calls);
        assertEquals(times(0, 500, 1000), car.times);
        assertEquals(4, car.fanRaw);
        assertEquals("Cold: fan 3 (3 steps)", runner.lastResult());
    }

    @Test
    public void retriesCountTowardTheTemperatureCap() {
        car.clock = sched;
        car.wrongWay = true;
        car.driverOnly = 1;
        car.passengerTemp = 0f;
        car.dropPerLanded = 2; // two ignored presses before each that lands (the wrong way)
        run(preset(L, L, L, 70, L, L, L, L));
        assertEquals(Collections.nCopies(PresetRunner.TEMP_CAP, "climate 50 2"), car.calls);
        assertEquals("Cold: not done: 70 °F (stopped after 25 presses)", runner.lastResult());
    }

    @Test
    public void retriesCountTowardTheFanCap() {
        car.fanWrongWay = true;
        car.fanRaw = 5;
        car.dropPerLanded = 1;
        run(preset(L, L, L, L, 6, L, L, L));
        assertEquals(PresetRunner.FAN_CAP, car.calls.size());
        assertEquals("Cold: not done: fan 6 (stopped after 10 presses)", runner.lastResult());
    }

    private void assertSentOnce(Preset p, String... calls) {
        car.deaf = true;
        run(p);
        assertEquals(Arrays.asList(calls), car.calls);
    }

    @Test
    public void nothingButTemperatureAndFanStepsIsEverRetried() {
        car.ac = 0;
        assertSentOnce(preset(L, L, Preset.CLIMATE_MANUAL, L, L, 1, L, L), "climate 9 1");
        assertEquals("Cold: manual; not done: A/C on (no change after 1.5 s)", runner.lastResult());

        car.calls.clear();
        assertSentOnce(preset(L, L, Preset.CLIMATE_AUTO, L, L, L, L, L), "climate 8 1");

        car.calls.clear();
        assertSentOnce(preset(L, L, Preset.CLIMATE_MANUAL, L, L, L, 2, L), "climate 6 2");

        car.calls.clear();
        assertSentOnce(preset(L, L, L, L, L, L, L, 1), "climate 22 2");

        car.calls.clear();
        assertSentOnce(preset(L, L, Preset.CLIMATE_OFF, L, L, L, L, L), "climate 51 1");

        car.calls.clear();
        assertSentOnce(preset(1, L, L, L, L, L, L, L), "wheel 1", "wheel 0");

        car.calls.clear();
        assertSentOnce(preset(L, 1, L, L, L, L, L, L), "eco 2");
    }

    @Test
    public void aForcedPressIsNeverRetried() {
        car.auto = 1;
        car.fanRaw = 5; // fan 4 is already the target; the press only leaves auto
        assertSentOnce(preset(L, L, Preset.CLIMATE_MANUAL, L, 4, L, L, L), "climate 4 1");

        car.calls.clear();
        car.fanRaw = 2; // fan 1 at the low end: forced press is a down press
        assertSentOnce(preset(L, L, Preset.CLIMATE_MANUAL, L, 1, L, L, L), "climate 4 2");
    }

    @Test
    public void aTemperatureStepIsNotRetriedWhenSyncHoldsThePassengerBack() {
        car.sync = 1;
        car.passengerTemp = 75f;
        car.driverTemp = 72f;
        car.deaf = true;
        run(preset(L, L, L, 72, L, L, L, L));
        assertTrue(car.calls.isEmpty());
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
        assertEquals(70, p.driverTempF);
        assertEquals(70, p.passengerTempF);
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
    public void aSyncedPassengerWithTheDriversTargetIsNotSteppedEvenIfItsReadingLags() {
        car.sync = 1;
        car.passengerTemp = 75f; // the car has not caught up
        run(preset(L, L, L, 72, L, L, L, L));
        assertTrue(car.calls.isEmpty());
        assertEquals("Cold: 72 °F", runner.lastResult());
    }

    @Test
    public void syncOnInThePresetStepsOnlyTheDriverAndThePassengerFollows() {
        car.sync = 0;
        car.driverTemp = 72f;
        car.passengerTemp = 75f;
        run(temps(70, 66, 1)); // the 66 is dropped: Sync on has no passenger temperature
        assertEquals(Arrays.asList("climate 22 2", "climate 50 2", "climate 50 2"), car.calls);
        assertEquals(70f, car.driverTemp, 0f);
        assertEquals(70f, car.passengerTemp, 0f);
        assertEquals("Cold: sync on, 70 °F (2 steps)", runner.lastResult());
    }

    @Test
    public void differentTemperaturesStepEachSideToItsOwnTarget() {
        car.driverTemp = 72f;
        car.passengerTemp = 72f;
        run(temps(70, 74, 0));
        assertEquals(Arrays.asList("climate 50 2", "climate 50 2", "climate 49 1", "climate 49 1"),
                car.calls);
        assertEquals(70f, car.driverTemp, 0f);
        assertEquals(74f, car.passengerTemp, 0f);
        assertEquals("Cold: sync off, 70 °F (2 steps), passenger 74 °F (2 steps)",
                runner.lastResult());
    }

    @Test
    public void syncOffInThePresetLetsADifferentPassengerTargetBeSteppedOnASyncedCar() {
        car.sync = 1;
        car.driverTemp = 72f;
        car.passengerTemp = 72f;
        run(temps(70, 74, 0));
        assertEquals(Arrays.asList("climate 22 1", "climate 50 2", "climate 50 2", "climate 49 1",
                "climate 49 1"), car.calls);
        assertEquals(70f, car.driverTemp, 0f);
        assertEquals(74f, car.passengerTemp, 0f);
        assertEquals("Cold: sync off, 70 °F (2 steps), passenger 74 °F (2 steps)",
                runner.lastResult());
    }

    @Test
    public void aSyncedCarWithSyncLeftSkipsADifferentPassengerAndNamesIt() {
        car.sync = 1;
        car.driverTemp = 72f;
        car.passengerTemp = 72f;
        run(temps(70, 74, L));
        // the driver steps (the passenger follows it); the passenger's own target would need sync off
        assertEquals(Arrays.asList("climate 50 2", "climate 50 2"), car.calls);
        assertEquals(1, car.sync);
        assertEquals(70f, car.passengerTemp, 0f);
        assertEquals("Cold: 70 °F (2 steps); not done: passenger 74 °F (sync on)",
                runner.lastResult());
    }

    @Test
    public void aSyncedCarWithOnlyAPassengerTargetSendsNothingAndNamesIt() {
        car.sync = 1;
        car.driverTemp = 72f;
        car.passengerTemp = 72f;
        run(temps(L, 70, L));
        assertTrue(car.calls.isEmpty());
        assertEquals("Cold: not done: passenger 70 °F (sync on)", runner.lastResult());
    }

    @Test
    public void aSyncedCarWithTheSameTargetOnBothSidesIsSteppedOnceAndNothingIsNamed() {
        car.sync = 1;
        car.driverTemp = 72f;
        car.passengerTemp = 72f;
        run(temps(70, 70, L));
        assertEquals(Collections.nCopies(2, "climate 50 2"), car.calls);
        assertEquals(1, car.sync);
        assertEquals("Cold: 70 °F (2 steps)", runner.lastResult());
    }

    @Test
    public void aPassengerOnlyPresetStepsThePassengerOnAnUnsyncedCar() {
        car.driverTemp = 72f;
        car.passengerTemp = 72f;
        run(temps(L, 70, L));
        assertEquals(Collections.nCopies(2, "climate 49 2"), car.calls);
        assertEquals(72f, car.driverTemp, 0f);
        assertEquals(70f, car.passengerTemp, 0f);
        assertEquals("Cold: passenger 70 °F (2 steps)", runner.lastResult());
    }

    @Test
    public void aDriverOnlyOnCarSkipsThePassengerWhateverItsTarget() {
        car.driverOnly = 1;
        car.driverTemp = 72f;
        car.passengerTemp = 0f; // not shown
        run(temps(70, 74, 0));
        assertEquals(Collections.nCopies(2, "climate 50 2"), car.calls);
        assertEquals(70f, car.driverTemp, 0f);
        assertEquals("Cold: sync off, 70 °F (2 steps)", runner.lastResult());
    }

    @Test
    public void aPassengerOnlyPresetOnADriverOnlyCarSendsNothing() {
        car.driverOnly = 1;
        car.passengerTemp = 75f; // stale reading
        run(temps(L, 70, L));
        assertTrue(car.calls.isEmpty());
        assertEquals("Cold: nothing to do", runner.lastResult());
    }

    @Test
    public void celsiusPassengerTargetIsConvertedAndLabelledOnItsOwn() {
        car.unit = 1;
        car.driverTemp = 22.0f;
        car.passengerTemp = 22.0f;
        run(temps(72, 70, 0)); // 22.0 °C and 21.0 °C
        assertEquals(Collections.nCopies(2, "climate 49 2"), car.calls);
        assertEquals(22.0f, car.driverTemp, 0f);
        assertEquals(21.0f, car.passengerTemp, 0f);
        assertEquals("Cold: sync off, 22.0 °C, passenger 21.0 °C (2 steps)", runner.lastResult());
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
        assertFalse(runner.start(new Preset("", L, L, 1, L, L, L, L, L, L, L, L)));
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

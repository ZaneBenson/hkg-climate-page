package hkgclimate.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class PresetListTest {
    private static Preset p(String n) { return new Preset(n, 8, 5); }

    private static List<Preset> list(String... names) {
        List<Preset> l = new ArrayList<Preset>();
        for (String n : names) l.add(p(n));
        return l;
    }

    private static String names(List<Preset> l) {
        StringBuilder b = new StringBuilder();
        for (Preset x : l) b.append(x.name).append(',');
        return b.toString();
    }

    @Test
    public void checkAcceptsValidPresets() {
        assertNull(Preset.check("Cold", 8, 8));
        assertNull(Preset.check("a", 2, Preset.LEAVE));
        assertNull(Preset.check("0123456789", Preset.LEAVE, 3));
        assertNull(Preset.check("  pad  ", 5, 5));
    }

    @Test
    public void checkRefusesBadNames() {
        assertEquals("name 1-10 characters", Preset.check("", 8, 8));
        assertEquals("name 1-10 characters", Preset.check("   ", 8, 8));
        assertEquals("name 1-10 characters", Preset.check(null, 8, 8));
        assertEquals("name 1-10 characters", Preset.check("01234567890", 8, 8));
        assertNotNull(Preset.check("a\tb", 8, 8));
        assertNotNull(Preset.check("a\nb", 8, 8));
        assertNotNull(Preset.check("a\rb", 8, 8));
    }

    @Test
    public void checkRefusesBadSeats() {
        assertEquals("choose at least one seat", Preset.check("x", Preset.LEAVE, Preset.LEAVE));
        assertEquals("bad seat value", Preset.check("x", 1, 8));
        assertEquals("bad seat value", Preset.check("x", 8, 9));
        assertEquals("bad seat value", Preset.check("x", -2, 8));
        assertEquals("bad seat value", Preset.check("x", Preset.LEAVE, 0));
    }

    @Test
    public void presetTrimsItsName() {
        assertEquals("ab", new Preset("  ab ", 2, 2).name);
        assertEquals(new Preset("ab", 2, 2), new Preset(" ab", 2, 2));
        assertEquals(new Preset("ab", 2, 2).hashCode(), new Preset("ab ", 2, 2).hashCode());
    }

    @Test
    public void codecRoundTrips() {
        List<Preset> in = Arrays.asList(new Preset("Cold", 8, 8), new Preset("Warm", 5, Preset.LEAVE),
                new Preset("Off", 2, 2));
        assertEquals("Cold\t8\t8\t-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1\n"
                + "Warm\t5\t-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1\n"
                + "Off\t2\t2\t-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1\n", PresetList.encode(in));
        assertEquals(in, PresetList.decode(PresetList.encode(in)));
    }

    @Test
    public void decodeOfNullAndEmptyIsEmpty() {
        assertTrue(PresetList.decode(null).isEmpty());
        assertTrue(PresetList.decode("").isEmpty());
        assertEquals("", PresetList.encode(null));
    }

    @Test
    public void decodeSkipsGarbageWithoutThrowing() {
        String text = "garbage\n"
                + "\n"
                + "ok\t8\t5\n"
                + "this name is too long\t8\t5\n"
                + "x\tx\t5\n"
                + "x\t8\n"
                + "x\t8\t5\textra\n"
                + "x\t1\t9\n"
                + "x\t-1\t-1\n"
                + "x\t8\t\n"
                + "\t8\t5\n"
                + "x\t 8\t5\n"
                + "x\t99999999999999\t5\n"
                + "fine\t-1\t3\n";
        assertEquals(Arrays.asList(new Preset("ok", 8, 5), new Preset("fine", Preset.LEAVE, 3)),
                PresetList.decode(text));
    }

    @Test
    public void decodeAcceptsCarriageReturnLineEnds() {
        assertEquals(Arrays.asList(new Preset("a", 8, 5), new Preset("b", 2, 2)),
                PresetList.decode("a\t8\t5\r\nb\t2\t2\r\n"));
    }

    @Test
    public void decodeKeepsAtMostFour() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 6; i++) b.append("p").append(i).append("\t8\t8\n");
        List<Preset> out = PresetList.decode(b.toString());
        assertEquals(4, out.size());
        assertEquals("p0,p1,p2,p3,", names(out));
    }

    @Test
    public void encodeDropsInvalidPresetsAndExtras() {
        List<Preset> in = Arrays.asList(new Preset("ok", 8, 8), new Preset("a\tb", 8, 8),
                new Preset("", 8, 8), new Preset("none", -1, -1), new Preset("c", 2, 2));
        assertEquals("ok\t8\t8\t-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1\n"
                + "c\t2\t2\t-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1\n", PresetList.encode(in));
        assertEquals(4, PresetList.decode(PresetList.encode(list("a", "b", "c", "d", "e"))).size());
    }

    @Test
    public void addAppendsUntilFull() {
        List<Preset> l = new ArrayList<Preset>();
        for (String n : new String[] {"a", "b", "c", "d"}) l = PresetList.add(l, p(n));
        assertEquals("a,b,c,d,", names(l));
        List<Preset> full = PresetList.add(l, p("e"));
        assertEquals("a,b,c,d,", names(full));
    }

    @Test
    public void editsReturnNewListsAndLeaveTheInputAlone() {
        List<Preset> in = list("a", "b");
        PresetList.add(in, p("c"));
        PresetList.remove(in, 0);
        PresetList.replace(in, 1, p("z"));
        PresetList.moveDown(in, 0);
        assertEquals("a,b,", names(in));
    }

    @Test
    public void replaceAndRemove() {
        List<Preset> in = list("a", "b", "c");
        assertEquals("a,z,c,", names(PresetList.replace(in, 1, p("z"))));
        assertEquals("a,b,c,", names(PresetList.replace(in, 3, p("z"))));
        assertEquals("a,b,c,", names(PresetList.replace(in, -1, p("z"))));
        assertEquals("a,c,", names(PresetList.remove(in, 1)));
        assertEquals("a,b,c,", names(PresetList.remove(in, 3)));
        assertEquals("a,b,c,", names(PresetList.remove(in, -1)));
    }

    @Test
    public void reorder() {
        List<Preset> in = list("a", "b", "c");
        assertEquals("b,a,c,", names(PresetList.moveUp(in, 1)));
        assertEquals("a,b,c,", names(PresetList.moveUp(in, 0)));
        assertEquals("a,c,b,", names(PresetList.moveDown(in, 1)));
        assertEquals("a,b,c,", names(PresetList.moveDown(in, 2)));
        assertEquals("a,b,c,", names(PresetList.moveDown(in, 5)));
        assertEquals("a,b,c,", names(PresetList.moveUp(in, -1)));
    }

    private static final String NONE = "\t-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1";

    @Test
    public void oldThreeFieldLineLoadsWithEveryNewItemLeftAndSavesInTheNewFormat() {
        List<Preset> loaded = PresetList.decode("Cold\t8\t5\nWarm\t-1\t3\n");
        assertEquals(2, loaded.size());
        Preset cold = loaded.get(0);
        assertEquals(8, cold.driver);
        assertEquals(5, cold.passenger);
        for (int v : new int[] {cold.wheel, cold.eco, cold.climate, cold.tempF, cold.fan, cold.ac,
                cold.airflow, cold.sync}) {
            assertEquals(Preset.LEAVE, v);
        }
        String saved = PresetList.encode(loaded);
        assertEquals("Cold\t8\t5" + NONE + "\nWarm\t-1\t3" + NONE + "\n", saved);
        assertEquals(loaded, PresetList.decode(saved));
    }

    @Test
    public void elevenFieldLineRoundTrips() {
        Preset full = new Preset("Cold", 8, 8, 1, 0, Preset.CLIMATE_MANUAL, 70, 3, 1, 2, 1);
        String line = "Cold\t8\t8\t1\t0\t3\t70\t3\t1\t2\t1\n";
        assertEquals(line, PresetList.encode(Arrays.asList(full)));
        assertEquals(Arrays.asList(full), PresetList.decode(line));
        Preset climateOnly = new Preset("Auto", -1, -1, -1, -1, Preset.CLIMATE_AUTO, 64, -1, -1, -1, 1);
        assertEquals(Arrays.asList(climateOnly), PresetList.decode(PresetList.encode(Arrays.asList(climateOnly))));
    }

    @Test
    public void decodeSkipsLinesWithOtherFieldCounts() {
        assertTrue(PresetList.decode("x\t8\t5\t-1\n").isEmpty());
        assertTrue(PresetList.decode("x\t8\t5" + NONE + "\t-1\n").isEmpty());
        assertTrue(PresetList.decode("x\t8\t5\t-1\t-1\t-1\t-1\t-1\t-1\t-1\n").isEmpty());
    }

    @Test
    public void decodeSkipsElevenFieldLinesThatDoNotCheck() {
        assertTrue(PresetList.decode("x\t8\t5\t2\t-1\t-1\t-1\t-1\t-1\t-1\t-1\n").isEmpty());
        assertTrue(PresetList.decode("x\t8\t5\t-1\t-1\t-1\t83\t-1\t-1\t-1\t-1\n").isEmpty());
        assertTrue(PresetList.decode("x\t-1\t-1" + NONE + "\n").isEmpty());
        assertTrue(PresetList.decode("x\t8\t5\t-1\t-1\t3\t70\t-1\t-1\t-1\t-1\n").isEmpty());
        assertTrue(PresetList.decode("x\t8\t5\t-1\t-1\t-1\tabc\t-1\t-1\t-1\t-1\n").isEmpty());
    }

    @Test
    public void autoAndOffLinesDropTheirGreyedItemsOnLoad() {
        Preset auto = PresetList.decode("a\t-1\t-1\t-1\t-1\t2\t70\t5\t1\t3\t1\n").get(0);
        assertEquals(new Preset("a", -1, -1, -1, -1, Preset.CLIMATE_AUTO, 70, -1, -1, -1, 1), auto);
        Preset off = PresetList.decode("o\t-1\t-1\t1\t-1\t1\t70\t5\t1\t3\t1\n").get(0);
        assertEquals(new Preset("o", -1, -1, 1, -1, Preset.CLIMATE_OFF, -1, -1, -1, -1, -1), off);
    }
}

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
        assertEquals("Cold\t8\t8\nWarm\t5\t-1\nOff\t2\t2\n", PresetList.encode(in));
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
        assertEquals("ok\t8\t8\nc\t2\t2\n", PresetList.encode(in));
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
}

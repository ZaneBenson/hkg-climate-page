package hkgclimate.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The saved presets as text and the edits on a list. A line has 11 tab-separated fields: name,
 * driver, passenger, wheel, eco, climate, temperature (F), fan, A/C, airflow, sync. Lines of the
 * first format (name, driver, passenger) are still read, with every other item left as is.
 * Nothing is escaped: a name with a tab or line break is not a valid preset.
 * Every edit returns a new list and leaves its input alone.
 */
public final class PresetList {
    public static final int MAX = 4;
    private static final int FIELDS = 11;

    private PresetList() { }

    /** One line per valid preset (invalid ones are left out), at most MAX, each ending in a line feed. */
    public static String encode(List<Preset> presets) {
        StringBuilder b = new StringBuilder();
        if (presets == null) return "";
        int n = 0;
        for (Preset p : presets) {
            if (p == null || n >= MAX) continue;
            if (Preset.check(p) != null) continue;
            int[] v = {p.driver, p.passenger, p.wheel, p.eco, p.climate, p.tempF, p.fan, p.ac,
                    p.airflow, p.sync};
            b.append(p.name);
            for (int x : v) b.append('\t').append(x);
            b.append('\n');
            n++;
        }
        return b.toString();
    }

    /** The valid lines, in order, at most MAX; lines that don't parse or don't check are skipped. */
    public static List<Preset> decode(String text) {
        List<Preset> out = new ArrayList<Preset>();
        if (text == null) return out;
        for (String raw : text.split("\n", -1)) {
            if (out.size() >= MAX) break;
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (line.length() == 0) continue;
            String[] f = line.split("\t", -1);
            if (f.length != 3 && f.length != FIELDS) continue;
            // the Preset trims its name, which would hide a stray carriage return at either end
            if (f[0].indexOf('\r') >= 0) continue;
            int[] v = new int[FIELDS - 1];
            Arrays.fill(v, Preset.LEAVE);
            try {
                for (int k = 1; k < f.length; k++) v[k - 1] = Integer.parseInt(f[k]);
            } catch (NumberFormatException e) {
                continue;
            }
            Preset p = new Preset(f[0], v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9]);
            if (Preset.check(p) != null) continue;
            out.add(p);
        }
        return out;
    }

    /** The list with the preset at the end; unchanged when it is full or the preset is null. */
    public static List<Preset> add(List<Preset> list, Preset p) {
        List<Preset> out = copy(list);
        if (p != null && out.size() < MAX) out.add(p);
        return out;
    }

    /** The list with the preset at index replaced; unchanged when the index is out of range. */
    public static List<Preset> replace(List<Preset> list, int index, Preset p) {
        List<Preset> out = copy(list);
        if (p != null && index >= 0 && index < out.size()) out.set(index, p);
        return out;
    }

    /** The list without the preset at index; unchanged when the index is out of range. */
    public static List<Preset> remove(List<Preset> list, int index) {
        List<Preset> out = copy(list);
        if (index >= 0 && index < out.size()) out.remove(index);
        return out;
    }

    /** The preset at index swapped with the one before it; unchanged for the first or a bad index. */
    public static List<Preset> moveUp(List<Preset> list, int index) {
        List<Preset> out = copy(list);
        if (index >= 1 && index < out.size()) swap(out, index, index - 1);
        return out;
    }

    /** The preset at index swapped with the one after it; unchanged for the last or a bad index. */
    public static List<Preset> moveDown(List<Preset> list, int index) {
        List<Preset> out = copy(list);
        if (index >= 0 && index < out.size() - 1) swap(out, index, index + 1);
        return out;
    }

    private static void swap(List<Preset> l, int a, int b) {
        Preset t = l.get(a);
        l.set(a, l.get(b));
        l.set(b, t);
    }

    private static List<Preset> copy(List<Preset> list) {
        List<Preset> out = new ArrayList<Preset>();
        if (list == null) return out;
        for (Preset p : list) {
            if (p != null && out.size() < MAX) out.add(p);
        }
        return out;
    }
}

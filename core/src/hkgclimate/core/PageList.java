package hkgclimate.core;

import java.util.ArrayList;
import java.util.List;

/** Edits the comma separated page lists of the split screen, adding or removing our page id (4). */
public final class PageList {
    /** Our page's id (CLIMATE), as a list token. */
    public static final String TOKEN = "4";

    private PageList() { }

    /**
     * The list with our page in it. null (key missing) stays null; a list with no entries becomes
     * just our page; a list that already has our page is returned as it is; otherwise the page
     * goes in after the first entry. Entries are compared trimmed and empty ones are ignored.
     */
    public static String withPage(String csv) {
        if (csv == null) return null;
        List<String> tokens = tokens(csv);
        if (tokens.isEmpty()) return TOKEN;
        if (tokens.contains(TOKEN)) return csv;
        tokens.add(1, TOKEN);
        return join(tokens);
    }

    /** The list without any entry equal to our page's id; null stays null, a list without it as is. */
    public static String withoutPage(String csv) {
        if (csv == null) return null;
        List<String> tokens = tokens(csv);
        if (!tokens.contains(TOKEN)) return csv;
        while (tokens.remove(TOKEN)) { /* every one */ }
        return join(tokens);
    }

    /** The trimmed, non-empty entries, in order. */
    private static List<String> tokens(String csv) {
        List<String> out = new ArrayList<String>();
        for (String t : csv.split(",", -1)) {
            String trimmed = t.trim();
            if (trimmed.length() > 0) out.add(trimmed);
        }
        return out;
    }

    private static String join(List<String> tokens) {
        StringBuilder sb = new StringBuilder();
        for (String t : tokens) {
            if (sb.length() > 0) sb.append(',');
            sb.append(t);
        }
        return sb.toString();
    }
}

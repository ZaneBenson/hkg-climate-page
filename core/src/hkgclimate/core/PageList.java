package hkgclimate.core;

/** Edits the comma separated page lists of the split screen, adding or removing our page id (4). */
public final class PageList {
    /** Our page's id (CLIMATE), as a list token. */
    public static final String TOKEN = "4";

    private PageList() { }

    /**
     * The list with our page in it. null (key missing) stays null; an empty list becomes just our
     * page; a list that already has our page is returned as it is; otherwise the page goes in
     * after the first entry.
     */
    public static String withPage(String csv) {
        if (csv == null) return null;
        if (csv.length() == 0) return TOKEN;
        if (has(csv)) return csv;
        int comma = csv.indexOf(',');
        if (comma < 0) return csv + "," + TOKEN;
        return csv.substring(0, comma) + "," + TOKEN + csv.substring(comma);
    }

    /** The list without any entry equal to our page's id; null stays null. */
    public static String withoutPage(String csv) {
        if (csv == null) return null;
        if (!has(csv)) return csv;
        StringBuilder sb = new StringBuilder();
        for (String t : csv.split(",", -1)) {
            if (TOKEN.equals(t.trim())) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(t);
        }
        return sb.toString();
    }

    private static boolean has(String csv) {
        for (String t : csv.split(",", -1)) {
            if (TOKEN.equals(t.trim())) return true;
        }
        return false;
    }
}

package hkgclimate.core;

import java.util.List;

/** Picks the one "last send" line to show for a tap that may have sent two commands. */
public final class SendSummary {
    private SendSummary() { }

    /**
     * The worst line: a refusal or failure beats a send, and among equals the later line wins. Null
     * lines are skipped; null when there is none.
     */
    public static String worst(List<String> lines) {
        String best = null;
        boolean bestBad = false;
        if (lines == null) return null;
        for (String l : lines) {
            if (l == null) continue;
            boolean bad = l.startsWith("send refused") || l.startsWith("send failed");
            if (best == null || bad || !bestBad) {
                best = l;
                bestBad = bad;
            }
        }
        return best;
    }
}

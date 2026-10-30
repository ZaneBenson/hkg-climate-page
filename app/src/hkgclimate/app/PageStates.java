package hkgclimate.app;

import android.os.Bundle;
import hkgclimate.core.PageState;
import hkgclimate.link.PageLink;

/** Turns a PageState into the Bundle sent to the page. Also names the debug Bundle's keys. */
final class PageStates {
    private PageStates() { }

    // raw values, read by the settings screen only (PageLink.K_DEBUG); -1 until read
    static final String D_DRV_STATE = "drvState";
    static final String D_AST_STATE = "astState";
    static final String D_DRV_OPTION = "drvOption";
    static final String D_AST_OPTION = "astOption";
    /** float */
    static final String D_TEMP_C = "tempC";
    static final String D_STATE_C = "stateC";
    /** float */
    static final String D_TEMP_F = "tempF";
    static final String D_STATE_F = "stateF";
    /** The raw getTemperatureUnit() value. */
    static final String D_UNIT = "unit";
    /** boolean: the seat service is connected and its listener registered. */
    static final String D_SEATS_READY = "seatsReady";
    /** boolean: the DATC temperature listener is registered. */
    static final String D_TEMP_READY = "tempReady";
    /** String: the last page list write failure; null when there is none. */
    static final String D_PAGE_LIST_ERROR = "pageListError";
    /** String: the seat sender's last result, or the latest "no change" text, whichever came last; null before any. */
    static final String D_LAST_SEND = "lastSend";

    /** The page's Bundle (PageLink keys); debug, when not null, goes under K_DEBUG. */
    static Bundle toBundle(PageState s, Bundle debug) {
        Bundle b = new Bundle();
        b.putString(PageLink.K_TOP, s.top);
        b.putStringArray(PageLink.K_SEAT_TEXT, s.seatText.clone());
        b.putIntArray(PageLink.K_SEAT_LEVEL, s.seatLevel.clone());
        b.putIntArray(PageLink.K_SEAT_KIND, s.seatKind.clone());
        b.putBooleanArray(PageLink.K_SEAT_ENABLED, s.seatEnabled.clone());
        b.putIntArray(PageLink.K_SEAT_TAP_KIND, s.seatTapKind.clone());
        if (debug != null) b.putBundle(PageLink.K_DEBUG, debug);
        return b;
    }
}

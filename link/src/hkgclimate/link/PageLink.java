package hkgclimate.link;

import android.os.IBinder;

/**
 * The binder protocol between the climate page (in the stock panel app) and our app's
 * ClimateService, plus the names both sides need. Shared source: it uses only android.os and
 * java APIs so it can be compiled into the page as well as the app.
 *
 * Page to service (descriptor {@link #DESCRIPTOR}):
 *   TX_SUBSCRIBE   in: IBinder callback
 *   TX_UNSUBSCRIBE in: IBinder callback
 *   TX_TAP         in: int seat (0 driver, 1 passenger), long requestId; sent FLAG_ONEWAY
 * Service to page (descriptor {@link #CALLBACK_DESCRIPTOR}):
 *   CB_STATE       in: Bundle with the K_ keys below; FLAG_ONEWAY
 */
public final class PageLink {
    private PageLink() { }

    public static final String DESCRIPTOR = "hkgclimate.link.PageLink";
    public static final String CALLBACK_DESCRIPTOR = "hkgclimate.link.PageCallback";

    public static final int TX_SUBSCRIBE = IBinder.FIRST_CALL_TRANSACTION;
    public static final int TX_UNSUBSCRIBE = IBinder.FIRST_CALL_TRANSACTION + 1;
    public static final int TX_TAP = IBinder.FIRST_CALL_TRANSACTION + 2;

    public static final int CB_STATE = IBinder.FIRST_CALL_TRANSACTION;

    public static final String PACKAGE = "hkgclimate.app";
    public static final String SERVICE = "hkgclimate.app.ClimateService";
    public static final String SETTINGS = "hkgclimate.app.SettingsActivity";

    /** String: the top line of the page. */
    public static final String K_TOP = "top";
    /** String[], index 0 driver, 1 passenger: the text shown for each seat. */
    public static final String K_SEAT_TEXT = "seatText";
    /** int[], index 0 driver: the seat's level. */
    public static final String K_SEAT_LEVEL = "seatLevel";
    /** int[], index 0 driver: the seat's kind (heat or cool). */
    public static final String K_SEAT_KIND = "seatKind";
    /** boolean[], index 0 driver: whether tapping the seat is allowed. */
    public static final String K_SEAT_ENABLED = "seatEnabled";
    /** boolean: this is a test build (taps are logged, not sent). */
    public static final String K_TEST_MODE = "testMode";
    /** Bundle of raw values; read by the settings screen only. */
    public static final String K_DEBUG = "debug";
}

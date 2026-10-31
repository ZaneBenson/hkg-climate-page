package hkgclimate.app;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * The layout both settings screens share. The head unit's screen is 1280 dp wide and the right-hand
 * split panel covers its right 427 dp, so everything sits in one column of at most 800 dp (padding
 * included), against the left edge, with 24 dp of padding. Nothing is ever laid out under the panel,
 * whether or not it is shown.
 */
final class Column {
    /** The widest the column gets, padding included. */
    static final int MAX_DP = 800;
    /** The padding inside the column. */
    static final int PAD_DP = 24;

    private Column() {}

    /** The column's width in pixels: 800 dp, or the whole screen when the screen is narrower. */
    static int widthPx(Activity a) {
        android.util.DisplayMetrics m = a.getResources().getDisplayMetrics();
        return Math.min(Math.round(MAX_DP * m.density), m.widthPixels);
    }

    /** Pads the content and puts it, left-aligned and no wider than the column, in a scroll view. */
    static View wrap(Activity a, LinearLayout content) {
        int pad = Math.round(PAD_DP * a.getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(a);
        scroll.addView(content, new FrameLayout.LayoutParams(
                widthPx(a), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        return scroll;
    }
}

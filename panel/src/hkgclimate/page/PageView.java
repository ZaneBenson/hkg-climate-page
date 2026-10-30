package hkgclimate.page;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import hkgclimate.link.PageLink;

/**
 * The climate page's view, built in code (the panel app has no resources of ours): a top line
 * with a Settings button, two seat buttons with three level bars each, four greyed preset slots
 * and a yellow strip while this is a test build. The slot is 640 x 720 px at density 240; sizes
 * are given in dp and sp and converted with the context's display metrics. All methods are for
 * the main thread.
 */
final class PageView extends FrameLayout {
    private static final String TAG = ClimatePage.TAG;

    interface Listener {
        void onSeatTap(int seat);
        void onSettings();
    }

    private static final int SEATS = 2;
    private static final int BARS = 3;

    private static final int COLOR_HEAT = Color.parseColor("#FF8A00");
    private static final int COLOR_COOL = Color.parseColor("#2D9CDB");
    private static final int COLOR_OFF = Color.parseColor("#62797F");
    private static final int COLOR_STRIP = Color.parseColor("#F2C94C");
    private static final int TEXT_DAY = Color.parseColor("#62797F");
    private static final int TEXT_NIGHT = Color.parseColor("#B6C1C8");

    private static final String WAITING = "Waiting for the climate app";
    private static final String SEPARATOR = " · ";

    private final float density;
    private final float scaledDensity;

    private final TextView top;
    private final TextView settings;
    private final LinearLayout[] seatButtons = new LinearLayout[SEATS];
    private final TextView[] seatNames = new TextView[SEATS];
    private final TextView[] seatStates = new TextView[SEATS];
    private final View[][] bars = new View[SEATS][BARS];
    private final TextView[] presets = new TextView[4];
    private final TextView strip;

    private final String[] seatTextNow = new String[SEATS];
    private final int[] levelNow = new int[SEATS];
    private final int[] kindNow = new int[SEATS];
    private final boolean[] enabledNow = new boolean[SEATS];

    private boolean night;
    private Listener listener;

    PageView(Context context, boolean night) {
        super(context);
        DisplayMetrics m = context.getResources().getDisplayMetrics();
        density = m.density;
        scaledDensity = m.scaledDensity;
        this.night = night;

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams columnParams = new FrameLayout.LayoutParams(dp(424),
                LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        columnParams.topMargin = dp(8);
        addView(column, columnParams);

        // top line, Settings at its right
        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        top = new TextView(context);
        textSize(top, 22);
        top.setMaxLines(2);
        top.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.setText(WAITING);
        topRow.addView(top, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        settings = new TextView(context);
        textSize(settings, 16);
        settings.setText("Settings");
        settings.setGravity(Gravity.CENTER);
        settings.setPadding(dp(10), dp(6), dp(10), dp(6));
        settings.setClickable(true);
        settings.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Listener l = listener;
                    if (l != null) l.onSettings();
                } catch (Throwable t) {
                    Log.e(TAG, "settings button failed", t);
                }
            }
        });
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        settingsParams.leftMargin = dp(8);
        topRow.addView(settings, settingsParams);
        LinearLayout.LayoutParams topRowParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        topRowParams.bottomMargin = dp(12);
        column.addView(topRow, topRowParams);

        // the two seat buttons
        LinearLayout seatRow = new LinearLayout(context);
        seatRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < SEATS; i++) {
            LinearLayout b = new LinearLayout(context);
            b.setOrientation(LinearLayout.VERTICAL);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(6), dp(6), dp(6), dp(6));
            seatNames[i] = new TextView(context);
            textSize(seatNames[i], 20);
            seatNames[i].setGravity(Gravity.CENTER);
            seatNames[i].setSingleLine(true);
            b.addView(seatNames[i], new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            LinearLayout barRow = new LinearLayout(context);
            barRow.setOrientation(LinearLayout.HORIZONTAL);
            barRow.setGravity(Gravity.CENTER);
            for (int j = 0; j < BARS; j++) {
                View bar = new View(context);
                bars[i][j] = bar;
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(40), dp(10));
                if (j > 0) bp.leftMargin = dp(4);
                barRow.addView(bar, bp);
            }
            LinearLayout.LayoutParams barRowParams = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            barRowParams.topMargin = dp(14);
            barRowParams.bottomMargin = dp(14);
            b.addView(barRow, barRowParams);

            seatStates[i] = new TextView(context);
            textSize(seatStates[i], 18);
            seatStates[i].setGravity(Gravity.CENTER);
            seatStates[i].setSingleLine(true);
            b.addView(seatStates[i], new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            final int seat = i;
            b.setClickable(true);
            b.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        // a disabled seat ignores taps
                        if (!enabledNow[seat]) return;
                        Listener l = listener;
                        if (l != null) l.onSeatTap(seat);
                    } catch (Throwable t) {
                        Log.e(TAG, "seat tap failed", t);
                    }
                }
            });
            seatButtons[i] = b;
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(200), dp(200));
            if (i > 0) sp.leftMargin = dp(24);
            seatRow.addView(b, sp);
        }
        LinearLayout.LayoutParams seatRowParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        seatRowParams.bottomMargin = dp(12);
        column.addView(seatRow, seatRowParams);

        // four greyed preset slots, 2 x 2
        for (int row = 0; row < 2; row++) {
            LinearLayout r = new LinearLayout(context);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 2; col++) {
                TextView p = new TextView(context);
                textSize(p, 18);
                p.setText("Preset");
                p.setGravity(Gravity.CENTER);
                p.setAlpha(0.35f);
                p.setClickable(false);
                presets[row * 2 + col] = p;
                LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(200), dp(56));
                if (col > 0) pp.leftMargin = dp(24);
                r.addView(p, pp);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = dp(8);
            column.addView(r, rp);
        }

        // the test mode strip
        strip = new TextView(context);
        textSize(strip, 14);
        strip.setText("Test mode: taps are logged, not sent");
        strip.setTextColor(Color.BLACK);
        strip.setBackgroundColor(COLOR_STRIP);
        strip.setGravity(Gravity.CENTER);
        strip.setPadding(dp(8), dp(6), dp(8), dp(6));
        strip.setVisibility(View.GONE);
        LinearLayout.LayoutParams stripParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        stripParams.topMargin = dp(4);
        column.addView(strip, stripParams);

        showNotice(WAITING);
        applyTheme();
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setNight(boolean night) {
        this.night = night;
        applyTheme();
        refreshSeats();
    }

    /** Show a message in the top line; seats are blank and disabled until the next state. */
    void showNotice(String text) {
        top.setText(text);
        for (int i = 0; i < SEATS; i++) {
            seatTextNow[i] = null;
            levelNow[i] = 0;
            kindNow[i] = 0;
            enabledNow[i] = false;
        }
        refreshSeats();
    }

    /** Draw a state Bundle from the app (PageLink keys). Missing parts show as off and disabled. */
    void render(Bundle state) {
        if (state == null) return;
        String t = state.getString(PageLink.K_TOP);
        top.setText(t == null ? "" : t);
        String[] text = state.getStringArray(PageLink.K_SEAT_TEXT);
        int[] level = state.getIntArray(PageLink.K_SEAT_LEVEL);
        int[] kind = state.getIntArray(PageLink.K_SEAT_KIND);
        boolean[] enabled = state.getBooleanArray(PageLink.K_SEAT_ENABLED);
        for (int i = 0; i < SEATS; i++) {
            seatTextNow[i] = text != null && i < text.length ? text[i] : null;
            levelNow[i] = level != null && i < level.length ? level[i] : 0;
            kindNow[i] = kind != null && i < kind.length ? kind[i] : 0;
            enabledNow[i] = enabled != null && i < enabled.length && enabled[i];
        }
        strip.setVisibility(state.getBoolean(PageLink.K_TEST_MODE, false) ? View.VISIBLE : View.GONE);
        refreshSeats();
    }

    private void refreshSeats() {
        for (int i = 0; i < SEATS; i++) {
            // "Driver · heat mid": the seat name on the first line, the state under the bars
            String full = seatTextNow[i];
            String name = i == 0 ? "Driver" : "Passenger";
            String state = "";
            if (full != null) {
                int cut = full.indexOf(SEPARATOR);
                if (cut >= 0) {
                    name = full.substring(0, cut);
                    state = full.substring(cut + SEPARATOR.length());
                } else {
                    name = full;
                }
            }
            seatNames[i].setText(name);
            seatStates[i].setText(state);

            int lit = kindNow[i] == 1 ? COLOR_HEAT : kindNow[i] == 2 ? COLOR_COOL : COLOR_OFF;
            for (int j = 0; j < BARS; j++) {
                bars[i][j].setBackgroundColor(j < levelNow[i] ? lit : COLOR_OFF);
            }
            seatButtons[i].setEnabled(enabledNow[i]);
            seatButtons[i].setAlpha(enabledNow[i] ? 1f : 0.5f);
        }
    }

    private void applyTheme() {
        int c = night ? TEXT_NIGHT : TEXT_DAY;
        top.setTextColor(c);
        settings.setTextColor(c);
        settings.setBackground(outline(c, 6));
        for (int i = 0; i < SEATS; i++) {
            seatNames[i].setTextColor(c);
            seatStates[i].setTextColor(c);
            seatButtons[i].setBackground(outline(c, 12));
        }
        for (TextView p : presets) {
            p.setTextColor(c);
            p.setBackground(outline(c, 8));
        }
    }

    /** A rounded outline in the text colour, with a faint fill while pressed. */
    private android.graphics.drawable.Drawable outline(int color, int radiusDp) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.TRANSPARENT);
        normal.setCornerRadius(dp(radiusDp));
        normal.setStroke(dp(2), color);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor((color & 0x00FFFFFF) | 0x40000000);
        pressed.setCornerRadius(dp(radiusDp));
        pressed.setStroke(dp(2), color);
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[] {android.R.attr.state_pressed}, pressed);
        d.addState(new int[0], normal);
        return d;
    }

    private int dp(float v) {
        return (int) (v * density + 0.5f);
    }

    private void textSize(TextView t, float sp) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp * scaledDensity);
    }
}

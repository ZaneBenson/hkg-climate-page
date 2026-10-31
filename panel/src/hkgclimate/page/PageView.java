package hkgclimate.page;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import hkgclimate.link.PageLink;

/**
 * The climate page's view, built in code (the panel app has no resources of ours), after the
 * owner's design (docs/superpowers/specs/assets/2026-10-31-panel-v1-2.html):
 * <ul>
 * <li>a top line, as wide as the buttons below, with the outside temperature and a gear button
 *     that opens the settings;</li>
 * <li>two seat buttons. The border and the seat icon show the seat's mode (orange and rising
 *     arrows: heat; blue and a fan: cool; grey and the plain seat: unavailable), the three bars
 *     its level. A round sub-button near the top inner corner shows swap (the seat is off: flip
 *     the next tap's mode) or × (the seat is on: turn it off), or nothing;</li>
 * <li>up to six preset buttons, two per row, only for saved presets; the applied preset is
 *     highlighted.</li>
 * </ul>
 * The slot is 640 x 720 px at density 240; sizes are given in dp and sp and converted with the
 * context's display metrics. A state from an older app (without the 1.2 keys) shows no highlight
 * and no sub-buttons. All methods are for the main thread.
 */
final class PageView extends FrameLayout {
    private static final String TAG = ClimatePage.TAG;

    interface Listener {
        void onSeatTap(int seat);
        void onSeatSwap(int seat);
        void onSeatOff(int seat);
        void onPresetTap(int index);
        void onSettings();
    }

    private static final int SEATS = 2;
    private static final int BARS = 3;
    private static final int PRESETS = 6;
    private static final int PRESET_COLUMNS = 2;
    private static final int PRESET_ROWS = PRESETS / PRESET_COLUMNS;

    // what a seat's sub-button shows
    private static final int SUB_NONE = 0;
    private static final int SUB_SWAP = 1;
    private static final int SUB_OFF = 2;

    private static final int COLOR_HEAT = Color.parseColor("#FF8A00");
    private static final int COLOR_COOL = Color.parseColor("#2D9CDB");
    private static final int COLOR_OFF = Color.parseColor("#62797F");
    private static final int TEXT_DAY = Color.parseColor("#62797F");
    private static final int TEXT_NIGHT = Color.parseColor("#B6C1C8");
    // the highlighted preset's border and name
    private static final int HIGHLIGHT_DAY = Color.parseColor("#1F2A30");
    private static final int HIGHLIGHT_NIGHT = Color.parseColor("#FFFFFF");
    // the highlighted preset's fill: the text colour at this alpha (about 18 %)
    private static final int HIGHLIGHT_FILL_ALPHA = 0x2E;

    private static final String WAITING = "Waiting for the climate app";

    private final float density;
    private final float scaledDensity;

    private final TextView top;
    private final ImageView gear;
    private final Icon gearIcon;
    private final FrameLayout[] seatBoxes = new FrameLayout[SEATS];
    private final LinearLayout[] seatButtons = new LinearLayout[SEATS];
    private final Icon[] seatIcons = new Icon[SEATS];
    private final ImageView[] subButtons = new ImageView[SEATS];
    private final Icon[] subIcons = new Icon[SEATS];
    private final View[][] bars = new View[SEATS][BARS];
    private final LinearLayout[] presetRows = new LinearLayout[PRESET_ROWS];
    private final TextView[] presets = new TextView[PRESETS];

    private final int[] levelNow = new int[SEATS];
    private final int[] kindNow = new int[SEATS];
    private final int[] tapKindNow = new int[SEATS];
    private final boolean[] enabledNow = new boolean[SEATS];
    private final int[] subNow = new int[SEATS];
    private final String[] presetNameNow = new String[PRESETS];
    private final boolean[] presetEnabledNow = new boolean[PRESETS];
    private int presetActiveNow = -1;
    // the preset slot drawn at each button position (saved presets only, in slot order); -1: none
    private final int[] slotAt = new int[PRESETS];

    private boolean night;
    private Listener listener;

    PageView(Context context, boolean night) {
        super(context);
        DisplayMetrics m = context.getResources().getDisplayMetrics();
        density = m.density;
        scaledDensity = m.scaledDensity;
        this.night = night;
        int c = textColor();

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams columnParams = new FrameLayout.LayoutParams(dp(424),
                LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        columnParams.topMargin = dp(8);
        addView(column, columnParams);

        // top line, 384 dp (the buttons' width), the gear at its right
        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        top = new TextView(context);
        textSize(top, 22);
        top.setMaxLines(2);
        top.setEllipsize(android.text.TextUtils.TruncateAt.END);
        topRow.addView(top, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        gearIcon = new Icon(Icon.GEAR, dp(26), dp(26), false, c);
        gear = new ImageView(context);
        gear.setScaleType(ImageView.ScaleType.CENTER);
        gear.setImageDrawable(gearIcon);
        gear.setContentDescription("Settings");
        gear.setOnClickListener(new OnClickListener() {
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
        LinearLayout.LayoutParams gearParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        gearParams.leftMargin = dp(8);
        topRow.addView(gear, gearParams);
        LinearLayout.LayoutParams topRowParams = new LinearLayout.LayoutParams(
                dp(384), LayoutParams.WRAP_CONTENT);
        topRowParams.bottomMargin = dp(12);
        column.addView(topRow, topRowParams);

        // the two seat buttons
        LinearLayout seatRow = new LinearLayout(context);
        seatRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < SEATS; i++) {
            seatRow.addView(buildSeat(context, i, c), seatParams(i));
        }
        LinearLayout.LayoutParams seatRowParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        seatRowParams.bottomMargin = dp(12);
        column.addView(seatRow, seatRowParams);

        // up to six preset buttons, two per row; a position is shown only for a saved preset
        for (int row = 0; row < PRESET_ROWS; row++) {
            LinearLayout r = new LinearLayout(context);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < PRESET_COLUMNS; col++) {
                final int position = row * PRESET_COLUMNS + col;
                TextView p = new TextView(context);
                textSize(p, 18);
                p.setGravity(Gravity.CENTER);
                p.setSingleLine(true);
                p.setEllipsize(android.text.TextUtils.TruncateAt.END);
                p.setPadding(dp(8), 0, dp(8), 0);
                p.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            // the state may have changed since the button was drawn
                            int slot = slotAt[position];
                            if (slot < 0 || !presetLive(slot)) return;
                            Listener l = listener;
                            if (l != null) l.onPresetTap(slot);
                        } catch (Throwable t) {
                            Log.e(TAG, "preset tap failed", t);
                        }
                    }
                });
                p.setClickable(false);
                p.setVisibility(GONE);
                presets[position] = p;
                LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(180), dp(50));
                if (col > 0) pp.leftMargin = dp(24);
                r.addView(p, pp);
            }
            r.setVisibility(GONE);
            presetRows[row] = r;
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = dp(8);
            column.addView(r, rp);
        }

        showNotice(WAITING);
        applyTheme();
    }

    /**
     * One seat: a 180 dp square holding the button (icon row over the bars, centred) and, on top
     * of it, the round sub-button, which takes its own taps.
     */
    private FrameLayout buildSeat(Context context, final int seat, int c) {
        boolean passenger = seat == 1;
        FrameLayout box = new FrameLayout(context);

        LinearLayout b = new LinearLayout(context);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(6), dp(6), dp(6), dp(6));

        // the icon row: as wide as the bars, the seat icon at the outer end (the passenger's
        // mirrored, at the right)
        FrameLayout iconRow = new FrameLayout(context);
        seatIcons[seat] = new Icon(Icon.SEAT, dp(52), dp(56), passenger, c);
        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setImageDrawable(seatIcons[seat]);
        iconRow.addView(icon, new FrameLayout.LayoutParams(dp(52), dp(56),
                (passenger ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));
        b.addView(iconRow, new LinearLayout.LayoutParams(dp(116), dp(56)));

        // three 36 x 9 dp bars, 4 dp apart: 116 dp
        LinearLayout barRow = new LinearLayout(context);
        barRow.setOrientation(LinearLayout.HORIZONTAL);
        barRow.setGravity(Gravity.CENTER);
        for (int j = 0; j < BARS; j++) {
            View bar = new View(context);
            bars[seat][j] = bar;
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(36), dp(9));
            if (j > 0) bp.leftMargin = dp(4);
            barRow.addView(bar, bp);
        }
        LinearLayout.LayoutParams barRowParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        barRowParams.topMargin = dp(22);
        b.addView(barRow, barRowParams);

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
        seatButtons[seat] = b;
        box.addView(b, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // the sub-button, 14 dp from the top and inner edges inside the 2 dp border (as the
        // mockup places it); added last so it lies over the button and gets its taps first
        subIcons[seat] = new Icon(Icon.SWAP, dp(30), dp(30), false, c);
        ImageView sub = new ImageView(context);
        sub.setScaleType(ImageView.ScaleType.CENTER);
        sub.setImageDrawable(subIcons[seat]);
        sub.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    // the state may have changed since the sub-button was drawn
                    int what = subNow[seat];
                    Listener l = listener;
                    if (l == null) return;
                    if (what == SUB_SWAP) l.onSeatSwap(seat);
                    else if (what == SUB_OFF) l.onSeatOff(seat);
                } catch (Throwable t) {
                    Log.e(TAG, "seat sub-button tap failed", t);
                }
            }
        });
        sub.setClickable(false);
        sub.setVisibility(INVISIBLE);
        subButtons[seat] = sub;
        FrameLayout.LayoutParams subParams = new FrameLayout.LayoutParams(dp(52), dp(52),
                Gravity.TOP | (passenger ? Gravity.LEFT : Gravity.RIGHT));
        subParams.topMargin = dp(16);
        if (passenger) subParams.leftMargin = dp(16); else subParams.rightMargin = dp(16);
        box.addView(sub, subParams);

        seatBoxes[seat] = box;
        return box;
    }

    private LinearLayout.LayoutParams seatParams(int seat) {
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(180), dp(180));
        if (seat > 0) sp.leftMargin = dp(24);
        return sp;
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setNight(boolean night) {
        this.night = night;
        applyTheme();
        refreshSeats();
        refreshPresets();
    }

    /** Show a message in the top line; seats are blank and disabled and no presets are shown until the next state. */
    void showNotice(String text) {
        top.setText(text);
        for (int i = 0; i < SEATS; i++) {
            levelNow[i] = 0;
            kindNow[i] = 0;
            tapKindNow[i] = 0;
            enabledNow[i] = false;
            subNow[i] = SUB_NONE;
        }
        for (int i = 0; i < PRESETS; i++) {
            presetNameNow[i] = null;
            presetEnabledNow[i] = false;
        }
        presetActiveNow = -1;
        refreshSeats();
        refreshPresets();
    }

    /**
     * Draw a state Bundle from the app (PageLink keys). Missing parts show as off, disabled or
     * absent: no sub-button without K_SEAT_SWAP / K_SEAT_OFF, no highlight without K_PRESET_ACTIVE.
     */
    void render(Bundle state) {
        if (state == null) return;
        String t = state.getString(PageLink.K_TOP);
        top.setText(t == null ? "" : t);
        int[] level = state.getIntArray(PageLink.K_SEAT_LEVEL);
        int[] kind = state.getIntArray(PageLink.K_SEAT_KIND);
        int[] tapKind = state.getIntArray(PageLink.K_SEAT_TAP_KIND);
        boolean[] enabled = state.getBooleanArray(PageLink.K_SEAT_ENABLED);
        boolean[] swap = state.getBooleanArray(PageLink.K_SEAT_SWAP);
        boolean[] off = state.getBooleanArray(PageLink.K_SEAT_OFF);
        for (int i = 0; i < SEATS; i++) {
            levelNow[i] = level != null && i < level.length ? level[i] : 0;
            kindNow[i] = kind != null && i < kind.length ? kind[i] : 0;
            tapKindNow[i] = tapKind != null && i < tapKind.length ? tapKind[i] : 0;
            enabledNow[i] = enabled != null && i < enabled.length && enabled[i];
            boolean showOff = off != null && i < off.length && off[i];
            boolean showSwap = swap != null && i < swap.length && swap[i];
            subNow[i] = !enabledNow[i] ? SUB_NONE : showOff ? SUB_OFF : showSwap ? SUB_SWAP : SUB_NONE;
        }
        String[] presetNames = state.getStringArray(PageLink.K_PRESET_NAMES);
        boolean[] presetEnabled = state.getBooleanArray(PageLink.K_PRESET_ENABLED);
        for (int i = 0; i < PRESETS; i++) {
            presetNameNow[i] = presetNames != null && i < presetNames.length ? presetNames[i] : null;
            presetEnabledNow[i] = presetEnabled != null && i < presetEnabled.length && presetEnabled[i];
        }
        presetActiveNow = state.getInt(PageLink.K_PRESET_ACTIVE, -1);
        refreshSeats();
        refreshPresets();
    }

    /**
     * Saved presets fill the button positions in slot order, two per row; unused positions and
     * empty rows are hidden. An enabled preset takes taps, the others are greyed. The applied
     * preset gets a 4 dp border, a bold name in the highlight colour and a light fill.
     */
    private void refreshPresets() {
        int c = textColor();
        int hi = night ? HIGHLIGHT_NIGHT : HIGHLIGHT_DAY;
        int fill = (c & 0x00FFFFFF) | (HIGHLIGHT_FILL_ALPHA << 24);
        int shown = 0;
        for (int slot = 0; slot < PRESETS; slot++) {
            if (!saved(slot)) continue;
            int position = shown++;
            slotAt[position] = slot;
            TextView p = presets[position];
            boolean live = presetLive(slot);
            boolean active = slot == presetActiveNow;
            p.setText(presetNameNow[slot]);
            p.setTextColor(active ? hi : c);
            p.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            p.setBackground(active ? outline(hi, 8, 4, fill) : outline(c, 8, 2, Color.TRANSPARENT));
            p.setClickable(live);
            p.setAlpha(live ? 1f : 0.35f);
            p.setVisibility(VISIBLE);
        }
        for (int position = shown; position < PRESETS; position++) {
            slotAt[position] = -1;
            presets[position].setClickable(false);
            presets[position].setVisibility(GONE);
        }
        for (int row = 0; row < PRESET_ROWS; row++) {
            presetRows[row].setVisibility(row * PRESET_COLUMNS < shown ? VISIBLE : GONE);
        }
    }

    /** A slot holds a saved preset when it has a name. */
    private boolean saved(int slot) {
        String name = presetNameNow[slot];
        return name != null && name.length() > 0;
    }

    /** A slot takes taps only with an enabled flag and a name. */
    private boolean presetLive(int slot) {
        return presetEnabledNow[slot] && saved(slot);
    }

    private void refreshSeats() {
        int c = textColor();
        for (int i = 0; i < SEATS; i++) {
            int lit = kindNow[i] == 1 ? COLOR_HEAT : kindNow[i] == 2 ? COLOR_COOL : COLOR_OFF;
            for (int j = 0; j < BARS; j++) {
                bars[i][j].setBackgroundColor(j < levelNow[i] ? lit : COLOR_OFF);
            }
            int mode = tapKindNow[i];
            seatIcons[i].setShape(mode == 1 ? Icon.SEAT_HEAT : mode == 2 ? Icon.SEAT_COOL : Icon.SEAT);
            seatIcons[i].setColor(c);
            seatButtons[i].setBackground(seatOutline(i));
            seatButtons[i].setEnabled(enabledNow[i]);
            seatBoxes[i].setAlpha(enabledNow[i] ? 1f : 0.5f);

            ImageView sub = subButtons[i];
            int what = subNow[i];
            if (what == SUB_NONE) {
                sub.setClickable(false);
                sub.setVisibility(INVISIBLE);
            } else {
                subIcons[i].setShape(what == SUB_OFF ? Icon.OFF : Icon.SWAP);
                subIcons[i].setColor(c);
                sub.setBackground(circle(c));
                sub.setClickable(true);
                sub.setVisibility(VISIBLE);
            }
        }
    }

    private void applyTheme() {
        int c = textColor();
        top.setTextColor(c);
        gearIcon.setColor(c);
        gear.setBackground(outline(c, 8, 2, Color.TRANSPARENT));
    }

    private int textColor() {
        return night ? TEXT_NIGHT : TEXT_DAY;
    }

    /** A seat button's outline: the border shows what the next tap does (grey when unavailable). */
    private Drawable seatOutline(int seat) {
        int k = tapKindNow[seat];
        int border = k == 1 ? COLOR_HEAT : k == 2 ? COLOR_COOL : COLOR_OFF;
        return outline(border, 12, 2, Color.TRANSPARENT);
    }

    /** A rounded outline with the given border width and fill, filled faintly in the border colour while pressed. */
    private Drawable outline(int color, int radiusDp, int strokeDp, int fill) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(fill);
        normal.setCornerRadius(dp(radiusDp));
        normal.setStroke(dp(strokeDp), color);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(pressedFill(color));
        pressed.setCornerRadius(dp(radiusDp));
        pressed.setStroke(dp(strokeDp), color);
        return pressable(normal, pressed);
    }

    /** The sub-button's round 2 dp outline, filled faintly while pressed. */
    private Drawable circle(int color) {
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.OVAL);
        normal.setColor(Color.TRANSPARENT);
        normal.setStroke(dp(2), color);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.OVAL);
        pressed.setColor(pressedFill(color));
        pressed.setStroke(dp(2), color);
        return pressable(normal, pressed);
    }

    private static int pressedFill(int color) {
        return (color & 0x00FFFFFF) | 0x40000000;
    }

    private static Drawable pressable(Drawable normal, Drawable pressed) {
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

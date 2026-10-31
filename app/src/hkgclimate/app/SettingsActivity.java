package hkgclimate.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import hkgclimate.core.Preset;
import hkgclimate.core.PresetList;
import hkgclimate.core.SeatValues;
import hkgclimate.core.SwitchTemp;
import hkgclimate.link.PageLink;
import java.util.List;

/**
 * The settings screen. Views are built in code. It learns the car's values the way the page does:
 * it binds to our ClimateService and subscribes through the PageLink binder with its own
 * callback. The switch temperature is stored in whole degrees F and shown in the car's unit.
 */
public class SettingsActivity extends Activity {
    private static final String TAG = CarLink.TAG;
    /** Shown until the first state says otherwise: degrees F. */
    private static final int DEFAULT_RAW_UNIT = 2;
    private static final String MINUS = "−";
    private static final String DEGREE = "°";
    private static final String DOT = " · ";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private int rawUnit = DEFAULT_RAW_UNIT;
    private Bundle debug;
    private boolean gotState;

    private IBinder service;
    private boolean bound;

    private TextView tempValue;
    private Button minus;
    private Button plus;
    private TextView statusText;
    private TextView seatStatus;
    private LinearLayout presetBox;

    // ours alone: the service calls it with every state; main-thread work is posted
    private final Binder callback = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            try {
                if (code != PageLink.CB_STATE) return false;
                data.enforceInterface(PageLink.CALLBACK_DESCRIPTOR);
                final Bundle state = data.readBundle();
                handler.post(new Runnable() {
                    @Override public void run() { onState(state); }
                });
                return true;
            } catch (Throwable t) {
                Log.w(TAG, "settings callback failed", t);
                return false;
            }
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = binder;
            subscribe();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        setContentView(buildViews());
        refresh();
        refreshPresets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPresets(); // the editor may have saved while we were away
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            bound = bindService(new Intent(this, ClimateService.class), connection, Context.BIND_AUTO_CREATE);
            if (!bound) {
                // the Android docs: unbind even when the bind failed, to release the connection
                try {
                    unbindService(connection);
                } catch (Throwable t) {
                    Log.w(TAG, "settings could not unbind after a failed bind", t);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "settings could not bind to the climate service", t);
        }
        refresh();
    }

    @Override
    protected void onStop() {
        disconnect();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        disconnect();
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        return t;
    }

    private Button stepButton(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(28);
        b.setMinWidth(dp(96));
        b.setMinimumWidth(dp(96));
        b.setOnClickListener(l);
        return b;
    }

    private View buildViews() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(32);
        root.setPadding(pad, pad, pad, pad);

        root.addView(text("Climate Page settings", 30));

        TextView switchLabel = text("Switch temperature: seats heat below it and cool at or above it,"
                + " from the outside temperature", 20);
        switchLabel.setPadding(0, dp(24), 0, dp(8));
        root.addView(switchLabel);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        minus = stepButton(MINUS, new View.OnClickListener() {
            @Override public void onClick(View v) { step(-1); }
        });
        tempValue = text("", 32);
        tempValue.setGravity(Gravity.CENTER);
        tempValue.setMinWidth(dp(160));
        plus = stepButton("+", new View.OnClickListener() {
            @Override public void onClick(View v) { step(1); }
        });
        row.addView(minus);
        row.addView(tempValue);
        row.addView(plus);
        root.addView(row);

        CheckBox enabled = new CheckBox(this);
        enabled.setText("Show the climate page on the split screen");
        enabled.setTextSize(22);
        enabled.setChecked(prefs.isPageEnabled());
        enabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean isChecked) {
                prefs.setPageEnabled(isChecked);
            }
        });
        enabled.setPadding(0, dp(16), 0, dp(16));
        root.addView(enabled);

        TextView presetsTitle = text("Presets", 24);
        presetsTitle.setPadding(0, dp(16), 0, dp(8));
        root.addView(presetsTitle);
        presetBox = new LinearLayout(this);
        presetBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(presetBox);

        statusText = text("", 18);
        statusText.setPadding(0, dp(16), 0, 0);
        root.addView(statusText);

        seatStatus = text("", 18);
        seatStatus.setPadding(0, dp(24), 0, 0);
        root.addView(seatStatus);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    /** One step of the switch temperature in the shown unit. Only a press writes the preference. */
    private void step(int delta) {
        try {
            int shown = SwitchTemp.toDisplay(prefs.getSwitchTempF(), rawUnit);
            prefs.setSwitchTempF(SwitchTemp.fromDisplay(shown + delta, rawUnit));
        } catch (Throwable t) {
            Log.w(TAG, "changing the switch temperature failed", t);
        }
        refresh();
    }

    private void onState(Bundle state) {
        if (service == null) return; // a state posted before we disconnected
        gotState = true;
        debug = state == null ? null : state.getBundle(PageLink.K_DEBUG);
        rawUnit = debug == null ? DEFAULT_RAW_UNIT : debug.getInt(PageStates.D_UNIT, DEFAULT_RAW_UNIT);
        refresh();
    }

    private void refresh() {
        try {
            int stored = prefs.getSwitchTempF();
            int shown = SwitchTemp.toDisplay(stored, rawUnit);
            tempValue.setText(shown + DEGREE + (rawUnit == 1 ? "C" : "F"));
            // a button is off when its step would not change the stored value (at the limit)
            minus.setEnabled(SwitchTemp.fromDisplay(shown - 1, rawUnit) < stored);
            plus.setEnabled(SwitchTemp.fromDisplay(shown + 1, rawUnit) > stored);
            statusText.setText(statusLines());
            String last = debug == null ? null : debug.getString(PageStates.D_LAST_SEND);
            seatStatus.setText("Seat command: " + (last == null ? "nothing sent yet" : last));
        } catch (Throwable t) {
            Log.w(TAG, "settings refresh failed", t);
        }
    }

    /** Rebuilds the preset list from the preferences: the summary line and the buttons for each preset. */
    private void refreshPresets() {
        try {
            presetBox.removeAllViews();
            final List<Preset> list = prefs.getPresets();
            if (list.isEmpty()) presetBox.addView(text("No presets yet", 18));
            for (int i = 0; i < list.size(); i++) {
                final int at = i;
                final Preset p = list.get(i);
                TextView line = text(summary(p), 20);
                line.setPadding(0, dp(12), 0, 0);
                presetBox.addView(line);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.addView(smallButton("Edit", true, new View.OnClickListener() {
                    @Override public void onClick(View v) { edit(at); }
                }));
                row.addView(smallButton("Delete", true, new View.OnClickListener() {
                    @Override public void onClick(View v) { confirmDelete(at, p.name); }
                }));
                row.addView(smallButton("Up", at > 0, new View.OnClickListener() {
                    @Override public void onClick(View v) { move(at, -1); }
                }));
                row.addView(smallButton("Down", at < list.size() - 1, new View.OnClickListener() {
                    @Override public void onClick(View v) { move(at, 1); }
                }));
                presetBox.addView(row);
            }
            Button add = smallButton("Add preset", list.size() < PresetList.MAX, new View.OnClickListener() {
                @Override public void onClick(View v) { edit(-1); }
            });
            presetBox.addView(add);
            if (list.size() >= PresetList.MAX) {
                presetBox.addView(text("At most " + PresetList.MAX + " presets", 16));
            }
        } catch (Throwable t) {
            Log.w(TAG, "settings preset list failed", t);
        }
    }

    private Button smallButton(String label, boolean enabled, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setEnabled(enabled);
        b.setOnClickListener(l);
        return b;
    }

    /** "Cold: Driver heat high · Passenger off"; a seat left as is is not named. */
    static String summary(Preset p) {
        StringBuilder sb = new StringBuilder(p.name).append(':');
        boolean first = true;
        for (int seat = 0; seat < 2; seat++) {
            int v = seat == 0 ? p.driver : p.passenger;
            if (v == Preset.LEAVE) continue;
            String w = SeatValues.words(v);
            sb.append(first ? " " : DOT).append(seat == 0 ? "Driver " : "Passenger ")
                    .append(w == null ? String.valueOf(v) : w);
            first = false;
        }
        return sb.toString();
    }

    /** Opens the editor for the preset at index (-1 for a new one), with the seats' options from the last state. */
    private void edit(int index) {
        try {
            Intent in = new Intent(this, PresetEditorActivity.class);
            in.putExtra(PresetEditorActivity.EXTRA_INDEX, index);
            if (debug != null) {
                in.putExtra(PresetEditorActivity.EXTRA_DRV_OPTION, debug.getInt(PageStates.D_DRV_OPTION, -1));
                in.putExtra(PresetEditorActivity.EXTRA_AST_OPTION, debug.getInt(PageStates.D_AST_OPTION, -1));
            }
            startActivity(in);
        } catch (Throwable t) {
            Log.w(TAG, "settings could not open the preset editor", t);
        }
    }

    private void confirmDelete(final int index, String name) {
        try {
            new AlertDialog.Builder(this)
                    .setMessage("Delete preset " + name + "?")
                    .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int which) { delete(index); }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Throwable t) {
            Log.w(TAG, "settings could not show the delete question", t);
        }
    }

    private void delete(int index) {
        try {
            prefs.setPresets(PresetList.remove(prefs.getPresets(), index));
        } catch (Throwable t) {
            Log.w(TAG, "deleting the preset failed", t);
        }
        refreshPresets();
    }

    private void move(int index, int delta) {
        try {
            List<Preset> list = prefs.getPresets();
            prefs.setPresets(delta < 0 ? PresetList.moveUp(list, index) : PresetList.moveDown(list, index));
        } catch (Throwable t) {
            Log.w(TAG, "moving the preset failed", t);
        }
        refreshPresets();
    }

    private String statusLines() {
        StringBuilder sb = new StringBuilder();
        if (service == null) {
            sb.append("Climate service: not connected\n");
        } else if (!gotState || debug == null) {
            sb.append("Climate service: connected, waiting for the first update\n");
        } else {
            Bundle d = debug;
            sb.append("Climate service: connected\n");
            sb.append("Seat service: ").append(ready(d.getBoolean(PageStates.D_SEATS_READY))).append('\n');
            sb.append("Temperature service: ").append(ready(d.getBoolean(PageStates.D_TEMP_READY))).append('\n');
            sb.append("Driver seat: ").append(seat(d.getInt(PageStates.D_DRV_STATE, -1)))
                    .append(", option ").append(d.getInt(PageStates.D_DRV_OPTION, -1)).append('\n');
            sb.append("Passenger seat: ").append(seat(d.getInt(PageStates.D_AST_STATE, -1)))
                    .append(", option ").append(d.getInt(PageStates.D_AST_OPTION, -1)).append('\n');
            sb.append("Outside temperature (C): ").append(d.getFloat(PageStates.D_TEMP_C))
                    .append(", state ").append(hex(d.getInt(PageStates.D_STATE_C, -1))).append('\n');
            sb.append("Outside temperature (F): ").append(d.getFloat(PageStates.D_TEMP_F))
                    .append(", state ").append(hex(d.getInt(PageStates.D_STATE_F, -1))).append('\n');
            sb.append("Car temperature unit: ").append(d.getInt(PageStates.D_UNIT, -1)).append('\n');
            String err = d.getString(PageStates.D_PAGE_LIST_ERROR);
            sb.append("Page lists: ").append(err == null ? "ok" : err).append('\n');
        }
        return sb.toString();
    }

    private static String ready(boolean b) {
        return b ? "ready" : "not ready";
    }

    private static String hex(int v) {
        return v < 0 ? "unknown" : "0x" + Integer.toHexString(v);
    }

    private static String seat(int state) {
        if (state < 0) return "unknown";
        String w = SeatValues.words(state);
        return state + (w == null ? "" : " (" + w + ")");
    }

    private void subscribe() {
        call(PageLink.TX_SUBSCRIBE);
    }

    /** Unsubscribe and unbind; safe to call twice. */
    private void disconnect() {
        if (service != null) call(PageLink.TX_UNSUBSCRIBE);
        service = null;
        gotState = false;
        debug = null;
        if (bound) {
            bound = false;
            try {
                unbindService(connection);
            } catch (Throwable t) {
                Log.w(TAG, "settings could not unbind", t);
            }
        }
    }

    private void call(int code) {
        IBinder s = service;
        if (s == null) return;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(PageLink.DESCRIPTOR);
            data.writeStrongBinder(callback);
            s.transact(code, data, reply, 0);
            reply.readException();
        } catch (Throwable t) {
            Log.w(TAG, "settings link call " + code + " failed", t);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}

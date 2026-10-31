package hkgclimate.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import hkgclimate.core.Preset;
import hkgclimate.core.PresetChoices;
import hkgclimate.core.PresetList;
import hkgclimate.core.SeatChoices;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds or edits one preset: a name and a dropdown for each item, in this order: driver seat,
 * passenger seat, steering wheel, eco, climate, temperature, fan, A/C, airflow, sync. Started only
 * by our SettingsActivity (not exported). Extras: "index" (the slot being edited, -1 for a new
 * preset), "drvOption" / "astOption" (the seats' raw option values from the settings screen's last
 * state; missing or unknown means heat and cool) and "unit" (the car's temperature unit from that
 * state, 1 Celsius, 2 Fahrenheit; missing means Fahrenheit). The cool choices appear only for a
 * seat whose option is heat and cool, except a cool value the preset already has for that seat.
 * Choosing a climate mode greys out (disables) the items it does not use; they are stored as Leave
 * as is. Save builds the whole preset, checks it with Preset.check, and writes the preferences
 * directly, only after the check passes.
 */
public class PresetEditorActivity extends Activity {
    private static final String TAG = CarLink.TAG;

    static final String EXTRA_INDEX = "index";
    static final String EXTRA_DRV_OPTION = PageStates.D_DRV_OPTION;
    static final String EXTRA_AST_OPTION = PageStates.D_AST_OPTION;
    static final String EXTRA_UNIT = PageStates.D_UNIT;

    /** The car's raw temperature unit for Fahrenheit, used when the extra is missing. */
    private static final int DEFAULT_UNIT = 2;

    /**
     * Fixed view ids of the dropdowns. Android saves a view's state under its id, so every dropdown
     * needs its own, and they must differ from the name field's (SeatChoices.NAME_FIELD_ID, 201)
     * and from SeatChoices' button id bases (1 and 101, each plus up to 7).
     */
    private static final int ID_DRIVER = 301;
    private static final int ID_PASSENGER = 302;
    private static final int ID_WHEEL = 303;
    private static final int ID_ECO = 304;
    private static final int ID_CLIMATE = 305;
    private static final int ID_TEMPERATURE = 306;
    private static final int ID_FAN = 307;
    private static final int ID_AC = 308;
    private static final int ID_AIRFLOW = 309;
    private static final int ID_SYNC = 310;

    /** One dropdown, the value behind each of its positions and the item name the climate mode greys by (null for a seat). */
    private static final class Item {
        final String name;
        final Spinner view;
        final int[] values;

        Item(String name, Spinner view, int[] values) {
            this.name = name;
            this.view = view;
            this.values = values;
        }

        /** The value of the chosen position; Leave as is when none is chosen. */
        int value() {
            int pos = view.getSelectedItemPosition();
            return pos >= 0 && pos < values.length ? values[pos] : Preset.LEAVE;
        }
    }

    /** A list adapter that shows the labels at the editor's text size, closed and open. */
    private static final class SizedAdapter extends ArrayAdapter<String> {
        SizedAdapter(Activity a, String[] labels) {
            super(a, android.R.layout.simple_spinner_item, labels);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }

        @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
            return sized(super.getView(position, convertView, parent));
        }

        @Override public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
            return sized(super.getDropDownView(position, convertView, parent));
        }

        private static View sized(View v) {
            if (v instanceof TextView) ((TextView) v).setTextSize(22);
            return v;
        }
    }

    private Prefs prefs;
    private int index = -1;
    private EditText nameField;
    private Item driver;
    private Item passenger;
    private Item wheel;
    private Item eco;
    private Item climate;
    private Item temperature;
    private Item fan;
    private Item ac;
    private Item airflow;
    private Item sync;
    private final List<Item> items = new ArrayList<Item>();
    private TextView error;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            prefs = new Prefs(this);
            Intent in = getIntent();
            index = in == null ? -1 : in.getIntExtra(EXTRA_INDEX, -1);
            int drvOption = in == null ? -1 : in.getIntExtra(EXTRA_DRV_OPTION, -1);
            int astOption = in == null ? -1 : in.getIntExtra(EXTRA_AST_OPTION, -1);
            int unit = in == null ? DEFAULT_UNIT : in.getIntExtra(EXTRA_UNIT, DEFAULT_UNIT);
            Preset editing = null;
            if (index >= 0) {
                List<Preset> list = prefs.getPresets();
                if (index < list.size()) {
                    editing = list.get(index);
                } else {
                    index = -1; // the slot is gone: this becomes a new preset
                }
            }
            setContentView(buildViews(editing, drvOption, astOption, unit));
            applyGreying();
        } catch (Throwable t) {
            Log.w(TAG, "preset editor failed to start", t);
            finish();
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        try {
            applyGreying(); // the restored climate choice may differ from the one the views were built with
        } catch (Throwable t) {
            Log.w(TAG, "preset editor could not restore its greying", t);
        }
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

    private View buildViews(Preset editing, int drvOption, int astOption, int unit) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(32);
        root.setPadding(pad, pad, pad, pad);

        root.addView(text(editing == null ? "New preset" : "Edit preset", 30));

        TextView nameLabel = text("Name (1 to " + Preset.MAX_NAME + " characters)", 20);
        nameLabel.setPadding(0, dp(24), 0, dp(8));
        root.addView(nameLabel);
        nameField = new EditText(this);
        nameField.setId(SeatChoices.NAME_FIELD_ID);
        nameField.setSingleLine(true);
        nameField.setTextSize(24);
        nameField.setFilters(new InputFilter[] {new InputFilter.LengthFilter(Preset.MAX_NAME)});
        if (editing != null) nameField.setText(editing.name);
        root.addView(nameField);

        int leave = Preset.LEAVE;
        items.clear();
        driver = seatSpinner(root, "Driver seat", ID_DRIVER, drvOption, editing == null ? leave : editing.driver);
        passenger = seatSpinner(root, "Passenger seat", ID_PASSENGER, astOption,
                editing == null ? leave : editing.passenger);
        wheel = choiceSpinner(root, "Steering wheel", ID_WHEEL, PresetChoices.ITEM_WHEEL,
                PresetChoices.wheel(), editing == null ? leave : editing.wheel);
        eco = choiceSpinner(root, "Eco mode", ID_ECO, PresetChoices.ITEM_ECO,
                PresetChoices.eco(), editing == null ? leave : editing.eco);
        climate = choiceSpinner(root, "Climate", ID_CLIMATE, PresetChoices.ITEM_CLIMATE,
                PresetChoices.climate(), editing == null ? leave : editing.climate);
        climate.view.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyGreying();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {
                applyGreying();
            }
        });
        temperature = choiceSpinner(root, "Temperature", ID_TEMPERATURE, PresetChoices.ITEM_TEMPERATURE,
                PresetChoices.temperatureChoices(unit), editing == null ? leave : editing.tempF);
        fan = choiceSpinner(root, "Fan speed", ID_FAN, PresetChoices.ITEM_FAN,
                PresetChoices.fan(), editing == null ? leave : editing.fan);
        ac = choiceSpinner(root, "A/C", ID_AC, PresetChoices.ITEM_AC,
                PresetChoices.ac(), editing == null ? leave : editing.ac);
        airflow = choiceSpinner(root, "Airflow", ID_AIRFLOW, PresetChoices.ITEM_AIRFLOW,
                PresetChoices.airflow(), editing == null ? leave : editing.airflow);
        sync = choiceSpinner(root, "Sync", ID_SYNC, PresetChoices.ITEM_SYNC,
                PresetChoices.sync(), editing == null ? leave : editing.sync);

        error = text("", 20);
        error.setTextColor(Color.RED);
        error.setPadding(0, dp(16), 0, 0);
        root.addView(error);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(16), 0, 0);
        Button save = new Button(this);
        save.setText("Save");
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        buttons.addView(save);
        buttons.addView(cancel);
        root.addView(buttons);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    /**
     * One seat's dropdown (SeatChoices decides which choices are offered). The seat's stored choice is
     * always offered and selected, so a seat that is not touched keeps its value when the preset is saved.
     */
    private Item seatSpinner(LinearLayout root, String title, int id, int option, int current) {
        List<String> labels = new ArrayList<String>();
        List<Integer> values = new ArrayList<Integer>();
        int selected = 0;
        int currentPosition = SeatChoices.positionOf(current);
        for (int i = 0; i < SeatChoices.LABELS.length; i++) {
            if (!SeatChoices.offered(i, option, current)) continue;
            if (i == currentPosition) selected = labels.size();
            labels.add(SeatChoices.label(i, option));
            values.add(SeatChoices.VALUES[i]);
        }
        return itemSpinner(root, title, id, null, labels.toArray(new String[0]), toInts(values), selected);
    }

    /** A dropdown of the choices, selecting the one with the stored value (Leave as is when it is not offered). */
    private Item choiceSpinner(LinearLayout root, String title, int id, String itemName,
            PresetChoices.Choice[] choices, int current) {
        String[] labels = new String[choices.length];
        int[] values = new int[choices.length];
        for (int i = 0; i < choices.length; i++) {
            labels[i] = choices[i].label;
            values[i] = choices[i].value;
        }
        return itemSpinner(root, title, id, itemName, labels, values, PresetChoices.positionOf(choices, current));
    }

    /** The title and the dropdown, with the fixed view id; the item is also listed for the greying. */
    private Item itemSpinner(LinearLayout root, String title, int id, String itemName, String[] labels,
            int[] values, int selected) {
        TextView label = text(title, 22);
        label.setPadding(0, dp(24), 0, dp(8));
        root.addView(label);
        Spinner spinner = new Spinner(this);
        spinner.setId(id);
        spinner.setAdapter(new SizedAdapter(this, labels));
        spinner.setSelection(selected);
        root.addView(spinner);
        Item item = new Item(itemName, spinner, values);
        items.add(item);
        return item;
    }

    private static int[] toInts(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    /**
     * Enables each dropdown the chosen climate mode uses and disables the others (PresetChoices.greyed);
     * a disabled dropdown keeps showing its value, and the Preset constructor stores it as Leave as is.
     */
    private void applyGreying() {
        if (climate == null) return;
        int mode = climate.value();
        for (Item item : items) {
            if (item.name != null) item.view.setEnabled(!PresetChoices.greyed(item.name, mode));
        }
    }

    /** Builds the whole preset and checks it with Preset.check; only a valid preset is stored. The reason shows otherwise. */
    private void save() {
        error.setText("");
        List<Preset> next;
        try {
            Preset p = new Preset(nameField.getText().toString(), driver.value(), passenger.value(),
                    wheel.value(), eco.value(), climate.value(), temperature.value(), fan.value(),
                    ac.value(), airflow.value(), sync.value());
            String reason = Preset.check(p);
            if (reason != null) {
                error.setText(reason);
                return;
            }
            List<Preset> list = prefs.getPresets();
            if (index >= 0 && index < list.size()) {
                next = PresetList.replace(list, index, p);
            } else if (list.size() < PresetList.MAX) {
                next = PresetList.add(list, p);
            } else {
                error.setText("at most " + PresetList.MAX + " presets");
                return;
            }
        } catch (Throwable t) {
            fail(t);
            return;
        }
        // the write and finish() are not inside the catch above: a saved preset is never reported as unsaved
        boolean written;
        try {
            written = prefs.setPresets(next);
        } catch (Throwable t) {
            fail(t);
            return;
        }
        if (!written) {
            error.setText("could not save: the settings write was refused");
            return;
        }
        finish();
    }

    /** Logs the error with its stack trace and shows its class and message in full. */
    private void fail(Throwable t) {
        Log.w(TAG, "saving the preset failed", t);
        String message = t.getMessage();
        error.setText("could not save: " + t.getClass().getSimpleName() + (message == null ? "" : ": " + message));
    }
}

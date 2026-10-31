package hkgclimate.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import hkgclimate.core.Preset;
import hkgclimate.core.PresetList;
import hkgclimate.core.SeatChoices;
import java.util.List;

/**
 * Adds or edits one preset: a name and a choice for each front seat. Started only by our
 * SettingsActivity (not exported). Extras: "index" (the slot being edited, -1 for a new preset)
 * and "drvOption" / "astOption" (the seats' raw option values from the settings screen's last
 * state; missing or unknown means heat and cool). The cool choices appear only for a seat whose
 * option is heat and cool, except a cool value the preset already has for that seat. Save writes the preferences directly, only after Preset.check passes.
 */
public class PresetEditorActivity extends Activity {
    private static final String TAG = CarLink.TAG;

    static final String EXTRA_INDEX = "index";
    static final String EXTRA_DRV_OPTION = PageStates.D_DRV_OPTION;
    static final String EXTRA_AST_OPTION = PageStates.D_AST_OPTION;

    private Prefs prefs;
    private int index = -1;
    private EditText nameField;
    private RadioGroup driverGroup;
    private RadioGroup passengerGroup;
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
            Preset editing = null;
            if (index >= 0) {
                List<Preset> list = prefs.getPresets();
                if (index < list.size()) {
                    editing = list.get(index);
                } else {
                    index = -1; // the slot is gone: this becomes a new preset
                }
            }
            setContentView(buildViews(editing, drvOption, astOption));
        } catch (Throwable t) {
            Log.w(TAG, "preset editor failed to start", t);
            finish();
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

    private View buildViews(Preset editing, int drvOption, int astOption) {
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

        driverGroup = seatGroup(root, "Driver seat", SeatChoices.DRIVER_ID_BASE, drvOption,
                editing == null ? Preset.LEAVE : editing.driver);
        passengerGroup = seatGroup(root, "Passenger seat", SeatChoices.PASSENGER_ID_BASE, astOption,
                editing == null ? Preset.LEAVE : editing.passenger);

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
     * One seat's group of choices (SeatChoices decides which are offered). Each button's id is the seat's
     * id base plus its position (the seats use different bases, so Android's saved view state, which
     * is keyed by id, cannot mix them up). The seat's stored choice is always offered and checked, so a seat that is not
     * touched keeps its value when the preset is saved.
     */
    private RadioGroup seatGroup(LinearLayout root, String title, int idBase, int option, int current) {
        TextView label = text(title, 22);
        label.setPadding(0, dp(24), 0, dp(8));
        root.addView(label);
        RadioGroup g = new RadioGroup(this);
        g.setOrientation(RadioGroup.VERTICAL);
        for (int i = 0; i < SeatChoices.LABELS.length; i++) {
            if (!SeatChoices.offered(i, option, current)) continue;
            RadioButton b = new RadioButton(this);
            b.setId(SeatChoices.idOf(idBase, i));
            b.setText(SeatChoices.label(i, option));
            b.setTextSize(22);
            g.addView(b);
        }
        g.check(SeatChoices.idOf(idBase, SeatChoices.positionOf(current)));
        root.addView(g);
        return g;
    }

    /** The value of the choice checked in the group; Leave as is when none is. */
    private static int chosen(RadioGroup g, int idBase) {
        return SeatChoices.valueOfId(idBase, g.getCheckedRadioButtonId());
    }

    /** Checks the input with Preset.check; only a valid preset is stored. The reason shows otherwise. */
    private void save() {
        try {
            String name = nameField.getText().toString();
            int driver = chosen(driverGroup, SeatChoices.DRIVER_ID_BASE);
            int passenger = chosen(passengerGroup, SeatChoices.PASSENGER_ID_BASE);
            String reason = Preset.check(name, driver, passenger);
            if (reason != null) {
                error.setText(reason);
                return;
            }
            Preset p = new Preset(name, driver, passenger);
            List<Preset> list = prefs.getPresets();
            List<Preset> next;
            if (index >= 0 && index < list.size()) {
                next = PresetList.replace(list, index, p);
            } else if (list.size() < PresetList.MAX) {
                next = PresetList.add(list, p);
            } else {
                error.setText("at most " + PresetList.MAX + " presets");
                return;
            }
            prefs.setPresets(next);
            finish();
        } catch (Throwable t) {
            Log.w(TAG, "saving the preset failed", t);
            error.setText("could not save");
        }
    }
}

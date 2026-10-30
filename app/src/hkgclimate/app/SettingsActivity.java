package hkgclimate.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** The settings screen. Skeleton: one line of text. */
public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView text = new TextView(this);
        text.setText("Climate Page settings");
        setContentView(text);
    }
}

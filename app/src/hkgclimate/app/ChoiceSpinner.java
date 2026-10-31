package hkgclimate.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * A dropdown that opens its choices in our own dialog: a scrollable list of the labels with the
 * current one marked, placed against the left edge and no wider than the settings column (Column.place),
 * so it never reaches under the right-hand split panel (the platform's dialog-mode popup is centred and
 * sized by the system). Everything else is a Spinner: the adapter shows the closed view, the selection,
 * its listener and the saved view state are the Spinner's own, and a disabled one gets no taps.
 * The dialog is closed when the view leaves the window.
 */
final class ChoiceSpinner extends Spinner {
    private static final String TAG = CarLink.TAG;

    private final String title;
    private final String[] labels;
    private AlertDialog dialog;

    /** The labels are the ones the adapter shows, in the same order. */
    ChoiceSpinner(Context context, String title, String[] labels) {
        super(context, Spinner.MODE_DIALOG); // never the drop-down popup, even if performClick were not overridden
        this.title = title;
        this.labels = labels;
    }

    /** The list rows at the editor's text size, with the radio mark of a single-choice list. */
    private static final class RowAdapter extends ArrayAdapter<String> {
        RowAdapter(Context c, String[] labels) {
            super(c, android.R.layout.simple_list_item_single_choice, labels);
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View v = super.getView(position, convertView, parent);
            if (v instanceof TextView) ((TextView) v).setTextSize(22);
            return v;
        }
    }

    @Override
    public boolean performClick() {
        if (!isEnabled()) return false;
        try {
            dismissDialog();
            AlertDialog d = new AlertDialog.Builder(getContext())
                    .setTitle(title)
                    .setSingleChoiceItems(new RowAdapter(getContext(), labels), getSelectedItemPosition(),
                            new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface di, int which) {
                                    setSelection(which);
                                    di.dismiss();
                                }
                            })
                    .create();
            dialog = d;
            d.show();
            Column.place(getContext(), d);
        } catch (Throwable t) {
            Log.w(TAG, "the choice list could not be shown", t);
        }
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        dismissDialog();
        super.onDetachedFromWindow();
    }

    private void dismissDialog() {
        AlertDialog d = dialog;
        dialog = null;
        if (d != null) {
            try {
                d.dismiss();
            } catch (Throwable t) {
                Log.w(TAG, "the choice list could not be closed", t);
            }
        }
    }
}

package ma.chouitv.home.tilawah;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import ma.chouitv.home.R;

/**
 * Asks for the name of a list with the TV's keyboard (a one-line EditText in the app's dialog style, as the settings do): prefilled, the
 * keyboard up at once, «حفظ» and «إلغاء» reachable with the arrows. An empty name is refused (the dialog stays), a long one is cut to 40.
 */
final class NameDialog {
    interface Done { void name(String cleaned); }

    private NameDialog() {}

    static void ask(final Context c, String title, String initial, final Done done) {
        final EditText in = new EditText(c);
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT);
        in.setFilters(new InputFilter[] {new InputFilter.LengthFilter(Playlists.MAX_NAME)});
        in.setTypeface(Ui.font(c, "Thmanyah-Medium.otf"));
        in.setText(initial);
        in.setSelection(in.getText().length());
        final AlertDialog d = new AlertDialog.Builder(c, R.style.UrDialog).setTitle(title).setView(in)
                .setPositiveButton(R.string.name_save, null).setNegativeButton(R.string.name_cancel, null).create();
        d.setOnShowListener(x -> {
            // the positive button is wired here so that an empty name keeps the dialog open
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = Playlists.cleanName(in.getText().toString());
                if (name.isEmpty()) { Toast.makeText(c, R.string.name_empty, Toast.LENGTH_SHORT).show(); return; }
                d.dismiss();
                done.name(name);
            });
            in.requestFocus();
        });
        if (d.getWindow() != null) d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        d.show();
    }
}

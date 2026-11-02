package ma.chouitv.home.tilawah;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * Opens the system screen where exact alarms are allowed for this app (the adhan rings late without it, see AdhanScheduler.exactAllowed()).
 * It was a row of the adhan section of the listening screen; that section moved to the settings, which call this.
 */
public final class ExactAlarmSettings {
    private ExactAlarmSettings() {}

    /** From Android 12 exact alarms are the user's to allow: the system screen for it, else the app's own page. */
    public static void open(Context c) {
        Uri app = Uri.parse("package:" + c.getPackageName());
        Intent[] tries = Build.VERSION.SDK_INT >= 31
                ? new Intent[]{new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, app),
                        new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)}
                : new Intent[]{new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)};
        for (Intent intent : tries) {
            try {
                c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (RuntimeException ignored) { }
        }
    }
}

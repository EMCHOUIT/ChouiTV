package ma.chouitv.screensaver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Some TVs (TCL) leave the app cold after a power cycle and the first dream fails.
 * Any boot broadcast that does arrive brings the process up and re-arms the wake-up job (UpdateJob, which
 * does no work of its own); the persisted job covers TVs that never send one.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        UpdateJob.schedule(c);
        // after the update to 4.0 (MY_PACKAGE_REPLACED) or a boot: bring over the old app's data without waiting for the home screen to open
        final PendingResult pending = goAsync();
        Migration.runOnce(c, pending::finish);
    }
}

package ma.chouitv.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Asks for an update check from ADB (the shell only: DUMP): ma.chouitv.home.UPDATE_CHECK [--ez force true] [--ez dry true]; answers with what happened.
 * --ez status true only reports what the last attempt left behind (its plan, and the cause of a failure).
 */
public class UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (i.getBooleanExtra("status", false)) { setResultData("update: " + SelfUpdate.status(c)); return; }
        final PendingResult pending = goAsync();
        SelfUpdate.maybeStart(c, i.getBooleanExtra("force", false), i.getBooleanExtra("dry", false), message -> {
            pending.setResultData("update: " + message);
            pending.finish();
        });
    }
}

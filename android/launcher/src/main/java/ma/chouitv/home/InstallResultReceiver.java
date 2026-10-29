package ma.chouitv.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/**
 * Answers of Android's installer for the update (SelfUpdate). Nothing is shown from here: a receiver is in the background, and Android refuses it
 * the «install?» screen. The answer goes to UpdateActivity, which is in the front. When that screen is gone (the person left), the plan is kept and
 * resumes the next time the home screen is in front (SelfUpdate.run).
 */
public class InstallResultReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        UpdateActivity screen = UpdateActivity.live;
        if (screen != null) {
            screen.deliver(status, (Intent) i.getParcelableExtra(Intent.EXTRA_INTENT), message);
            return;
        }
        // no screen: a finished install is counted, a failure is recorded; a question for the person waits for the screen to come back
        if (status == PackageInstaller.STATUS_SUCCESS) SelfUpdate.advance(c);
        else if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) SelfUpdate.failed(c, "no screen; status " + status + (message == null ? "" : ": " + message), status == PackageInstaller.STATUS_FAILURE_ABORTED);
    }
}

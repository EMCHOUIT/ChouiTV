package ma.chouitv.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Answers of Android's installer for the update (SelfUpdate): asks the user, goes on to the next app, or gives up. */
public class InstallResultReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION: {   // the «install?» screen: OK with the remote
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { c.startActivity(confirm); } catch (RuntimeException e) { SelfUpdate.failed(c, "cannot show the installer: " + e); }
                }
                break;
            }
            case PackageInstaller.STATUS_SUCCESS:
                SelfUpdate.installNext(c);   // the screensaver is done: now this app (which ends this process)
                SelfUpdate.finished(c);
                break;
            default:   // refused, cancelled or failed
                SelfUpdate.failed(c, i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
        }
    }
}

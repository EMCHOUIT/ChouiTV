package ma.chouitv.home;

/**
 * The same update check as UpdateReceiver, for the screensaver's settings («تحديث التطبيق والشاشة الآن»): the screensaver holds the
 * BACKUP permission (same signing key), so only the two ChouiTV apps can ask. ma.chouitv.home.UPDATE_NOW always forces the check.
 * The check alone (dry) answers when it is done; the real update answers at once («started»): the downloads take longer than a broadcast may wait.
 */
public class UpdateNowReceiver extends UpdateReceiver {
    @Override
    public void onReceive(android.content.Context c, android.content.Intent i) {
        i.putExtra("force", true);
        if (i.getBooleanExtra("dry", false)) { super.onReceive(c, i); return; }
        SelfUpdate.maybeStart(c, true, false, null);
        setResultData("update: started");
    }
}

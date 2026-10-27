package ma.chouitv.home.tilawah;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The screensaver ended (a key on the remote woke the TV): the adhan that was ringing stops. Only an app with the same key may send this. */
public class AdhanStopReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (MusicService.adhanRinging()) MusicService.stopAdhan(context);
    }
}

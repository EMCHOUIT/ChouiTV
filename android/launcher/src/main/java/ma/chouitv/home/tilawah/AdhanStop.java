package ma.chouitv.home.tilawah;

import android.content.Context;
import android.view.KeyEvent;

/**
 * Any key on the remote ends the adhan of a prayer while it rings, on every screen of the home app and of «الاستماع» (the screensaver does the
 * same when it ends: AdhanStopReceiver). The key that stops it is not passed on, so it does nothing else; the volume keys keep their job, and a
 * trial of a muezzin (the settings) is not stopped by the keys, the person is using the screen.
 */
public final class AdhanStop {
    private AdhanStop() { }

    /** True when this key press ended a ringing adhan: the caller then swallows it. */
    public static boolean onKey(Context c, KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_POWER:
                return false;
            default:
                break;
        }
        if (!MusicService.adhanRinging()) return false;
        MusicService.stopAdhan(c);
        return true;
    }
}

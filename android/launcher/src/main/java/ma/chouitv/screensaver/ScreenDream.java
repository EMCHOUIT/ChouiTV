package ma.chouitv.screensaver;

import android.content.ComponentName;
import android.content.Intent;
import android.service.dreams.DreamService;
import android.webkit.WebView;

/** The system screensaver: starts on the TV's own idle timer, any key wakes it. */
public class ScreenDream extends DreamService {
    private WebView web;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        web = ScreenView.create(this);
        setContentView(web);
    }

    /** Any key wakes the TV and ends the dream: an adhan that is ringing stops with it (the home app decides whether one is). */
    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        try {
            sendBroadcast(new Intent("ma.chouitv.home.ADHAN_STOP_KEY")
                .setComponent(new ComponentName("ma.chouitv.home", "ma.chouitv.home.tilawah.AdhanStopReceiver")));
        } catch (RuntimeException ignored) { }
    }

    @Override
    public void onDetachedFromWindow() {
        ScreenView.release(web);
        web = null;
        super.onDetachedFromWindow();
    }
}

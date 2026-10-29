package ma.chouitv.home;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The screen of an update in progress (SelfUpdate): the install of this app (one package since 4.0). It is in the front, which is what lets Android
 * show its «install?» screen (an app in the background is refused that), and it keeps the person told that OK on the remote is what is asked. It
 * answers three ways: an install done, refused by the person (nothing is asked again for a day), or failed for a technical reason (its cause is
 * kept, and it is tried again later). Installing ourselves replaces this process: the install result usually never arrives here, and the new
 * process finds the plan entry already at the target version (SelfUpdate.run).
 */
public class UpdateActivity extends Activity {
    /** set while the screen is there: SelfUpdate waits for it before it starts the installs */
    static volatile UpdateActivity live;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView title, detail;
    private boolean answered;   // an answer of Android's installer arrived for the package being installed

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(0xFF040E09);
        box.setLayoutDirection(LinearLayout.LAYOUT_DIRECTION_RTL);
        box.setPadding(dp(80), dp(40), dp(80), dp(40));
        title = new TextView(this);
        title.setTextSize(34);
        title.setTextColor(0xFFF6EFE0);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(FontSource.typeface(this, "ThmanyahDisplay-Bold.otf"));
        detail = new TextView(this);
        detail.setTextSize(20);
        detail.setTextColor(0xFFDCB86F);
        detail.setGravity(Gravity.CENTER);
        detail.setTypeface(FontSource.typeface(this, "Thmanyah-Regular.otf"));
        detail.setPadding(0, dp(16), 0, 0);
        box.addView(title);
        box.addView(detail);
        setContentView(box);
        title.setText("تحديث تلفزيونك");
        detail.setText("جارٍ التحضير…");
        live = this;
        startNext();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    /** the next package of the plan into an installer session, off the main thread */
    private void startNext() {
        answered = false;
        new Thread(() -> SelfUpdate.installNext(getApplicationContext()), "update-install").start();
    }

    /** Android's installer answered for the package being installed (InstallResultReceiver). */
    void deliver(final int status, final Intent confirm, final String message) {
        ui.post(() -> {
            if (isFinishing()) return;
            switch (status) {
                case PackageInstaller.STATUS_PENDING_USER_ACTION:
                    detail.setText("في شاشة التثبيت: حرّك الريموت يمينًا إلى «تثبيت» ثم اضغط OK");
                    if (confirm != null) {
                        // started from this screen, which is in the front, and without a result to wait for: the installer answers by its own broadcast
                        try { startActivity(confirm); }
                        catch (RuntimeException e) { end(false, "cannot show the installer: " + e); }
                    } else {
                        end(false, "the installer gave no screen to show");
                    }
                    break;
                case PackageInstaller.STATUS_SUCCESS:
                    answered = true;
                    new Thread(() -> {
                        final boolean done = SelfUpdate.advance(getApplicationContext());
                        ui.post(() -> { if (done) finish(); else { startNext(); } });
                    }, "update-advance").start();
                    break;
                case PackageInstaller.STATUS_FAILURE_ABORTED:   // the person cancelled
                    answered = true;
                    end(true, message);
                    break;
                default:
                    answered = true;
                    end(false, "status " + status + (message == null ? "" : ": " + message));
            }
        });
    }

    private void end(boolean declined, String why) {
        SelfUpdate.failed(getApplicationContext(), why, declined);
        finish();
    }

    @Override public void onBackPressed() { end(true, "cancelled with BACK"); }

    @Override protected void onDestroy() {
        if (live == this) live = null;
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}

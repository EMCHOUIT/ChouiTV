package ma.chouitv.home.tilawah;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The adhan alarm was set for the prayer times as they were. When «تعديل وقت الصلاة» changes (on the TV's screen or from the
 * shell) the alarm has to be set again, or it rings at the old minute. The home screen calls this once a minute with the
 * screensaver's settings it has read anyway; the shell's {@code ADHAN_RESCHEDULE} does the same at once.
 */
public final class AdhanSync {
    private AdhanSync() { }

    /** What the alarm depends on besides the place: the minutes added, the zone and the calculation (the person can change all three). */
    private static String identity(Place p) {
        return PrayerAdjust.text(p.adj) + "|" + p.tz + "|" + p.method;
    }

    /** Sets the alarm again when the adjustments, the zone or the calculation in this fragment are not the ones it was last set with. */
    public static void onConfig(Context c, String fragment) {
        String now = identity(Place.get(c, fragment));
        SharedPreferences sp = c.getSharedPreferences("tilawah", Context.MODE_PRIVATE);
        String was = sp.getString("adj_scheduled", "");
        if (now.equals(was)) return;
        sp.edit().putString("adj_scheduled", now).apply();
        AdhanScheduler.schedule(c);
    }

    /** The shell asks: whatever the settings say now, set the alarm for it. */
    public static void reschedule(Context c) {
        SharedPreferences sp = c.getSharedPreferences("tilawah", Context.MODE_PRIVATE);
        sp.edit().putString("adj_scheduled", identity(Place.get(c))).apply();
        AdhanScheduler.schedule(c);
    }
}

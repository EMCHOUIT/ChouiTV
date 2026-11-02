package ma.chouitv.home.tilawah;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.KeyEvent;

/**
 * The adhan as the settings screen (ma.chouitv.screensaver.SettingsActivity, tab «الصلاة», group «أذان الصلوات») sees it. Prefs, Adhan and
 * PrayerTimes stay package-private; this is the only door, and every change goes the way it always went: the preference, then the alarm
 * set again for it (AdhanScheduler.schedule), so the next adhan is right at once. The lines are in the screen's digits (Ui.digits).
 */
public final class AdhanSettings {
    private AdhanSettings() { }

    // ---- the switch ----

    public static boolean on(Context c) { return new Prefs(c).adhanOn(); }

    /** «الأذان التلقائي»: on or off, and the alarm for the next prayer whose adhan is on (or none). */
    public static void setOn(Context c, boolean on) {
        new Prefs(c).setAdhanOn(on);
        AdhanScheduler.schedule(c);
        if (on) warmUp(c);
    }

    /** «مفعّل · العصر بعد ٣٨ د», or «متوقف»: the state and the next prayer that will ring. */
    public static String stateLine(Context c) {
        PrayerTimes.Next next = PrayerTimes.nextAdhan(c);
        return Ui.digits(AdhanSettingsText.state(on(c), next == null ? null : next.name(),
                next == null ? 0 : next.at - System.currentTimeMillis()));
    }

    /** Today's times and the recordings, as the listening screen asked for them when it opened (each at most once a day, off the main thread). */
    public static void warmUp(Context c) {
        PrayerTimes.refresh(c, null);
        Adhan.prefetchAll(c);
    }

    // ---- the muezzin ----

    /** The 21 recordings (and «أذان من ملفي»): OK on one plays a few seconds of it and keeps it; back or the choice returns to the caller. */
    public static Intent voiceScreen(Context c) { return new Intent(c, AdhanActivity.class); }

    public static String voiceLine(Context c) { return Adhan.label(new Prefs(c).playVoice(c)); }

    // ---- the volume ----

    public static int volume(Context c) { return new Prefs(c).adhanVolume(); }

    /** «٣٠٪  ███░░░░░░░»: the percent and a bar of ten that fills from the right. */
    public static String volumeLine(Context c) {
        int value = volume(c);
        int filled = Math.max(0, Math.min(10, value / 10));
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) bar.append(i < filled ? '█' : '░');
        return Ui.digits(AdhanSettingsText.percent(value)) + "  " + bar;
    }

    /** The adhan's volume in steps of 10 (10 to 100, no wrapping), heard for two seconds at the new level. True when it changed. */
    public static boolean changeVolume(Context c, int step) {
        Prefs prefs = new Prefs(c);
        int was = prefs.adhanVolume(), now = Math.max(10, Math.min(100, was + step));
        if (now == was) return false;
        prefs.setAdhanVolume(now);
        int voice = prefs.playVoice(c);
        MusicService.adhan(c, voice, Adhan.name(voice), 2);
        return true;
    }

    // ---- the prayers ----

    public static int prayerCount() { return PrayerTimes.PRAYERS.length; }

    public static String prayerName(int slot) { return PrayerTimes.NAMES[PrayerTimes.PRAYERS[slot]]; }

    public static boolean prayerOn(Context c, int slot) { return new Prefs(c).adhanPrayerOn(slot); }

    /** One prayer's own switch (the options screen has the same): the preference, then the alarm for the next one that rings. */
    public static void setPrayerOn(Context c, int slot, boolean on) {
        new Prefs(c).setAdhanPrayerOn(slot, on);
        AdhanScheduler.schedule(c);
    }

    /** «الخمس كلها» or «٤ من ٥ · بدون الفجر»: which of the five prayers ring. */
    public static String prayersLine(Context c) {
        Prefs prefs = new Prefs(c);
        String[] names = new String[prayerCount()];
        boolean[] ringing = new boolean[names.length];
        for (int i = 0; i < names.length; i++) { names[i] = prayerName(i); ringing[i] = prefs.adhanPrayerOn(i); }
        return Ui.digits(AdhanSettingsText.prayers(names, ringing));
    }

    /** One prayer's own options: its minutes, its adhan on or off, a trial of the adhan. */
    public static Intent prayerScreen(Context c, int slot) {
        return new Intent(c, PrayerOptionsActivity.class).putExtra(PrayerOptionsActivity.EXTRA_SLOT, slot);
    }

    /** «الفجر ٥:١٢ ص»: today's five times (the minutes the person added are in them), the next prayer marked with «●». */
    public static String[] todayLines(Context c) {
        PrayerTimes times = PrayerTimes.today(c);
        PrayerTimes.Next next = PrayerTimes.next(c);
        String[] out = new String[prayerCount()];
        for (int i = 0; i < out.length; i++) {
            int prayer = PrayerTimes.PRAYERS[i];
            out[i] = (next != null && next.index == prayer ? "● " : "") + PrayerTimes.NAMES[prayer] + "  " + PrayerTimes.clock12(times.clock[prayer]);
        }
        return out;
    }

    // ---- exact alarms ----

    /** False from Android 12 when exact alarms are not allowed: the adhan may then come minutes late. */
    public static boolean exactAllowed(Context c) { return AdhanScheduler.exactAllowed(c); }

    /** The system screen for exact alarms (Android 12+), else the app's own page; false when neither opens. */
    public static boolean openExactAlarmSettings(Context c) {
        Uri app = Uri.parse("package:" + c.getPackageName());
        Intent[] tries = Build.VERSION.SDK_INT >= 31
                ? new Intent[]{new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, app), new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)}
                : new Intent[]{new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)};
        for (Intent intent : tries) {
            try { c.startActivity(intent); return true; } catch (RuntimeException ignored) { }
        }
        return false;
    }

    // ---- a ringing adhan ----

    /** Any key ends a ringing adhan, on this screen too (see AdhanStop): true when the key did, and the caller swallows it. */
    public static boolean onKey(Context c, KeyEvent e) { return AdhanStop.onKey(c, e); }
}

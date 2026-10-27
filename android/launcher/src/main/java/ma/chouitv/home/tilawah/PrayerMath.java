package ma.chouitv.home.tilawah;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The arithmetic of the prayer times and of their cache, with nothing of Android in it, so a plain JVM can run it
 * (tests/PrayerMathRun.java). PrayerTimes holds the rest: the place, the preferences, Aladhan.
 */
final class PrayerMath {
    static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;

    private static final double RAD = Math.PI / 180;
    private static final Pattern CLOCK = Pattern.compile("^\\s*(\\d{1,2}):(\\d{2})");

    private PrayerMath() {}

    /**
     * A calculation, named by the id Aladhan gives it (the page and the online times ask Aladhan for the same one): Fajr and Isha are
     * angles below the horizon, or Isha is a fixed delay after Maghrib; the tune moves sunrise, Dhuhr, Asr and Maghrib by whole minutes
     * (the safety margins some authorities publish). The figures are Aladhan's list (api.aladhan.com/v1/methods) and, for Morocco,
     * Algeria, Tunisia, Jordan and the UAE, the ones the upstream project (tshi-dev/UrTV) adopted; template.html's PRAYER_METHODS says the same.
     */
    static final class Method {
        final int id;
        final double fajr, isha, maghrib;
        final int ishaMinutes;
        final int[] tune;   // sunrise, Dhuhr, Asr, Maghrib
        Method(int id, double fajr, double isha, int ishaMinutes, double maghrib, int... tune) {
            this.id = id; this.fajr = fajr; this.isha = isha; this.ishaMinutes = ishaMinutes; this.maghrib = maghrib;
            this.tune = tune.length == 4 ? tune : new int[4];
        }
        /** Isha is the delay after Maghrib; false: an angle, and Aladhan's Isha is kept too */
        boolean ishaIsDelay() { return ishaMinutes > 0; }
    }

    static final int UMM_AL_QURA = 4;
    private static final Method[] METHODS = {
        new Method(0, 16, 14, 0, 4),                    // Ja'fari (Leva, Qum)
        new Method(2, 15, 15, 0, .833),                 // North America (ISNA)
        new Method(3, 18, 17, 0, .833),                 // Muslim World League
        new Method(UMM_AL_QURA, 18.5, 0, 90, .833),    // Umm al-Qura
        new Method(5, 19.5, 17.5, 0, .833),             // Egypt (and Syria, Lebanon, Palestine)
        new Method(8, 19.5, 0, 90, .833),               // Gulf (Bahrain, Oman)
        new Method(9, 18, 17.5, 0, .833),               // Kuwait
        new Method(10, 18, 0, 90, .833),                // Qatar
        new Method(12, 12, 12, 0, .833),                // France (UOIF)
        new Method(16, 18.2, 18.2, 0, .833, -3, 3, 3, 3), // the UAE
        new Method(18, 18, 18, 0, .833),                // Tunisia
        new Method(19, 18, 17, 0, .833),                // Algeria
        new Method(21, 19, 17, 0, .833, 0, 5, 0, 5),    // Morocco (the Awqaf ministry): Dhuhr and Maghrib +5
        new Method(23, 18, 18, 0, .833, 0, 0, 0, 5),    // Jordan
    };

    static Method method(int id) {
        for (Method m : METHODS) if (m.id == id) return m;
        return method(UMM_AL_QURA);
    }

    /** Isha comes this long after Maghrib (Umm al-Qura): 90 minutes, 120 in Ramadan. */
    static long ishaDelayMillis(boolean ramadan) { return ishaDelayMillis(ramadan, UMM_AL_QURA); }

    /** The same for a method with a delay: the Ramadan half hour is Umm al-Qura's alone. */
    static long ishaDelayMillis(boolean ramadan, int methodId) {
        Method m = method(methodId);
        return (m.ishaMinutes + (ramadan && m.id == UMM_AL_QURA ? 30 : 0)) * 60_000L;
    }

    /** Local midnight of that day in that zone. */
    static long midnight(int y, int mo, int d, String tz) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone(tz), Locale.US);
        c.clear();
        c.set(y, mo - 1, d, 0, 0, 0);
        return c.getTimeInMillis();
    }

    /**
     * The six times of that local day (Fajr, sunrise, Dhuhr, Asr, Maghrib, Isha) as moments. Isha is an absolute
     * time, Maghrib plus its delay: at a high latitude, where Maghrib is late, it falls after midnight, on the next
     * day, instead of wrapping back to an hour of this day that has already gone.
     */
    static long[] compute(int y, int mo, int d, double lat, double lng, String tz, boolean ramadan) {
        return compute(y, mo, d, lat, lng, tz, ramadan, UMM_AL_QURA);
    }

    /** The same by the calculation of that Aladhan id (Method). */
    static long[] compute(int y, int mo, int d, double lat, double lng, String tz, boolean ramadan, int methodId) {
        Method m = method(methodId);
        TimeZone zone = TimeZone.getTimeZone(tz);
        long day0 = midnight(y, mo, d, tz);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        utc.clear();
        utc.set(y, mo - 1, d, 0, 0, 0);
        double jd0 = utc.getTimeInMillis() / 86400000.0 + 2440587.5 - lng / 360.0;
        double tzHours = zone.getOffset(day0 + 12 * 3600_000L) / 3600000.0;

        double fajr = adj(angleTime(jd0, lat, m.fajr, 5, true), tzHours, lng);
        double sunrise = adj(angleTime(jd0, lat, .833, 6, true), tzHours, lng);
        double dhuhr = adj(noon(jd0, 12), tzHours, lng);
        double asr = adj(asrTime(jd0, lat, 13), tzHours, lng);
        double maghrib = adj(angleTime(jd0, lat, m.maghrib, 18, false), tzHours, lng);

        long[] at = new long[6];
        at[FAJR] = day0 + minutesOfDay(fajr) * 60_000L;
        at[SUNRISE] = day0 + (minutesOfDay(sunrise) + m.tune[0]) * 60_000L;
        at[DHUHR] = day0 + (minutesOfDay(dhuhr) + m.tune[1]) * 60_000L;
        at[ASR] = day0 + (minutesOfDay(asr) + m.tune[2]) * 60_000L;
        at[MAGHRIB] = day0 + (minutesOfDay(maghrib) + m.tune[3]) * 60_000L;
        if (m.ishaIsDelay()) {
            at[ISHA] = at[MAGHRIB] + ishaDelayMillis(ramadan, m.id);
        } else {
            double isha = adj(angleTime(jd0, lat, m.isha, 18, false), tzHours, lng);
            at[ISHA] = Math.max(at[MAGHRIB], day0 + Math.round(isha * 60) * 60_000L);   // absolute, like the delay: never wrapped to the morning
        }
        return at;
    }

    /** The preference key of the day's times: the date in the city's zone and the city itself, so a new city refetches. */
    static String key(long millis, double lat, double lng, String tz) { return key(millis, lat, lng, tz, UMM_AL_QURA); }

    /** The same per calculation: another method is another set of times (Umm al-Qura keeps its old key, so nothing is refetched for it). */
    static String key(long millis, double lat, double lng, String tz, int methodId) {
        return "times_" + format("yyyyMMdd", millis, tz) + "_" + String.format(Locale.US, "%.3f_%.3f", lat, lng) + "_" + tz
            + (methodId == UMM_AL_QURA ? "" : "_m" + methodId);
    }

    /** The date Aladhan is asked for: the city's own day, not the TV's. */
    static String aladhanDate(long millis, String tz) { return format("dd-MM-yyyy", millis, tz); }

    /** "5:12" or "05:12 (+03)" -> {5, 12}; null when it is not a time. Aladhan puts a zone after the time on some days. */
    static int[] parseClock(String v) {
        if (v == null) return null;
        Matcher m = CLOCK.matcher(v);
        if (!m.find()) return null;
        int h = Integer.parseInt(m.group(1)), min = Integer.parseInt(m.group(2));
        return h < 24 && min < 60 ? new int[]{h, min} : null;
    }

    /** "HH:mm" of that moment in that zone. */
    static String clockOf(long at, String tz) { return format("HH:mm", at, tz); }

    private static String format(String pattern, long millis, String tz) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(TimeZone.getTimeZone(tz));
        return f.format(millis);
    }

    private static int minutesOfDay(double hours) { return (int) Math.round(fix(hours, 24) * 60) % 1440; }

    private static double adj(double h, double tzHours, double lng) { return h + tzHours - lng / 15.0; }

    private static double noon(double jd0, double t) { return fix(12 - sun(jd0, t / 24)[1], 24); }

    private static double angleTime(double jd0, double lat, double angle, double t, boolean before) {
        double decl = sun(jd0, t / 24)[0];
        double n = noon(jd0, t);
        double cosT = (-Math.sin(angle * RAD) - Math.sin(decl * RAD) * Math.sin(lat * RAD))
                / (Math.cos(decl * RAD) * Math.cos(lat * RAD));
        double T = Math.acos(Math.max(-1, Math.min(1, cosT))) / RAD / 15;
        return n + (before ? -T : T);
    }

    private static double asrTime(double jd0, double lat, double t) {
        double decl = sun(jd0, t / 24)[0];
        double a = -Math.atan(1 / (1 + Math.tan(Math.abs(lat - decl) * RAD))) / RAD;
        return angleTime(jd0, lat, a, t, false);
    }

    /** declination and equation of time */
    private static double[] sun(double jd0, double t) {
        double D = jd0 + t - 2451545;
        double g = fix(357.529 + .98560028 * D, 360);
        double q = fix(280.459 + .98564736 * D, 360);
        double L = fix(q + 1.915 * Math.sin(g * RAD) + .020 * Math.sin(2 * g * RAD), 360);
        double e = 23.439 - .00000036 * D;
        double ra = fix(Math.atan2(Math.cos(e * RAD) * Math.sin(L * RAD), Math.cos(L * RAD)) / RAD / 15, 24);
        double decl = Math.asin(Math.sin(e * RAD) * Math.sin(L * RAD)) / RAD;
        return new double[]{decl, q / 15 - ra};
    }

    private static double fix(double a, double b) {
        a = a - b * Math.floor(a / b);
        return a < 0 ? a + b : a;
    }
}

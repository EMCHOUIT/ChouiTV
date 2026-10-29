package ma.chouitv.home.tilawah;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import ma.chouitv.home.R;
import java.net.URLDecoder;

/**
 * Where the TV is, for the prayer times: the city picked in the screensaver (its ConfigProvider,
 * same signing key) and, when that app is missing or older, values in res/values/config.xml.
 */
final class Place {
    static final Uri CONFIG = Uri.parse("content://ma.chouitv.home.config");

    final double lat, lng;
    final String tz;
    /** «تعديل وقت الصلاة»: minutes added to the five prayers (see PrayerAdjust); zeros unless the settings say so */
    final int[] adj;
    /** the calculation, by Aladhan's id (#pm= of the settings fragment: the zone's own or the person's pick); 4, Umm al-Qura, when there is none */
    final int method;
    /** the calculation as the screensaver's settings name it («تلقائي · المغرب · وزارة الأوقاف»), for the adhan section; empty from an older screensaver */
    final String methodLabel;

    private Place(double lat, double lng, String tz, int[] adj, int method, String methodLabel) {
        this.lat = lat; this.lng = lng; this.tz = tz; this.adj = adj; this.method = method; this.methodLabel = methodLabel;
    }

    /** «تعديل وقت الصلاة» from the adhan section: the five minutes go to the screensaver's settings (WRITE_TASKS, the same key), so the page, the screen and the adhan agree. */
    static boolean saveAdjust(Context c, int[] adj) {
        try {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put("adj", PrayerAdjust.text(adj));
            return c.getContentResolver().update(Uri.withAppendedPath(CONFIG, "prayeradj"), v, null, null) > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static Place get(Context c) {
        try (Cursor cur = c.getContentResolver().query(CONFIG, null, null, null, null)) {
            if (cur != null && cur.moveToFirst() && cur.getString(0) != null) {
                Place p = fromFragment(cur.getString(0));
                if (p != null) return p;
            }
        } catch (RuntimeException ignored) {
            // screensaver app missing or signed with another key: fall back to the bundled values
        }
        return bundled(c);
    }

    /** From a settings fragment the caller has already read (one read for a whole tick); null = the screensaver gave none. */
    static Place get(Context c, String fragment) {
        Place p = fragment == null ? null : fromFragment(fragment);
        return p != null ? p : bundled(c);
    }

    private static Place bundled(Context c) {
        return new Place(number(c.getString(R.string.latitude), 24.6877),
                number(c.getString(R.string.longitude), 46.7219),
                orDefault(c.getString(R.string.timezone), "Asia/Riyadh"), new int[PrayerAdjust.COUNT], PrayerMath.UMM_AL_QURA, "");
    }

    /** "city=..&lat=24.7&lon=46.7&tz=Asia%2FRiyadh&..." — the same fragment the page reads. */
    private static Place fromFragment(String fragment) {
        try {
            String lat = null, lon = null, tz = null, pm = null, pml = null;
            for (String part : fragment.split("&")) {
                int eq = part.indexOf('=');
                if (eq < 0) continue;
                String key = part.substring(0, eq), value = URLDecoder.decode(part.substring(eq + 1), "UTF-8");
                if ("lat".equals(key)) lat = value;
                else if ("lon".equals(key)) lon = value;
                else if ("tz".equals(key)) tz = value;
                else if ("pm".equals(key)) pm = value;
                else if ("pml".equals(key)) pml = value;
            }
            if (lat == null || lon == null) return null;
            int method = (int) number(pm, PrayerMath.UMM_AL_QURA);
            return new Place(number(lat, 24.6877), number(lon, 46.7219), orDefault(tz, "Asia/Riyadh"),
                    PrayerAdjust.fromFragment(fragment), PrayerMath.method(method).id, pml == null ? "" : pml);
        } catch (Exception e) {
            return null;
        }
    }

    private static double number(String s, double fallback) {
        if (s == null) return fallback;
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return fallback; }
    }

    private static String orDefault(String s, String fallback) {
        return s == null || s.trim().isEmpty() ? fallback : s.trim();
    }
}

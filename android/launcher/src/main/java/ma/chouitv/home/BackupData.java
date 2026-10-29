package ma.chouitv.home;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The home app's own settings for «تصدير الإعدادات / استيراد الإعدادات»: the adhan (on, muezzin, volume, the prayers whose adhan is off), the
 * favourites row of the dock (what is shown, hidden, the names) and the radios (the countries, the stations unticked, the favourites). The screensaver's settings screen asks for them through BackupProvider (same signing
 * key) and puts them in the one file. Nothing else of this app is exported: not what is playing, not update state, not the person's own recording.
 */
public final class BackupData {
    private static final Pattern ADHAN = Pattern.compile("^(adhan_on|adhan_volume|adhan_voice|adhan_p[0-4])$");
    private static final Pattern DOCK = Pattern.compile("^(dock|hidden|shown|names)$");
    private static final Pattern PACKAGES = Pattern.compile("^[A-Za-z0-9_.,]{0,2000}$");

    private BackupData() { }

    private static SharedPreferences adhanPrefs(Context c) { return c.getSharedPreferences("tilawah", Context.MODE_PRIVATE); }

    public static JSONObject export(Context c) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("adhan", dump(adhanPrefs(c), ADHAN));
        o.put("dock", dump(Dock.prefs(c), DOCK));
        o.put("radio", ma.chouitv.home.tilawah.RadioBackup.export(c));   // the radios' countries, the stations unticked, the favourites
        return o;
    }

    private static JSONObject dump(SharedPreferences sp, Pattern allowed) throws JSONException {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, ?> e : new TreeMap<String, Object>(sp.getAll()).entrySet()) {
            Object v = e.getValue();
            if (allowed.matcher(e.getKey()).matches() && (v instanceof Boolean || v instanceof Integer || v instanceof String)) o.put(e.getKey(), v);
        }
        return o;
    }

    /** Checks the whole file, then writes it: what is not in it goes back to its default. Returns how many settings changed; throws with the reason when it is refused. */
    public static int apply(Context c, JSONObject o) throws JSONException {
        JSONObject adhan = o.optJSONObject("adhan"), dock = o.optJSONObject("dock"), radio = o.optJSONObject("radio");
        if (adhan == null) adhan = new JSONObject();
        if (dock == null) dock = new JSONObject();
        if (radio == null) radio = new JSONObject();   // a file from before the radios: they go back to their default (Morocco)
        ma.chouitv.home.tilawah.RadioBackup.check(radio);
        for (Iterator<String> it = adhan.keys(); it.hasNext(); ) {
            String k = it.next();
            Object v = adhan.get(k);
            if (!ADHAN.matcher(k).matches()) throw new IllegalArgumentException("adhan: unknown setting " + k);
            if (k.equals("adhan_volume")) {
                if (!(v instanceof Integer) || (Integer) v < 10 || (Integer) v > 100) throw new IllegalArgumentException("adhan_volume is 10..100");
            } else if (k.equals("adhan_voice")) {
                if (!(v instanceof Integer) || (Integer) v < 1 || (Integer) v > 21) throw new IllegalArgumentException("adhan_voice is 1..21");
            } else if (!(v instanceof Boolean)) {
                throw new IllegalArgumentException(k + " is on or off");
            }
        }
        for (Iterator<String> it = dock.keys(); it.hasNext(); ) {
            String k = it.next();
            Object v = dock.get(k);
            if (!DOCK.matcher(k).matches()) throw new IllegalArgumentException("dock: unknown setting " + k);
            if (!(v instanceof String)) throw new IllegalArgumentException(k + " is text");
            String s = (String) v;
            if (k.equals("names") ? s.length() > 6000 : !PACKAGES.matcher(s).matches()) throw new IllegalArgumentException(k + " is not valid");
        }
        int n = write(adhanPrefs(c), ADHAN, adhan) + write(Dock.prefs(c), DOCK, dock) + ma.chouitv.home.tilawah.RadioBackup.apply(c, radio);
        ma.chouitv.home.tilawah.AdhanSync.reschedule(c);   // the alarm follows the adhan settings
        c.sendBroadcast(new android.content.Intent(Dock.CHANGED).setPackage(c.getPackageName()));   // a running home screen redraws its favourites row
        return n;
    }

    private static int write(SharedPreferences sp, Pattern allowed, JSONObject want) throws JSONException {
        SharedPreferences.Editor ed = sp.edit();
        int n = 0;
        for (String k : sp.getAll().keySet()) {
            if (allowed.matcher(k).matches() && !want.has(k)) { ed.remove(k); n++; }
        }
        for (Iterator<String> it = want.keys(); it.hasNext(); ) {
            String k = it.next();
            Object v = want.get(k), old = sp.getAll().get(k);
            if (v.equals(old)) continue;
            n++;
            if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
            else if (v instanceof Integer) ed.putInt(k, (Integer) v);
            else ed.putString(k, (String) v);
        }
        ed.commit();
        return n;
    }
}

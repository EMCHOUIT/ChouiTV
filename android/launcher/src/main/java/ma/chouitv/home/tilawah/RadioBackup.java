package ma.chouitv.home.tilawah;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The radios' part of «تصدير / استيراد الإعدادات» (BackupData asks for it): the countries, the stations unticked and the favourites.
 * Not the downloaded lists: a TV that gets a file with other countries downloads them itself, once the file is applied.
 */
public final class RadioBackup {
    private RadioBackup() {}

    private static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences("radio", Context.MODE_PRIVATE); }

    public static JSONObject export(Context c) throws JSONException {
        JSONObject o = new JSONObject();
        SharedPreferences p = sp(c);
        if (p.contains("countries")) o.put("countries", RadioChoice.join(RadioChoice.parse(p.getString("countries", ""))));
        Set<String> off = new TreeSet<>(p.getStringSet("off", new HashSet<String>())), fav = new TreeSet<>(p.getStringSet("fav", new HashSet<String>()));
        if (!off.isEmpty()) o.put("off", join(off, ","));
        if (!fav.isEmpty()) o.put("fav", join(fav, "\n"));
        return o;
    }

    private static String join(Set<String> set, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String s : set) sb.append(sb.length() == 0 ? "" : sep).append(s);
        return sb.toString();
    }

    private static List<String> offered() {
        List<String> out = new ArrayList<>(Arrays.asList(RadioStore.OFFERED));
        out.add(RadioChoice.HOME);
        return out;
    }

    /** Throws with the reason when the file's radios are not valid; writes nothing then. */
    public static void check(JSONObject want) {
        for (java.util.Iterator<String> it = want.keys(); it.hasNext(); ) {
            String k = it.next();
            if (!(k.equals("countries") || k.equals("off") || k.equals("fav"))) throw new IllegalArgumentException("radio: unknown setting " + k);
            if (!(want.opt(k) instanceof String)) throw new IllegalArgumentException("radio " + k + " is text");
        }
        String bad = RadioBackupRules.check(offered(), want.optString("countries"), want.optString("off"), want.optString("fav"));
        if (bad != null) throw new IllegalArgumentException(bad);
    }

    /** Writes the radios of a checked file (what it does not name goes back to its default); returns how many settings changed. */
    public static int apply(Context c, JSONObject want) {
        SharedPreferences p = sp(c);
        SharedPreferences.Editor ed = p.edit();
        int n = 0;
        List<String> now = p.contains("countries") ? RadioChoice.parse(p.getString("countries", "")) : null;
        if (want.has("countries")) {
            List<String> next = RadioChoice.parse(want.optString("countries"));
            if (!next.equals(now)) n++;
            ed.putString("countries", RadioChoice.join(next));
        } else if (now != null) {
            ed.remove("countries");
            n++;
        }
        Set<String> off = new HashSet<>(RadioBackupRules.split(want.optString("off"), ","));
        Set<String> fav = new HashSet<>(RadioBackupRules.split(want.optString("fav"), "\n"));
        if (!off.equals(p.getStringSet("off", new HashSet<String>()))) n++;
        if (!fav.equals(p.getStringSet("fav", new HashSet<String>()))) n++;
        if (off.isEmpty()) ed.remove("off"); else ed.putStringSet("off", off);
        if (fav.isEmpty()) ed.remove("fav"); else ed.putStringSet("fav", fav);
        ed.commit();
        RadioStore.changed(c);   // the catalogue follows the countries and the unticked stations
        if (n > 0) downloadMissing(c);
        return n;
    }

    /** A country of the file with no list on this TV yet: its list is downloaded now (the lists are not in the file). */
    private static void downloadMissing(Context c) {
        for (String cc : RadioStore.toDownload(c)) {
            if (!RadioStore.downloaded(c, cc)) { RadioStore.update(c, (RadioStore.Done) null); return; }
        }
    }
}

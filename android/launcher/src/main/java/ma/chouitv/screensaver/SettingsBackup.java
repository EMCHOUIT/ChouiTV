package ma.chouitv.screensaver;

import ma.chouitv.home.BuildConfig;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * «تصدير الإعدادات / استيراد الإعدادات» and `saver.sh export | import`: everything the person set, in one JSON file, to move to another TV or to
 * get back after a change. The file holds the screensaver's settings ("settings") and the home app's adhan, dock and radio settings ("home", fetched
 * through its BackupProvider). It never holds the person's photos, tasks, update state, TV address or serial. An import is checked whole
 * before anything is written, keeps what was there in settings-before-import.json (`import --undo`), and what the file does not name goes
 * back to its default, so the TV ends up as the file says.
 */
final class SettingsBackup {
    static final String FILE_NAME = "chouitv-settings.json";
    private static final int SCHEMA = 1;
    private static final Uri HOME = Uri.parse("content://ma.chouitv.home.backup");
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_]{1,48}$");
    /** state that belongs to this TV or this moment, not to the person's choices */
    private static final Pattern SKIP = Pattern.compile("^(lastCheck|latestCheck|latestVersion|etag|ownRev|ownMode|tasks|settingsTab|m_[A-Za-z0-9_]*)$");

    private SettingsBackup() { }

    // ---------------------------------------------------------------- the file

    static JSONObject export(Context c) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("app", "ChouiTV");
        root.put("schema", SCHEMA);
        root.put("version", BuildConfig.VERSION_NAME);
        root.put("settings", dumpSettings(c));
        root.put("home", readHome(c));
        return root;
    }

    private static JSONObject dumpSettings(Context c) throws JSONException {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, ?> e : new TreeMap<String, Object>(new Prefs(c).raw().getAll()).entrySet()) {
            Object v = e.getValue();
            if (KEY.matcher(e.getKey()).matches() && !SKIP.matcher(e.getKey()).matches()
                    && (v instanceof Boolean || v instanceof Integer || v instanceof String)) o.put(e.getKey(), v);
        }
        return o;
    }

    private static JSONObject readHome(Context c) {
        try (Cursor cur = c.getContentResolver().query(HOME, null, null, null, null)) {
            if (cur != null && cur.moveToFirst() && cur.getString(0) != null) return new JSONObject(cur.getString(0));
        } catch (RuntimeException | JSONException ignored) {
            // the home app is not installed, or older: the file then carries the screensaver's settings alone
        }
        return new JSONObject();
    }

    /** null when this is a settings file this build can read, else why not. */
    static String validate(JSONObject file) {
        if (file == null) return "not a settings file";
        if (!"ChouiTV".equals(file.optString("app"))) return "not a ChouiTV settings file";
        if (file.optInt("schema", -1) != SCHEMA) return "this file is from another version of the format (schema " + file.opt("schema") + ")";
        JSONObject s = file.optJSONObject("settings");
        if (s == null) return "the file has no settings";
        try {
            for (Iterator<String> it = s.keys(); it.hasNext(); ) {
                String k = it.next();
                Object v = s.get(k);
                if (!KEY.matcher(k).matches() || SKIP.matcher(k).matches()) return "unknown setting " + k;
                if (!(v instanceof Boolean || v instanceof Integer || v instanceof String)) return k + " has a value of a kind that is not a setting";
                if (v instanceof String && ((String) v).length() > 4000) return k + " is too long";
            }
        } catch (JSONException e) {
            return "damaged file";
        }
        return null;
    }

    // ---------------------------------------------------------------- what would change, and doing it

    /** One line per setting that differs, «الاسم: الجديد (كان: القديم)». */
    static List<String> diff(Context c, JSONObject file) {
        List<String> out = new ArrayList<>();
        try {
            JSONObject now = export(c);
            compare("", now.getJSONObject("settings"), file.getJSONObject("settings"), out);
            JSONObject wantHome = file.optJSONObject("home");
            if (wantHome != null && wantHome.length() > 0) {
                JSONObject nowHome = now.optJSONObject("home");
                for (String part : new String[] {"adhan", "dock", "radio"}) {
                    compare(part + ".", nowHome == null || nowHome.optJSONObject(part) == null ? new JSONObject() : nowHome.getJSONObject(part),
                            wantHome.optJSONObject(part) == null ? new JSONObject() : wantHome.getJSONObject(part), out);
                }
            }
        } catch (JSONException ignored) {
            // an unreadable file was refused by validate() before this
        }
        return out;
    }

    private static void compare(String prefix, JSONObject now, JSONObject want, List<String> out) throws JSONException {
        TreeSet<String> keys = new TreeSet<>();
        for (Iterator<String> it = now.keys(); it.hasNext(); ) keys.add(it.next());
        for (Iterator<String> it = want.keys(); it.hasNext(); ) keys.add(it.next());
        for (String k : keys) {
            String a = now.has(k) ? String.valueOf(now.get(k)) : "";
            String b = want.has(k) ? String.valueOf(want.get(k)) : "";
            if (!a.equals(b)) out.add(label(prefix + k) + ": " + (b.isEmpty() ? "الافتراضي" : shorten(b)) + "  (كان " + (a.isEmpty() ? "الافتراضي" : shorten(a)) + ")");
        }
    }

    private static String shorten(String s) { return s.length() > 40 ? s.substring(0, 40) + "…" : s; }

    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        String[][] l = {
            {"cityName", "المدينة"}, {"cityLat", "خط العرض"}, {"cityLon", "خط الطول"}, {"cityTz", "منطقة المدينة"},
            {"c_tz_choice", "المنطقة الزمنية"}, {"c_pm_choice", "طريقة حساب الصلاة"}, {"prayerAdjOn", "تعديل وقت الصلاة"}, {"prayerAdj", "دقائق التعديل"},
            {"teams", "الفرق"}, {"panel", "البطاقة بجانب الساعة"}, {"homePanel", "بطاقات الشاشة الرئيسية"}, {"greeting", "التحية"},
            {"adhanBar", "تنبيه قبل الأذان"}, {"yearStrip", "شريط السنة"}, {"digits", "الأرقام"}, {"national", "المناسبات الوطنية"},
            {"cardStyle", "شكل البطاقة"}, {"corner", "لمسة مغربية"}, {"family", "مدن العائلة"}, {"markets", "الأسواق"}, {"mkStyle", "شكل الأسواق"},
            {"stocks", "الأسهم"}, {"slot", "الخانة السفلية"}, {"photoMinutes", "تبديل الصورة"}, {"autoUpdate", "التحديث التلقائي"},
            {"adhan.adhan_on", "الأذان التلقائي"}, {"adhan.adhan_voice", "المؤذن"}, {"adhan.adhan_volume", "صوت الأذان"},
            {"adhan.adhan_p0", "أذان الفجر"}, {"adhan.adhan_p1", "أذان الظهر"}, {"adhan.adhan_p2", "أذان العصر"},
            {"adhan.adhan_p3", "أذان المغرب"}, {"adhan.adhan_p4", "أذان العشاء"},
            {"radio.countries", "بلدان الإذاعات"}, {"radio.off", "إذاعات مُلغاة"}, {"radio.fav", "مفضلة الإذاعات"},
            {"dock.dock", "ترتيب المفضلة"}, {"dock.hidden", "التطبيقات المخفية"}, {"dock.shown", "التطبيقات المُظهَرة"}, {"dock.names", "أسماء التطبيقات"},
        };
        for (String[] p : l) LABELS.put(p[0], p[1]);
    }

    private static String label(String key) { String l = LABELS.get(key); return l != null ? l : key; }

    /** Checks the file whole, keeps what is there now, then writes. Returns how many settings changed; throws with the reason. */
    static int apply(Context c, JSONObject file) throws JSONException {
        String bad = validate(file);
        if (bad != null) throw new IllegalArgumentException(bad);
        Prefs prefs = new Prefs(c);
        keepBefore(c);
        int n = 0;
        JSONObject home = file.optJSONObject("home");
        if (home != null && home.length() > 0) {   // the home app checks its half whole, and refuses before anything here is touched
            ContentValues v = new ContentValues();
            v.put("json", home.toString());
            int answer;
            try { answer = c.getContentResolver().update(HOME, v, null, null); } catch (RuntimeException e) { answer = -1; }
            if (answer == 0) throw new IllegalArgumentException("the adhan, dock or radio settings of the file are not valid");
            if (answer > 0) n += answer - 1;   // -1: the home app is not there; its half is left out
        }
        JSONObject want = file.getJSONObject("settings"), now = dumpSettings(c);
        android.content.SharedPreferences.Editor ed = prefs.raw().edit();
        for (Iterator<String> it = now.keys(); it.hasNext(); ) {
            String k = it.next();
            if (!want.has(k)) { ed.remove(k); n++; }
        }
        for (Iterator<String> it = want.keys(); it.hasNext(); ) {
            String k = it.next();
            Object v = want.get(k);
            if (v.equals(now.opt(k))) continue;
            n++;
            if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
            else if (v instanceof Integer) ed.putInt(k, (Integer) v);
            else ed.putString(k, (String) v);
        }
        ed.commit();
        return n;
    }

    // ---------------------------------------------------------------- the copy kept before an import, and the undo

    private static File beforeFile(Context c) { return new File(c.getFilesDir(), "settings-before-import.json"); }

    private static void keepBefore(Context c) throws JSONException {
        try (OutputStream o = new FileOutputStream(beforeFile(c))) {
            o.write(export(c).toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // no copy, no undo: the import itself still goes on
        }
    }

    static boolean hasUndo(Context c) { return beforeFile(c).isFile(); }

    static JSONObject readBefore(Context c) {
        try (InputStream in = new FileInputStream(beforeFile(c))) {
            return new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- the file on the TV: Downloads/chouitv-settings.json

    /** Writes the file to the Downloads folder (and a copy in the app's own folder, where `adb pull` finds it); returns where it went, null when nothing could be written. */
    static String save(Context c, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        String where = null;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.content.ContentResolver r = c.getContentResolver();
                Uri uri = findDownload(r);
                if (uri == null) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME);
                    v.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                }
                if (uri != null) {
                    try (OutputStream o = r.openOutputStream(uri, "wt")) { o.write(bytes); }
                    where = "التنزيلات/" + FILE_NAME;
                }
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                try (OutputStream o = new FileOutputStream(new File(dir, FILE_NAME))) { o.write(bytes); }
                where = "التنزيلات/" + FILE_NAME;
            }
        } catch (IOException | RuntimeException ignored) {
            // the folder of the app is tried next
        }
        File own = ownFile(c);
        if (own != null) {
            try (OutputStream o = new FileOutputStream(own)) { o.write(bytes); if (where == null) where = "مجلد التطبيق"; } catch (IOException ignored) { }
        }
        return where;
    }

    /** The file's text: the Downloads copy first, then the folder of the app (where `adb push` can put one); null when there is none. */
    static String load(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.content.ContentResolver r = c.getContentResolver();
                Uri uri = findDownload(r);
                if (uri != null) try (InputStream in = r.openInputStream(uri)) { if (in != null) return new String(readAll(in), StandardCharsets.UTF_8); }
            }
        } catch (IOException | RuntimeException ignored) { }
        File[] tries = {new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FILE_NAME), ownFile(c)};
        for (File f : tries) {
            if (f == null || !f.isFile()) continue;
            try (InputStream in = new FileInputStream(f)) { return new String(readAll(in), StandardCharsets.UTF_8); } catch (IOException | RuntimeException ignored) { }
        }
        return null;
    }

    private static File ownFile(Context c) {
        File dir = c.getExternalFilesDir(null);
        return dir == null ? null : new File(dir, FILE_NAME);
    }

    private static Uri findDownload(android.content.ContentResolver r) {
        if (Build.VERSION.SDK_INT < 29) return null;
        try (Cursor cur = r.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, new String[] {MediaStore.Downloads._ID},
                MediaStore.Downloads.DISPLAY_NAME + "=?", new String[] {FILE_NAME}, null)) {
            if (cur != null && cur.moveToFirst()) return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cur.getLong(0));
        } catch (RuntimeException ignored) { }
        return null;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0 && b.size() < 1_000_000) b.write(buf, 0, n);
        return b.toByteArray();
    }
}

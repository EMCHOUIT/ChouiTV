package ma.chouitv.home.tilawah;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The radios of the countries the person chose (three at most): which countries, which stations stay ticked, the favourites, and the
 * small per-country lists kept on the TV. The lists are made every day by optimize/radio_catalog.py (only streams that answered,
 * family-safe, 120 at most per country) and downloaded on request, never in the background. Morocco's radios ship with the app.
 */
final class RadioStore {
    static final String BASE = "https://github.com/EMCHOUIT/ChouiTV/releases/download/radio/";
    /** The countries offered besides Morocco: the ones radio_catalog.py builds. */
    static final String[] OFFERED = {"dz", "tn", "eg", "sa", "ae", "qa", "kw", "jo", "lb", "iq", "ly", "sd",
            "fr", "be", "es", "de", "gb", "us", "ca", "tr", "it", "nl", "sn"};

    private RadioStore() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("radio", Context.MODE_PRIVATE);
    }

    // ---- the person's choices ----

    static List<String> chosen(Context c) { return RadioChoice.parse(sp(c).getString("countries", "")); }

    static void setChosen(Context c, List<String> countries) {
        sp(c).edit().putString("countries", RadioChoice.join(countries)).apply();
        changed(c);
    }

    /** A station the person unticked in the settings (it stays in the list file, it is only hidden). */
    static boolean off(Context c, String cc, String id) { return sp(c).getStringSet("off", new HashSet<String>()).contains(cc + ":" + id); }

    static void setOff(Context c, String cc, String id, boolean off) {
        Set<String> set = new HashSet<>(sp(c).getStringSet("off", new HashSet<String>()));
        if (off) set.add(cc + ":" + id); else set.remove(cc + ":" + id);
        sp(c).edit().putStringSet("off", set).apply();
        changed(c);
    }

    static boolean favourite(Context c, String url) { return sp(c).getStringSet("fav", new HashSet<String>()).contains(url); }

    static boolean toggleFavourite(Context c, String url) {
        Set<String> set = new HashSet<>(sp(c).getStringSet("fav", new HashSet<String>()));
        boolean now = set.add(url);
        if (!now) set.remove(url);
        sp(c).edit().putStringSet("fav", set).apply();
        return now;
    }

    static long updatedAt(Context c) { return sp(c).getLong("updated", 0); }

    // ---- the lists on the TV ----

    private static File file(Context c, String cc) {
        File dir = new File(c.getApplicationContext().getFilesDir(), "radio");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, cc + ".json");
    }

    /** The stations of one country as the catalogue file holds them (all, ticked or not); empty when the list was never downloaded. */
    static List<Catalog.Station> read(Context c, String cc) {
        List<Catalog.Station> out = new ArrayList<>();
        File f = file(c, cc);
        if (!f.exists()) return out;
        try (InputStream in = new java.io.FileInputStream(f)) {
            JSONArray a = new JSONObject(new String(readAll(in), StandardCharsets.UTF_8)).getJSONArray("stations");
            for (int i = 0; i < a.length(); i++) {
                JSONObject s = a.getJSONObject(i);
                out.add(new Catalog.Station(s.getString("n"), s.getString("u"), true, cc,
                        s.optString("i"), s.optString("c", "music"), s.optString("f"), s.optString("t")));
            }
        } catch (Exception broken) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();   // a half-written file is never kept: the list is asked for again
            out.clear();
        }
        return out;
    }

    static boolean downloaded(Context c, String cc) { return file(c, cc).exists(); }

    /** Adds the ticked stations of the chosen countries (not Morocco: its radios are the bundled ones) to the catalogue. */
    static void appendTo(Catalog catalog, Context c) {
        for (String cc : chosen(c)) {
            if (cc.equals(RadioChoice.HOME)) continue;
            for (Catalog.Station s : read(c, cc)) {
                if (!off(c, cc, s.id)) catalog.stations.add(s);
            }
        }
    }

    /** Reads the lists again and forgets a saved station that no longer means the same radio. */
    static void changed(Context c) {
        Catalog cat = Catalog.get(c);
        cat.reloadRadios(c);
        Prefs prefs = new Prefs(c);
        if (prefs.source() == Prefs.SOURCE_RADIO && prefs.station() >= cat.bundledCount()) prefs.save(Prefs.SOURCE_NONE, "", 1, 0, 0);
    }

    // ---- download ----

    interface Done { void done(boolean ok, int countries); }

    /** A download with its progress, one country (one small file) at a time; every call comes on the main thread. */
    interface Progress {
        int WAITING = 0, LOADING = 1, DONE = 2, FAILED = 3;
        /** a country's state changed; finished = the countries done (well or not) so far, total = the countries to download */
        void step(String cc, int state, int finished, int total);
        /** the end: got = lists downloaded, failed = the countries that could not be (their old list stays), total = how many were asked */
        void done(int got, java.util.List<String> failed, int total);
    }

    /** The chosen countries that have a list to download (Morocco's radios ship with the app). */
    static List<String> toDownload(Context c) {
        List<String> out = new ArrayList<>();
        for (String cc : chosen(c)) if (!cc.equals(RadioChoice.HOME)) out.add(cc);
        return out;
    }

    /** Downloads the lists of the chosen countries; the old ones stay for a country that fails. Off the main thread, answers on it. */
    static void update(final Context context, final Progress progress) {
        final Context c = context.getApplicationContext();
        final List<String> targets = toDownload(c);
        final Handler ui = new Handler(Looper.getMainLooper());
        for (String cc : targets) ui.post(() -> progress.step(cc, Progress.WAITING, 0, targets.size()));
        new Thread(() -> {
            final List<String> failed = new ArrayList<>();
            int finished = 0;
            for (final String cc : targets) {
                final int before = finished;
                ui.post(() -> progress.step(cc, Progress.LOADING, before, targets.size()));
                boolean ok = fetch(c, cc);
                finished++;
                if (!ok) failed.add(cc);
                final int after = finished;
                final boolean good = ok;
                ui.post(() -> progress.step(cc, good ? Progress.DONE : Progress.FAILED, after, targets.size()));
            }
            final int got = targets.size() - failed.size();
            if (got > 0) sp(c).edit().putLong("updated", System.currentTimeMillis()).apply();
            ui.post(() -> {
                if (got > 0) changed(c);
                progress.done(got, failed, targets.size());
            });
        }, "radio-update").start();
    }

    /** The same without the progress (the command line, the list that was never downloaded). */
    static void update(final Context context, final Done done) {
        update(context, new Progress() {
            @Override public void step(String cc, int state, int finished, int total) { }
            @Override public void done(int got, List<String> failed, int total) { if (done != null) done.done(failed.isEmpty() && total > 0, got); }
        });
    }

    private static final String[] DAYS = {"الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"};
    private static final String[] MONTHS = {"يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"};

    /** «الخميس 29 أكتوبر 2026، 12:35 ص» (the digits are the screen's own setting, applied by Ui.digits where it is shown). */
    static String stamp(long at) {
        java.util.Calendar k = java.util.Calendar.getInstance();
        k.setTimeInMillis(at);
        int h = k.get(java.util.Calendar.HOUR);
        return DAYS[k.get(java.util.Calendar.DAY_OF_WEEK) - 1] + " " + k.get(java.util.Calendar.DAY_OF_MONTH) + " " + MONTHS[k.get(java.util.Calendar.MONTH)]
                + " " + k.get(java.util.Calendar.YEAR) + "، " + (h == 0 ? 12 : h) + ":" + String.format(Locale.US, "%02d", k.get(java.util.Calendar.MINUTE))
                + " " + (k.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM ? "ص" : "م");
    }

    /** How many radios the downloaded lists of the chosen countries hold (for «١٦٥ إذاعة»). */
    static int downloadedStations(Context c) {
        int n = 0;
        for (String cc : toDownload(c)) n += read(c, cc).size();
        return n;
    }

    private static boolean fetch(Context c, String cc) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(BASE + cc + ".json").openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            if (conn.getResponseCode() != 200) return false;
            byte[] body;
            try (InputStream in = conn.getInputStream()) { body = readAll(in); }
            new JSONObject(new String(body, StandardCharsets.UTF_8)).getJSONArray("stations");   // it must be a list before it replaces the old one
            File tmp = new File(file(c, cc).getPath() + ".part");
            try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(body); }
            return tmp.renameTo(file(c, cc)) || (file(c, cc).delete() && tmp.renameTo(file(c, cc)));
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    // ---- names ----

    /** «المغرب», «فرنسا»… in Arabic whatever the TV's language. */
    static String countryName(String cc) {
        switch (cc.toLowerCase(Locale.ROOT)) {   // the long official names, in the short form people say
            case "sa": return "السعودية";
            case "ae": return "الإمارات";
            case "gb": return "بريطانيا";
            case "us": return "أمريكا";
            default:
        }
        String name = new Locale("", cc.toUpperCase(Locale.ROOT)).getDisplayCountry(new Locale("ar"));
        return name == null || name.isEmpty() ? cc.toUpperCase(Locale.ROOT) : name;
    }

    /** The sections of the catalogue, in the order they are listed. */
    static String sectionName(String section) {
        switch (section) {
            case "news": return "أخبار";
            case "culture": return "ثقافة";
            case "sport": return "رياضة";
            case "religion": return "دين";
            default: return "موسيقى";
        }
    }
}

package ma.chouitv.home.tilawah;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The reciters, the live streams and the 114 surah names, read once from the bundled JSON. */
final class Catalog {

    static final class Reciter {
        final String id, name, server;
        final int surahs;
        Reciter(String id, String name, String server, int surahs) {
            this.id = id; this.name = name; this.server = server; this.surahs = surahs;
        }
    }

    static final class Station {
        final String name, url, group;
        final boolean live;
        /** Only for the radios of the catalogue the person downloaded: its id, section (news, music…), format and place. */
        final String id, section, kind, place;
        Station(String name, String url, boolean live, String group) { this(name, url, live, group, "", "", "", ""); }
        Station(String name, String url, boolean live, String group, String id, String section, String kind, String place) {
            this.name = name; this.url = url; this.live = live; this.group = group;
            this.id = id; this.section = section; this.kind = kind; this.place = place;
        }
    }

    final List<Reciter> reciters = new ArrayList<>();
    /**
     * The bundled stations first (their index never moves), then the chosen countries' radios. The list is never changed in place: a new one
     * replaces it in one step (reloadRadios), so a screen reading it never sees a half-built list.
     */
    volatile List<Station> stations = new ArrayList<>();
    private int bundled;
    final String[] surahNames = new String[115];

    private static Catalog cached;

    static synchronized Catalog get(Context context) {
        if (cached == null) cached = load(context.getApplicationContext());
        return cached;
    }

    private static Catalog load(Context c) {
        Catalog catalog = new Catalog();
        try {
            JSONArray reciters = new JSONObject(read(c, "quran/reciters.json")).getJSONArray("reciters");
            for (int i = 0; i < reciters.length(); i++) {
                JSONObject r = reciters.getJSONObject(i);
                catalog.reciters.add(new Reciter(r.getString("id"), r.getString("name"),
                        r.getString("server"), r.getInt("surahs")));
            }
            JSONArray stations = new JSONObject(read(c, "quran/stations.json")).getJSONArray("stations");
            List<Station> all = new ArrayList<>();
            for (int i = 0; i < stations.length(); i++) {
                JSONObject s = stations.getJSONObject(i);
                all.add(new Station(s.getString("name"), s.getString("url"), s.optBoolean("live", true), s.optString("group", "quran")));
            }
            catalog.bundled = all.size();
            RadioStore.appendTo(all, c);
            catalog.stations = Collections.unmodifiableList(all);
            JSONArray surahs = new JSONObject(read(c, "quran/surahs.json")).getJSONArray("surahs");
            for (int i = 0; i < surahs.length(); i++) {
                JSONObject s = surahs.getJSONObject(i);
                int n = s.getInt("n");
                if (n >= 1 && n <= 114) catalog.surahNames[n] = s.getString("name");
            }
        } catch (Exception e) {
            throw new IllegalStateException("quran assets are missing or broken", e);
        }
        return catalog;
    }

    private static String read(Context c, String path) throws Exception {
        try (InputStream in = c.getAssets().open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** After the person changed the countries or the lists were updated: the downloaded radios are read again, the bundled ones stay as they are. */
    synchronized void reloadRadios(Context c) {
        List<Station> all = new ArrayList<>(stations.subList(0, bundled));
        RadioStore.appendTo(all, c.getApplicationContext());
        stations = Collections.unmodifiableList(all);
    }

    int bundledCount() { return bundled; }

    String surahName(int n) {
        return n >= 1 && n <= 114 && surahNames[n] != null ? surahNames[n] : String.valueOf(n);
    }

    int reciterIndex(String id) {
        for (int i = 0; i < reciters.size(); i++) if (reciters.get(i).id.equals(id)) return i;
        return -1;
    }

    /** Where a stream is in the list now, or -1: a station is remembered by its stream, because its place moves when the countries change. */
    int stationIndexByUrl(String url) {
        List<String> urls = new ArrayList<>();
        for (Station s : stations) urls.add(s.url);
        return StationNav.indexOfUrl(urls, url);
    }

    int stationIndex(String name) {
        for (int i = 0; i < stations.size(); i++) if (stations.get(i).name.equals(name)) return i;
        return -1;
    }
}

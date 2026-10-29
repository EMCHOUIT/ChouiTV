package ma.chouitv.home.tilawah;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * What the settings file may say about the radios, checked whole before anything is written. No Android here, so a test can run it.
 * The file holds three texts: «countries» (ma,fr,sa: three at most, only the offered ones), «off» (the stations the person unticked,
 * «fr:1a2b3c4d» separated by commas) and «fav» (the favourite stations' streams, one per line, https only).
 */
final class RadioBackupRules {
    static final int MAX_OFF = 1200, MAX_FAV = 300, MAX_URL = 400;
    private static final Pattern OFF = Pattern.compile("^[a-z]{2}:[A-Za-z0-9]{0,16}$");

    private RadioBackupRules() {}

    /** null when the three texts are fine, else why not. offered = the countries the app offers (Morocco included). */
    static String check(List<String> offered, String countries, String off, String fav) {
        if (countries != null && !countries.isEmpty()) {
            String[] parts = countries.split(",");
            if (parts.length > RadioChoice.MAX) return "radio countries: " + RadioChoice.MAX + " at most";
            for (String cc : parts) if (!offered.contains(cc.trim().toLowerCase(java.util.Locale.ROOT))) return "radio countries: " + cc + " is not offered";
        }
        if (off != null && !off.isEmpty()) {
            String[] parts = off.split(",");
            if (parts.length > MAX_OFF) return "radio off: too many";
            for (String s : parts) if (!OFF.matcher(s).matches()) return "radio off: " + s + " is not a station";
        }
        if (fav != null && !fav.isEmpty()) {
            String[] lines = fav.split("\n");
            if (lines.length > MAX_FAV) return "radio fav: too many";
            for (String u : lines) if (!u.startsWith("https://") || u.length() > MAX_URL || u.contains(" ")) return "radio fav: not a stream";
        }
        return null;
    }

    /** «off»/«fav» as the lists they stand for (blank = none). */
    static List<String> split(String text, String sep) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        out.addAll(Arrays.asList(text.split(sep)));
        return out;
    }
}

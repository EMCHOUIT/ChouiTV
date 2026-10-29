package ma.chouitv.home.tilawah;

import java.util.ArrayList;
import java.util.List;

/** Which countries' radios the person keeps: at most MAX, Morocco (the radios chosen by hand) first by default. No Android here, so a test can run it. */
final class RadioChoice {
    static final int MAX = 2;
    static final String HOME = "ma";

    private RadioChoice() {}

    /** «ma,fr» → [ma, fr]; blanks, repeats and anything past MAX are dropped; nothing at all means Morocco. */
    static List<String> parse(String csv) {
        List<String> out = new ArrayList<>();
        if (csv != null) {
            for (String part : csv.split(",")) {
                String cc = part.trim().toLowerCase(java.util.Locale.ROOT);
                if (cc.length() == 2 && !out.contains(cc) && out.size() < MAX) out.add(cc);
            }
        }
        if (out.isEmpty()) out.add(HOME);
        return out;
    }

    static String join(List<String> chosen) {
        StringBuilder sb = new StringBuilder();
        for (String cc : chosen) sb.append(sb.length() == 0 ? "" : ",").append(cc);
        return sb.toString();
    }

    /** The list after the person ticks or unticks a country; null when it is ticked and the list is full (two already), and when it would leave none. */
    static List<String> toggle(List<String> chosen, String cc) {
        List<String> out = new ArrayList<>(chosen);
        if (out.remove(cc)) return out.isEmpty() ? null : out;
        if (out.size() >= MAX) return null;
        out.add(cc);
        return out;
    }

    static boolean full(List<String> chosen) { return chosen.size() >= MAX; }
}

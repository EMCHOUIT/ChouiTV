package ma.chouitv.home.tilawah;

import java.util.List;

/**
 * Which station comes next or before, and where a remembered station is now. No Android here, so tests/station_nav_test.py can run it.
 * The list of stations changes (the countries chosen, the stations unticked, a list downloaded again), so a station is remembered by its stream URL,
 * never by its place in the list: a place may now be another radio.
 */
final class StationNav {
    private StationNav() {}

    /**
     * The station after (step +1) or before (step -1) `from` in the same group, going round; `from` itself when it is alone in its group, and -1 when
     * `from` is not a station of the list. ◀ ▶ of a radio stay among the radios of its own country (or of the Quran radios), never crossing into another.
     */
    static int neighbour(List<String> groups, int from, int step) {
        if (from < 0 || from >= groups.size()) return -1;
        String group = groups.get(from);
        int n = groups.size();
        for (int k = 1; k <= n; k++) {
            int i = ((from + step * k) % n + n) % n;
            if (groups.get(i).equals(group)) return i;
        }
        return from;
    }

    /** The place of a stream in the list, or -1 (a station that is not in the list any more, or never was remembered). */
    static int indexOfUrl(List<String> urls, String url) {
        if (url == null || url.isEmpty()) return -1;
        for (int i = 0; i < urls.size(); i++) if (urls.get(i).equals(url)) return i;
        return -1;
    }
}

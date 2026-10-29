package ma.chouitv.screensaver;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The decisions of Migration (4.0, the two apps became one) in plain Java, so a JVM runs them: tests/migration_test.py.
 * Nothing here touches Android; Migration asks the system and acts on the answers.
 */
final class MigrationCore {
    private MigrationCore() { }

    /** the screensaver app of 3.184 and before, which stays installed on a family TV after the update */
    static final String OLD_PACKAGE = "ma.chouitv.screensaver";
    static final int MAX_PHOTOS = 10;

    enum Go { ALREADY_DONE, NO_OLD_APP, UNTRUSTED, RUN }

    /** Whether to move anything: once only; nothing to move without the old app; nothing from an app signed by someone else. */
    static Go decide(boolean flagSet, boolean oldInstalled, boolean sameCertificate) {
        if (flagSet) return Go.ALREADY_DONE;
        if (!oldInstalled) return Go.NO_OLD_APP;
        if (!sameCertificate) return Go.UNTRUSTED;
        return Go.RUN;
    }

    /** How many photos to copy: the old app offers `offered`; nothing is added to photos that are here already (a second run never doubles them). */
    static int photosToCopy(int offered, int alreadyHere) {
        if (alreadyHere > 0 || offered <= 0) return 0;
        return Math.min(offered, MAX_PHOTOS);
    }

    /** Positions (0-based) of `incoming` to add, in order: not already among `have`, not twice, and no more than `room` (the list holds 12). */
    static List<Integer> tasksToAdd(List<String> have, List<String> incoming, int room) {
        List<String> seen = new ArrayList<>(have);
        List<Integer> out = new ArrayList<>();
        for (int k = 0; k < incoming.size() && out.size() < room; k++) {
            String t = incoming.get(k);
            if (t == null || t.isEmpty() || seen.contains(t)) continue;
            seen.add(t);
            out.add(k);
        }
        return out;
    }

    /** The package of the screensaver in screensaver_components ("pkg/cls" or several joined by commas); "" for none. */
    static String dreamOwner(String components) {
        if (components == null) return "";
        String first = components.split(",", 2)[0].trim();
        int slash = first.indexOf('/');
        return slash > 0 ? first.substring(0, slash) : "";
    }

    /**
     * The move is over: the old app is gone and, when the TV lets an app read which screensaver is chosen (components != null), it is one of ours.
     * When it cannot be read, the old app being gone is all that can be known.
     */
    static boolean moveFinished(boolean oldInstalled, String components, String ourPackage) {
        if (oldInstalled) return false;
        return components == null || dreamOwner(components).equals(ourPackage);
    }

    /** The settings the old app's page fragment says, as the keys of the settings file; only what can be read from it for certain. */
    static Map<String, Object> fallbackSettings(String fragment, String homePanels, String greeting, String adhanBar) {
        Map<String, String> f = parse(fragment);
        Map<String, Object> out = new LinkedHashMap<>();
        String city = f.get("city"), lat = f.get("lat"), lon = f.get("lon");
        if (city != null && !city.isEmpty() && lat != null && lat.matches("-?\\d{1,2}(\\.\\d+)?") && lon != null && lon.matches("-?\\d{1,3}(\\.\\d+)?")) {
            out.put("cityName", city);
            out.put("cityLat", lat);
            out.put("cityLon", lon);
            String tz = f.get("tz");
            if (tz != null && tz.matches("[A-Za-z_]+(/[A-Za-z_+\\-0-9]+){1,2}")) out.put("cityTz", tz);
        }
        copy(f, "panel", out, "panel", "[a-z0-9]{2,16}");
        copy(f, "digits", out, "digits", "[a-z0-9]{2,16}");
        copy(f, "cardstyle", out, "cardStyle", "[a-z0-9]{2,16}");
        copy(f, "photos", out, "photoMinutes", "[0-9]{1,3}");
        if (homePanels != null && homePanels.matches("[a-z0-9]{2,16}(,[a-z0-9]{2,16}){0,2}")) out.put("homePanel", homePanels);
        if ("1".equals(greeting) || "0".equals(greeting)) out.put("greeting", "1".equals(greeting));
        if ("1".equals(adhanBar) || "0".equals(adhanBar)) out.put("adhanBar", "1".equals(adhanBar));
        if ("1".equals(f.get("yearstrip")) || "0".equals(f.get("yearstrip"))) out.put("yearStrip", "1".equals(f.get("yearstrip")));
        return out;
    }

    private static void copy(Map<String, String> from, String key, Map<String, Object> to, String as, String shape) {
        String v = from.get(key);
        if (v != null && v.matches(shape)) to.put(as, v);
    }

    static Map<String, String> parse(String fragment) {
        Map<String, String> out = new LinkedHashMap<>();
        if (fragment == null) return out;
        for (String pair : fragment.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            try {
                out.put(pair.substring(0, eq), URLDecoder.decode(pair.substring(eq + 1).replace("+", "%2B"), "UTF-8"));
            } catch (UnsupportedEncodingException | IllegalArgumentException e) {
                // one damaged pair is left out
            }
        }
        return out;
    }

    /** One line for `saver.sh get`: where the settings came from and how much was brought over. */
    static String outcome(String source, int settingsChanged, int tasksDone, int tasksOffered, int photosDone, int photosOffered, String problem) {
        String s = source + ": settings " + settingsChanged + ", tasks " + tasksDone + "/" + tasksOffered + ", photos " + photosDone + "/" + photosOffered;
        return problem == null || problem.isEmpty() ? s : s + "; problem: " + problem;
    }
}

package ma.chouitv.home;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What an update has to do, decided from numbers only (no Android here, so tests/update_planner_test.py can run it). The apps come as a pair
 * and talk to each other, so «needs an update» looks at BOTH installed versions, not only this app's: a TV left with the screensaver at 176 and
 * the home screen at 175 is still behind. The home app goes last, because installing it replaces the process that is doing the update.
 */
final class UpdatePlanner {
    static final String HOME = "ma.chouitv.home";
    /** a technical failure (no space, a bad file) is tried again after this; a person who said no is left alone for RETRY_AFTER_DECLINE_MS */
    static final long RETRY_AFTER_FAILURE_MS = 30 * 60_000L, RETRY_AFTER_DECLINE_MS = 24L * 3600 * 1000;

    private UpdatePlanner() {}

    /**
     * The packages to install, in order: every listed package installed below `latest` (or missing: -1), the home app last.
     * installed: package → versionCode, -1 when it is not on the TV.
     */
    static List<String> plan(long latest, List<String> listed, Map<String, Long> installed) {
        List<String> out = new ArrayList<>();
        boolean home = false;
        for (String pkg : listed) {
            Long have = installed.get(pkg);
            if (have != null && have >= latest) continue;
            if (HOME.equals(pkg)) home = true; else if (!out.contains(pkg)) out.add(pkg);
        }
        if (home) out.add(HOME);
        return out;
    }

    /** true when one app of the pair is at `latest` or more and another is behind: the mixed state the update must repair */
    static boolean mixed(long latest, List<String> listed, Map<String, Long> installed) {
        boolean ahead = false, behind = false;
        for (String pkg : listed) {
            Long have = installed.get(pkg);
            if (have == null || have < latest) behind = true; else ahead = true;
        }
        return ahead && behind;
    }

    /**
     * Whether an automatic check must wait. A person who refused version N is left alone for a day (a forced check ignores it); a technical
     * failure only waits 30 minutes. declinedVersion / failedAt: 0 when there was none.
     */
    static boolean mustWait(long now, boolean force, int latest, int declinedVersion, long declinedAt, long failedAt) {
        if (force) return false;
        if (declinedVersion == latest && now - declinedAt < RETRY_AFTER_DECLINE_MS) return true;
        return failedAt > 0 && now - failedAt < RETRY_AFTER_FAILURE_MS;
    }

    /** an install left «in progress» is forgotten after ten minutes, or at once when the person asks again */
    static boolean staleInstall(long now, boolean force, long startedAt) {
        return force || now - startedAt >= 10 * 60_000L;
    }
}

package ma.chouitv.home;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What an update has to do, decided from numbers only (no Android here, so tests/update_planner_test.py can run it). Since 4.0 there is ONE package,
 * ma.chouitv.home (the home screen, the screensaver and its settings all live in it). latest.json lists only that package; an old manifest that
 * also lists ma.chouitv.screensaver is tolerated, but that entry is ignored: a 4.0 app must never install the old screensaver again.
 */
final class UpdatePlanner {
    static final String HOME = "ma.chouitv.home";
    /** a technical failure (no space, a bad file) is tried again after this; a person who said no is left alone for RETRY_AFTER_DECLINE_MS */
    static final long RETRY_AFTER_FAILURE_MS = 30 * 60_000L, RETRY_AFTER_DECLINE_MS = 24L * 3600 * 1000;

    private UpdatePlanner() {}

    /**
     * The packages to install: [HOME] when it is listed and installed below latest, else empty. Any other listed package is ignored.
     * installed: package to versionCode, -1 when it is not on the TV.
     */
    static List<String> plan(long latest, List<String> listed, Map<String, Long> installed) {
        List<String> out = new ArrayList<>();
        if (!listed.contains(HOME)) return out;
        Long have = installed.get(HOME);
        if (have == null || have < latest) out.add(HOME);
        return out;
    }

    /**
     * Whether a downloaded APK may be installed: null, or why not. pkg/apkVersion: what the file says it is; announced: latest.json's versionCode;
     * installed: our own installed versionCode; sameSigner: its signing key is the one of the installed app.
     */
    static String refusal(String pkg, long apkVersion, long announced, long installed, boolean sameSigner) {
        if (!HOME.equals(pkg)) return "package";
        if (apkVersion < announced) return "older-than-announced";
        if (apkVersion <= installed) return "not-newer";
        if (!sameSigner) return "signer";
        return null;
    }

    /** the signing certificates of the file are those of the installed app, and there is at least one */
    static boolean sameSigner(Set<String> got, Set<String> mine) {
        return !got.isEmpty() && got.equals(mine);
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

    /** an install left in progress is forgotten after ten minutes, or at once when the person asks again */
    static boolean staleInstall(long now, boolean force, long startedAt) {
        return force || now - startedAt >= 10 * 60_000L;
    }

    /** the plan entries still to do: those not yet at the target version (this app replaced by the new one finds its own entry done) */
    static List<String> remaining(List<String> plan, long target, Map<String, Long> installed) {
        List<String> out = new ArrayList<>();
        for (String pkg : plan) {
            Long have = installed.get(pkg);
            if (have == null || have < target) out.add(pkg);
        }
        return out;
    }
}

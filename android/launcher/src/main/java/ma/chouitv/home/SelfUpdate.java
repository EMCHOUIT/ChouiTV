package ma.chouitv.home;

import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.app.PendingIntent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Self-update of the ONE ChouiTV app (package ma.chouitv.home since 4.0) from the owner's repository (github.com/EMCHOUIT/ChouiTV). latest.json
 * there (signed: ManifestTrust) names the newest version and its APK with its SHA-256. When the home screen comes to the front (at most every
 * 6 hours) and this app is behind that version, its APK is downloaded into the cache and checked (hash, package name, version announced and newer
 * than ours, the SAME signing key as OUR installed signature). Then UpdateActivity, which is in the front, drives the install: Android shows its
 * «install?» screen to an app that is in the front, and refuses it to one in the background, which is why the install is not started from a
 * receiver. Installing ourselves ends this process; the new process finds the plan entry already at the target version and drops it.
 * An old-format manifest that also lists ma.chouitv.screensaver is tolerated: that entry is ignored (the old screensaver is never reinstalled).
 *
 * Entry point for the settings screen (same process, same module; Java package ma.chouitv.screensaver):
 *   public static void SelfUpdate.checkNow(android.content.Context ctx, boolean dry, SelfUpdate.Callback cb)
 *   public interface SelfUpdate.Callback { void say(String message); }
 * It forces the check (the 6 h wait and a recent refusal are ignored). dry = only says «available N» / «up to date …» and installs nothing.
 * cb (may be null) is called ONCE, on a background thread (post to the UI thread), with: "available N", "up to date …", "needs permission: install
 * unknown apps", "installing version N", "resumed", "busy", "an install is in progress", "checked recently", "version N was declined or failed
 * lately", or "error: …". There is no "update: " prefix.
 *
 * Android does not let an ordinary app install silently, and needs «install unknown apps» for this one:
 * adb shell appops set ma.chouitv.home REQUEST_INSTALL_PACKAGES allow
 */
public final class SelfUpdate {
    private static final String TAG = "SelfUpdate";
    static final String MANIFEST = "https://raw.githubusercontent.com/EMCHOUIT/ChouiTV/main/latest.json";
    private static final String APK_HOST = "https://github.com/EMCHOUIT/ChouiTV/releases/download/";   // the only place an APK may come from
    static final long CHECK_EVERY_MS = 6L * 3600 * 1000;
    static final String ACTION_RESULT = "ma.chouitv.home.INSTALL_RESULT";
    private static final String DIR = "update";
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private SelfUpdate() {}

    interface Done { void say(String message); }

    /** the answer of {@link #checkNow}: one message, on a background thread */
    public interface Callback { void say(String message); }

    /** The settings screen's button (same process): a forced check; dry = only says whether a newer version exists. See the comment at the top. */
    public static void checkNow(Context ctx, boolean dry, final Callback cb) {
        maybeStart(ctx, true, dry, cb == null ? null : new Done() { @Override public void say(String message) { cb.say(message); } });
    }

    private static SharedPreferences prefs(Context c) { return Dock.prefs(c); }

    private static File dir(Context c) { return new File(c.getCacheDir(), DIR); }

    private static long versionCode(Context c, String pkg) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(pkg, 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) { return -1; }
    }

    /** In the background: check, and if one of the apps is behind, download, verify and start the installs. Safe to call often. */
    static void maybeStart(final Context ctx, final boolean force, final Done done) { maybeStart(ctx, force, false, done); }

    /** dry: only says whether a newer version exists ("available N"), downloads and installs nothing, and touches nothing of an update in progress. */
    static void maybeStart(final Context ctx, final boolean force, final boolean dry, final Done done) {
        final Context c = ctx.getApplicationContext();
        if (!BUSY.compareAndSet(false, true)) { if (done != null) done.say("busy"); return; }
        new Thread(() -> {
            String message;
            try { message = run(c, force, dry); }
            catch (Throwable e) { Log.w(TAG, "check failed", e); message = "error: " + e; }   // an Error too: BUSY must be released
            finally { BUSY.set(false); }
            if (done != null) done.say(message);
        }, "self-update").start();
    }

    /** What the last attempt left behind, for `UPDATE_CHECK --ez status true` (the cause of a failure is not shown to the person, only kept here). */
    static String status(Context c) {
        SharedPreferences p = prefs(c);
        return "plan=" + p.getString("updPlan", "") + " target=" + p.getInt("updTarget", 0) + " why=" + p.getString("updWhy", "")
                + " declined=" + p.getInt("updDeclined", 0) + " failedAt=" + p.getLong("updFailedAt", 0);
    }

    private static String run(Context c, boolean force, boolean dry) throws Exception {
        long now = System.currentTimeMillis();
        SharedPreferences p = prefs(c);
        String planLeft = p.getString("updPlan", "");
        if (!planLeft.isEmpty()) {
            // a package of the plan that is on the TV at the target version is done, whatever the installer's answer said: this app itself is replaced
            // by the new version before it can hear that its own install succeeded, so its entry is still there in the new process
            List<String> all = planList(p);
            Map<String, Long> have = new HashMap<>();
            for (String pkg : all) have.put(pkg, versionCode(c, pkg));
            List<String> left = UpdatePlanner.remaining(all, p.getInt("updTarget", 0), have);
            if (left.isEmpty()) { if (!dry) finished(c); planLeft = ""; }
            else if (!dry) p.edit().putString("updPlan", android.text.TextUtils.join("|", left)).apply();
        }
        if (!planLeft.isEmpty()) {
            // an update half-way: the installs resume in front of the person; an install that never ended is forgotten after ten minutes
            if (!UpdatePlanner.staleInstall(now, force && !dry, p.getLong("updStartedAt", 0))) {
                if (!dry && UpdateActivity.live == null) { String ui = launchUi(c); if (ui != null) return "error: " + ui; return "resumed"; }
                return "an install is in progress";
            }
            if (dry) return "an install is in progress";
            abandonCurrent(c);
            clearPlan(c);
        }
        if (!dry) deleteAll(dir(c));
        if (!force && now - p.getLong("updCheck", 0) < CHECK_EVERY_MS) return "checked recently";
        p.edit().putLong("updCheck", now).apply();
        // the file and its signature are asked for with the same minute; the TV believes the file only if the owner signed exactly these bytes
        byte[] file = download(MANIFEST + "?t=" + (now / 60_000L));   // the minute: a cached copy is not kept for long
        String signature = new String(download(MANIFEST + ".sig?t=" + (now / 60_000L)), "UTF-8");
        if (!ManifestTrust.verify(file, signature)) {
            // right after a release the file and its signature can come from two different moments (GitHub's copies update one after the other): ask again in 30 minutes, not in 6 hours
            p.edit().putLong("updCheck", now - CHECK_EVERY_MS + 30 * 60_000L).apply();
            return "error: the version file is not signed by ChouiTV (" + file.length + " + " + signature.trim().length() + " bytes" + (ManifestTrust.lastError != null ? ", " + ManifestTrust.lastError : ", no match") + ")";
        }
        JSONObject m = new JSONObject(new String(file, "UTF-8"));
        int latest = m.getInt("versionCode");
        JSONArray apks = m.getJSONArray("apks");
        // one package since 4.0. Any other entry (the old screensaver of a 3.x manifest, or an unknown name) is ignored, never installed.
        List<String> listed = new ArrayList<>();
        Map<String, Long> installed = new HashMap<>();
        installed.put(UpdatePlanner.HOME, versionCode(c, UpdatePlanner.HOME));
        for (int i = 0; i < apks.length(); i++) {
            String pkg = apks.getJSONObject(i).getString("package");
            if (UpdatePlanner.HOME.equals(pkg) && !listed.contains(pkg)) listed.add(pkg);
        }
        List<String> plan = UpdatePlanner.plan(latest, listed, installed);
        if (plan.isEmpty()) return "up to date (installed " + installed + ", latest " + latest + ")";
        if (UpdatePlanner.mustWait(now, force, latest, p.getInt("updDeclined", 0), p.getLong("updDeclinedAt", 0), p.getLong("updFailedAt", 0)))
            return "version " + latest + " was declined or failed lately";
        // Android installs for an app only when the person allowed «install unknown apps» for it; without that the installer refuses at the last
        // step, after the download. The settings screen that asks the person is opened by whoever is in front (the screensaver's settings).
        if (Build.VERSION.SDK_INT >= 26 && !c.getPackageManager().canRequestPackageInstalls()) return "needs permission: install unknown apps";
        if (dry) return "available " + latest;
        File d = dir(c);
        if (!d.isDirectory() && !d.mkdirs()) return fail(c, "no cache folder");
        for (int i = 0; i < apks.length(); i++) {
            JSONObject a = apks.getJSONObject(i);
            String pkg = a.getString("package");
            if (!plan.contains(pkg)) continue;   // not our package, or already on the new version: not downloaded
            String url = a.getString("url");
            if (!url.startsWith(APK_HOST)) return fail(c, "the APK of " + pkg + " is not on the project's releases: " + url);
            File f = new File(d, pkg + ".apk");
            String sha = fetchTo(url, f);
            if (!sha.equalsIgnoreCase(a.getString("sha256"))) { deleteAll(d); return fail(c, "checksum of " + pkg + " does not match"); }
            String bad = verify(c, f, pkg, latest);
            if (bad != null) { deleteAll(d); return fail(c, bad); }
        }
        p.edit().putString("updPlan", android.text.TextUtils.join("|", plan)).putInt("updTarget", latest).putLong("updStartedAt", now)
                .putString("updWhy", "").putLong("updFailedAt", 0).apply();
        toast(c, "تحديث تلفزيونك " + latest + ": في شاشة التثبيت حرّك الريموت يمينًا إلى «تثبيت» ثم اضغط OK");
        String ui = launchUi(c);
        if (ui != null) { clearPlan(c); deleteAll(d); return fail(c, ui); }
        return "installing version " + latest;
    }

    /** a technical failure: remembered (with its cause), tried again in half an hour, never shown as a refusal by the person */
    private static String fail(Context c, String why) {
        Log.w(TAG, why);
        prefs(c).edit().putString("updWhy", why).putLong("updFailedAt", System.currentTimeMillis()).apply();
        return "error: " + why;
    }

    /**
     * Opens the screen that drives the installs, and waits for it to be there. Android refuses to start an activity for an app that is in the
     * background; null when it opened, else why not (the update then waits for the next time the home screen is in front).
     */
    private static String launchUi(Context c) {
        try {
            c.startActivity(new Intent(c, UpdateActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (RuntimeException e) { return "cannot open the update screen: " + e; }
        for (int i = 0; i < 40; i++) {
            if (UpdateActivity.live != null) return null;
            try { Thread.sleep(150); } catch (InterruptedException e) { return "interrupted"; }
        }
        return "the update screen did not open (the home screen is not in front)";
    }

    /** the file is our package, the version announced, newer than the installed one, and signed with OUR installed signature (the decision: UpdatePlanner.refusal) */
    private static String verify(Context c, File f, String pkg, int latest) {
        PackageManager pm = c.getPackageManager();
        if (Build.VERSION.SDK_INT < 28) return "this Android is too old to check the signature";
        PackageInfo mine, got;
        try {
            got = pm.getPackageArchiveInfo(f.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
            mine = pm.getPackageInfo(UpdatePlanner.HOME, PackageManager.GET_SIGNING_CERTIFICATES);
        } catch (Exception e) { return pkg + " cannot be read (" + e + ")"; }
        if (got == null || mine == null) return pkg + " cannot be read";
        if (got.signingInfo == null || mine.signingInfo == null) return "no signature to compare for " + pkg;
        boolean same = UpdatePlanner.sameSigner(certs(got.signingInfo.getApkContentsSigners()), certs(mine.signingInfo.getApkContentsSigners()));
        String why = UpdatePlanner.refusal(got.packageName, got.getLongVersionCode(), latest, mine.getLongVersionCode(), same);
        if (why == null) return null;
        switch (why) {
            case "package": return "not the package " + pkg;
            case "older-than-announced": return pkg + " is older than the version announced";
            case "not-newer": return pkg + " is not newer than the installed version";
            default: return pkg + " is signed with another key";
        }
    }

    private static Set<String> certs(Signature[] sigs) {
        Set<String> out = new HashSet<>();
        for (Signature s : sigs) out.add(Arrays.toString(s.toByteArray()));
        return out;
    }

    // ---- the installs, one package at a time (called by UpdateActivity, off the main thread) ----

    /** The first package of the plan goes into an installer session; its answer comes to InstallResultReceiver, which hands it to UpdateActivity. */
    static void installNext(Context c) {
        SharedPreferences p = prefs(c);
        List<String> plan = planList(p);
        if (plan.isEmpty()) { finished(c); return; }
        abandonCurrent(c);
        String pkg = plan.get(0);
        File f = new File(dir(c), pkg + ".apk");
        if (!f.isFile()) { failed(c, "the file of " + pkg + " is gone", false); return; }
        int id = -1;
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        try {
            PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            sp.setAppPackageName(pkg);
            id = pi.createSession(sp);
            p.edit().putInt("updSession", id).putString("updCurrent", pkg).apply();
            try (PackageInstaller.Session s = pi.openSession(id)) {
                try (InputStream in = new FileInputStream(f); OutputStream out = s.openWrite("apk", 0, f.length())) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    s.fsync(out);
                }
                Intent i = new Intent(c, InstallResultReceiver.class).setAction(ACTION_RESULT).putExtra("pkg", pkg);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                IntentSender sender = PendingIntent.getBroadcast(c, id, i, flags).getIntentSender();
                s.commit(sender);
            }
        } catch (Exception e) {
            if (id >= 0) { try { pi.abandonSession(id); } catch (RuntimeException ignored) { } }   // a failed session must not pile up in the system
            failed(c, "install " + pkg + ": " + e, false);
        }
    }

    /** the package that was being installed is done: the plan moves on; true when nothing is left */
    static boolean advance(Context c) {
        SharedPreferences p = prefs(c);
        List<String> plan = planList(p);
        if (!plan.isEmpty()) plan.remove(0);
        p.edit().putString("updPlan", android.text.TextUtils.join("|", plan)).putInt("updSession", -1).apply();
        if (plan.isEmpty()) { finished(c); return true; }
        return false;
    }

    private static List<String> planList(SharedPreferences p) {
        String q = p.getString("updPlan", "");
        return q.isEmpty() ? new ArrayList<String>() : new ArrayList<>(Arrays.asList(q.split("\\|")));
    }

    private static void clearPlan(Context c) { prefs(c).edit().putString("updPlan", "").putInt("updSession", -1).apply(); }

    private static void abandonCurrent(Context c) {
        int id = prefs(c).getInt("updSession", -1);
        if (id < 0) return;
        try { c.getPackageManager().getPackageInstaller().abandonSession(id); } catch (RuntimeException ignored) { }
        prefs(c).edit().putInt("updSession", -1).apply();
    }

    /**
     * The update stopped. userDeclined: the person said no at Android's screen: nothing is asked again for a day. Else it is a technical failure:
     * its cause is kept (updWhy, in the log and in `UPDATE_CHECK --ez status true`), and it is tried again in half an hour.
     */
    static void failed(Context c, String why, boolean userDeclined) {
        Log.w(TAG, (userDeclined ? "declined: " : "failed: ") + why);
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit().putString("updWhy", why == null ? "" : why);
        if (userDeclined) e.putInt("updDeclined", p.getInt("updTarget", 0)).putLong("updDeclinedAt", System.currentTimeMillis());
        else e.putLong("updFailedAt", System.currentTimeMillis());
        e.apply();
        abandonCurrent(c);
        clearPlan(c);
        deleteAll(dir(c));
        toast(c, userDeclined ? "أُلغي تحديث تلفزيونك" : "لم يكتمل تحديث تلفزيونك");
    }

    /** every package of the plan is installed (or this app was replaced by the new one: the plan is then found empty at the next check) */
    static void finished(Context c) {
        prefs(c).edit().putString("updPlan", "").putInt("updSession", -1).putInt("updDeclined", 0).putLong("updFailedAt", 0).apply();
        deleteAll(dir(c));
    }

    private static void toast(final Context c, final String text) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(c, text, Toast.LENGTH_LONG).show());
    }

    private static void deleteAll(File d) {
        File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }

    private static byte[] download(String url) throws java.io.IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        try {
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode() + " for " + url);
            try (InputStream in = c.getInputStream()) {
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    b.write(buf, 0, n);
                    if (b.size() > 64 * 1024) throw new java.io.IOException("the manifest is too big");
                }
                return b.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    /** downloads into the file and returns its SHA-256 (hex); redirects (GitHub release assets) are followed */
    private static String fetchTo(String url, File out) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try {
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); OutputStream o = new FileOutputStream(out)) {
                byte[] buf = new byte[65536];
                int n;
                long total = 0;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                    o.write(buf, 0, n);
                    if ((total += n) > 40L * 1024 * 1024) throw new java.io.IOException("file too big");
                }
            }
        } finally {
            c.disconnect();
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : md.digest()) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}

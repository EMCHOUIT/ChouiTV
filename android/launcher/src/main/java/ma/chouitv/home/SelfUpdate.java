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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Updates of the two apps from the owner's repository (github.com/EMCHOUIT/ChouiTV). latest.json there names the newest version and its two
 * APKs with their SHA-256. When the home screen comes to the front (at most every 6 hours) and a newer version exists, the APKs are downloaded into
 * the cache, checked (hash, package name, version, the SAME signing key as the installed app), and handed to Android's installer, which asks for one
 * OK with the remote for each. The screensaver goes first, then this app (its own replacement ends this process). Nothing stays on the TV: the files
 * are deleted when the install ends or fails, and at the next check.
 *
 * Android does not let an ordinary app install silently, and needs «install unknown apps» for this one:
 * adb shell appops set ma.chouitv.home REQUEST_INSTALL_PACKAGES allow
 */
final class SelfUpdate {
    static final String MANIFEST = "https://raw.githubusercontent.com/EMCHOUIT/ChouiTV/main/latest.json";
    static final long CHECK_EVERY_MS = 6L * 3600 * 1000, RETRY_AFTER_DECLINE_MS = 24L * 3600 * 1000;
    static final String ACTION_RESULT = "ma.chouitv.home.INSTALL_RESULT";
    private static final String DIR = "update";
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private SelfUpdate() {}

    interface Done { void say(String message); }

    private static SharedPreferences prefs(Context c) { return Dock.prefs(c); }

    private static File dir(Context c) { return new File(c.getCacheDir(), DIR); }

    private static long versionCode(Context c, String pkg) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(pkg, 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) { return -1; }
    }

    /** In the background: check, and if there is a newer version, download it, verify it and start the installer. Safe to call often. */
    static void maybeStart(final Context ctx, final boolean force, final Done done) { maybeStart(ctx, force, false, done); }

    /** dry: only says whether a newer version exists ("available N"), downloads and installs nothing. */
    static void maybeStart(final Context ctx, final boolean force, final boolean dry, final Done done) {
        final Context c = ctx.getApplicationContext();
        if (!BUSY.compareAndSet(false, true)) { if (done != null) done.say("busy"); return; }
        new Thread(() -> {
            String message;
            try { message = run(c, force, dry); }
            catch (Exception e) { message = "error: " + e; }
            finally { BUSY.set(false); }
            if (done != null) done.say(message);
        }).start();
    }

    private static String run(Context c, boolean force, boolean dry) throws Exception {
        long now = System.currentTimeMillis();
        SharedPreferences p = prefs(c);
        if (!p.getString("updQueue", "").isEmpty()) {
            // an install that never ended (the screen asking «install?» was closed, or Android never answered) must not block the updates for ever:
            // after ten minutes, or when the person asks again, it is forgotten
            if (!force && now - p.getLong("updStartedAt", 0) < 10 * 60_000L) return "an install is in progress";
            p.edit().putString("updQueue", "").apply();
        }
        deleteAll(dir(c));
        if (!force && now - p.getLong("updCheck", 0) < CHECK_EVERY_MS) return "checked recently";
        JSONObject m = new JSONObject(new String(download(MANIFEST), "UTF-8"));
        p.edit().putLong("updCheck", now).apply();
        int latest = m.getInt("versionCode");
        long mine = versionCode(c, c.getPackageName());
        if (latest <= mine) return "up to date (installed " + mine + ", latest " + latest + ")";
        if (!force && p.getInt("updDeclined", 0) == latest && now - p.getLong("updDeclinedAt", 0) < RETRY_AFTER_DECLINE_MS)
            return "version " + latest + " was declined lately";
        // Android installs for an app only when the person allowed «install unknown apps» for it; without that the installer refuses at the last
        // step, after the download. The settings screen that asks the person is opened by whoever is in front (the screensaver's settings).
        if (Build.VERSION.SDK_INT >= 26 && !c.getPackageManager().canRequestPackageInstalls()) return "needs permission: install unknown apps";
        if (dry) return "available " + latest;
        JSONArray apks = m.getJSONArray("apks");
        List<String> paths = new ArrayList<>();
        File d = dir(c);
        if (!d.isDirectory() && !d.mkdirs()) return "error: no cache folder";
        for (int i = 0; i < apks.length(); i++) {
            JSONObject a = apks.getJSONObject(i);
            String pkg = a.getString("package");
            if (!pkg.matches("ma\\.chouitv\\.(home|screensaver)")) return "error: unexpected package " + pkg;
            File f = new File(d, pkg + ".apk");
            String sha = fetchTo(a.getString("url"), f);
            if (!sha.equalsIgnoreCase(a.getString("sha256"))) { deleteAll(d); return "error: checksum of " + pkg + " does not match"; }
            String bad = verify(c, f, pkg, latest);
            if (bad != null) { deleteAll(d); return "error: " + bad; }
            paths.add(f.getAbsolutePath());
        }
        if (paths.isEmpty()) return "error: no apk listed";
        p.edit().putString("updQueue", android.text.TextUtils.join("|", paths)).putInt("updTarget", latest).putLong("updStartedAt", now).apply();
        toast(c, "تحديث تلفزيونك " + latest + ": اضغط OK لتثبيته");
        installNext(c);
        return "installing version " + latest;
    }

    /** the file is the package it says, a newer version, and signed with the key of the installed app of that name */
    private static String verify(Context c, File f, String pkg, int latest) {
        PackageManager pm = c.getPackageManager();
        if (Build.VERSION.SDK_INT < 28) return "this Android is too old to check the signature";
        PackageInfo mine, got;
        try {
            got = pm.getPackageArchiveInfo(f.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
            mine = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
        } catch (Exception e) { return pkg + " cannot be read (" + e + ")"; }
        if (got == null || !pkg.equals(got.packageName)) return "not the package " + pkg;
        if (got.getLongVersionCode() < latest) return pkg + " is older than the version announced";
        if (got.signingInfo == null || mine.signingInfo == null) return "no signature to compare for " + pkg;
        Set<String> a = certs(got.signingInfo.getApkContentsSigners()), b = certs(mine.signingInfo.getApkContentsSigners());
        if (a.isEmpty() || !a.equals(b)) return pkg + " is signed with another key";
        return null;
    }

    private static Set<String> certs(Signature[] sigs) {
        Set<String> out = new HashSet<>();
        for (Signature s : sigs) out.add(Arrays.toString(s.toByteArray()));
        return out;
    }

    /** Starts the installer for the next file of the queue (the screensaver, then this app). */
    static void installNext(Context c) {
        SharedPreferences p = prefs(c);
        String q = p.getString("updQueue", "");
        if (q.isEmpty()) return;
        String[] all = q.split("\\|");
        String path = all[0];
        String rest = android.text.TextUtils.join("|", Arrays.copyOfRange(all, 1, all.length));
        p.edit().putString("updQueue", rest).apply();
        File f = new File(path);
        String pkg = f.getName().replace(".apk", "");
        try {
            PackageInstaller pi = c.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            sp.setAppPackageName(pkg);
            int id = pi.createSession(sp);
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
            failed(c, "install " + pkg + ": " + e);
        }
    }

    /** the install ended badly or was refused: nothing more is tried for a day, and the files go */
    static void failed(Context c, String why) {
        SharedPreferences p = prefs(c);
        p.edit().putString("updQueue", "").putInt("updDeclined", p.getInt("updTarget", 0)).putLong("updDeclinedAt", System.currentTimeMillis()).apply();
        deleteAll(dir(c));
        toast(c, "لم يكتمل تحديث تلفزيونك");
    }

    static void finished(Context c) {
        prefs(c).edit().putString("updQueue", "").apply();
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
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
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
        try (InputStream in = c.getInputStream(); OutputStream o = new FileOutputStream(out)) {
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
            byte[] buf = new byte[65536];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
                o.write(buf, 0, n);
                if ((total += n) > 40L * 1024 * 1024) throw new java.io.IOException("file too big");
            }
        } finally {
            c.disconnect();
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : md.digest()) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}

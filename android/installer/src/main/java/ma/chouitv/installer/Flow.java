package ma.chouitv.installer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** What the installer does to the TV, step by step. Every method blocks: call them from a worker thread. */
final class Flow {
    static final String SAVER = "ma.chouitv.screensaver", HOME = "ma.chouitv.home", CHANNELS = "com.majnioui.tv.channels";
    static final String VERIFIER = "verifier_verify_adb_installs";

    interface Say { void step(int index, String state, int percent); }

    private final Adb tv;
    private final File cache;

    Flow(Adb tv, File cache) { this.tv = tv; this.cache = cache; }

    /** Downloads one APK and installs it; the same recovery as optimize/install.sh (Play Protect, another signing key). */
    void installApk(String pkg, String url, String sha, final Adb.Progress down, final Adb.Progress up) throws Exception {
        File f = Net.download(url, new File(cache, pkg + ".apk"), sha, down);
        try {
            String out = tv.install(f, up);
            if (out.contains("INSTALL_FAILED_VERIFICATION_FAILURE")) {
                // Play Protect refused an install over ADB: its check is off for this install only and put back after
                String was = tv.shell("settings get global " + VERIFIER).trim();
                tv.shell("settings put global " + VERIFIER + " 0");
                try { out = tv.install(f, up); }
                finally {
                    if (was.isEmpty() || was.equals("null")) tv.shell("settings delete global " + VERIFIER);
                    else tv.shell("settings put global " + VERIFIER + " " + was);
                }
            }
            if (out.contains("UPDATE_INCOMPATIBLE") || out.contains("signatures do not match")) {
                // an older build signed with another key: only then is the installed app removed
                tv.shell("pm uninstall " + pkg);
                out = tv.install(f, up);
            }
            if (!out.contains("Success")) throw new IOException(pkg + ": " + out);
            Thread.sleep(3000);
            if (!tv.shell("pm list packages " + pkg).contains("package:" + pkg)) {
                throw new IOException(pkg + " was removed by this TV right after the install");
            }
        } finally { f.delete(); }
    }

    /** The screensaver and the TV's permissions for the home app, then the home role. */
    void configure() throws IOException {
        tv.shell("settings put secure screensaver_enabled 1");
        tv.shell("settings put secure screensaver_components " + SAVER + "/.ScreenDream");
        tv.shell("settings put secure screensaver_default_component " + SAVER + "/.ScreenDream");
        tv.shell("pm grant " + HOME + " android.permission.ACCESS_FINE_LOCATION");
        tv.shell("appops set " + HOME + " SCHEDULE_EXACT_ALARM allow");
        tv.shell("cmd role add-role-holder --user 0 android.app.role.HOME " + HOME);
        tv.shell("cmd package set-home-activity --user 0 " + HOME + "/.MainActivity");
    }

    boolean installed(String pkg) throws IOException {
        return tv.shell("pm list packages " + pkg).contains("package:" + pkg);
    }

    String model() throws IOException {
        return tv.shell("getprop ro.product.model").trim();
    }

    // ---------- the screensaver's own receiver (the same one optimize/saver.sh talks to) ----------

    /** One broadcast to the screensaver's receiver; the receiver's answer, or an IOException on «error:». */
    String saver(String action, String extras) throws IOException {
        String out = tv.shell("am broadcast -n " + SAVER + "/.SaverReceiver -a " + SAVER + "." + action + (extras.isEmpty() ? "" : " " + extras));
        int i = out.indexOf("data=\"");
        String data = i < 0 ? "" : out.substring(i + 6);
        if (data.endsWith("\"")) data = data.substring(0, data.length() - 1);
        if (data.startsWith("error:")) throw new IOException(data);
        return data.trim();
    }

    static String q(String v) {
        if (v.indexOf('\'') >= 0) throw new IllegalArgumentException("no apostrophes");
        return "'" + v + "'";
    }

    /** «region: city، city…» lines the receiver prints: region → cities. */
    List<String[]> cities() throws IOException {
        List<String[]> r = new ArrayList<>();
        for (String line : saver("CITIES", "").split("\n")) {
            int c = line.indexOf(": ");
            if (c < 0) continue;
            r.add(new String[] {line.substring(0, c), line.substring(c + 2)});
        }
        return r;
    }

    void setCity(String city) throws IOException { saver("SET", "--es city " + q(city)); }

    void setTz(String tz) throws IOException { saver("SET", "--es tzchoice " + q(tz)); }

    void setMethod(String m) throws IOException { saver("SET", "--es pmchoice " + q(m)); }

    void rescheduleAdhan() throws IOException {
        tv.shell("am broadcast -n " + HOME + "/.tilawah.MusicReceiver -a " + HOME + ".ADHAN_RESCHEDULE");
    }

    void adhan(boolean on) throws IOException {
        tv.shell("am broadcast -n " + HOME + "/.tilawah.MusicReceiver -a " + HOME + ".ADHAN_" + (on ? "ON" : "OFF"));
    }

    /** A settings file made by «export»: what it would change (apply=false), or applies it (apply=true). */
    String importSettings(String b64, boolean apply) throws IOException {
        return saver("IMPORT", "--es json_b64 " + b64 + (apply ? " --es apply yes" : ""));
    }

    void openOnTv() throws IOException {
        tv.shell("input keyevent KEYCODE_HOME");
    }
}

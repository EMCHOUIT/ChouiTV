package ma.chouitv.screensaver;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 4.0 is one app: the screensaver that was ma.chouitv.screensaver lives here now. On a family TV the old app is still installed after the update
 * (an app cannot remove another one silently) and holds the person's settings, tasks and photos in its own data. Once, early, this brings them over:
 *
 *   - the settings through the same code as SaverReceiver IMPORT (SettingsBackup.validate + apply, the undo copy is kept), from the old app's
 *     .../export (3.185 and after); an older old app has no /export, so the basic settings are read from its page fragment instead;
 *   - the tasks (.../tasks) and the person's own photos (.../own/N, 10 at most) into this app's own storage.
 *
 * Only an app signed with our certificate is read (and the READ_CONFIG permission of the old app is signature-level too). Nothing is ever written to
 * the old app. Everything is caught: the home screen never crashes for this, and the flag is set whatever went wrong, so it is tried once; the outcome is
 * kept as a line for `saver.sh get`. The home half of an export is left out: the home app is this app, its data stayed where it was at the update.
 *
 * Then SettingsActivity guides the two steps only a person can do: pick this app's screensaver, and remove the old app (unfinished()).
 * Until then the old app's own screensaver stays the chosen one and keeps working.
 */
public final class Migration {
    private static final String PREFS = "migration", FLAG = "done", OUTCOME = "outcome", AT = "at", OPEN = "open";
    private static final Uri BASE = Uri.parse("content://" + MigrationCore.OLD_PACKAGE + ".config");
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private Migration() { }

    private static SharedPreferences state(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    // ---------------------------------------------------------------- running it, once

    public static void runOnce(Context c) { runOnce(c, null); }

    /** Starts the move in the background if it was never done; `done` runs when it ends (at once when there is nothing to do). Never throws. */
    public static void runOnce(Context context, final Runnable done) {
        boolean started = false;
        try {
            final Context c = context.getApplicationContext() != null ? context.getApplicationContext() : context;
            if (!state(c).getBoolean(FLAG, false) && RUNNING.compareAndSet(false, true)) {
                started = true;
                new Thread(() -> {
                    try {
                        run(c);
                    } catch (Throwable t) {
                        finish(c, "failed: " + t, false);
                    } finally {
                        RUNNING.set(false);
                        if (done != null) try { done.run(); } catch (Throwable ignored) { }
                    }
                }, "migration").start();
            }
        } catch (Throwable ignored) {
            // the home screen goes on
        }
        if (!started && done != null) try { done.run(); } catch (Throwable ignored) { }
    }

    /** The line for `saver.sh get`. */
    public static String outcome(Context c) {
        try {
            String s = state(c).getString(OUTCOME, null);
            return s != null ? s : RUNNING.get() ? "running" : "not run yet";
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static void finish(Context c, String outcome, boolean open) {
        try {
            SharedPreferences.Editor e = state(c).edit().putBoolean(FLAG, true).putString(OUTCOME, outcome).putLong(AT, System.currentTimeMillis());
            if (open) e.putBoolean(OPEN, true);
            e.commit();
        } catch (Throwable ignored) { }
    }

    private static void run(Context c) {
        boolean old = installed(c, MigrationCore.OLD_PACKAGE);
        boolean same = old && c.getPackageManager().checkSignatures(c.getPackageName(), MigrationCore.OLD_PACKAGE) == PackageManager.SIGNATURE_MATCH;
        MigrationCore.Go go = MigrationCore.decide(false, old, same);
        if (go != MigrationCore.Go.RUN) {
            finish(c, "skipped: " + go.name().toLowerCase(), false);
            return;
        }
        // the row in the settings stays until the person has finished, even if what follows fails halfway
        state(c).edit().putBoolean(OPEN, true).commit();
        String problem = null, source = "export";
        int changes = 0, tasksDone = 0, tasksOffered = 0, photosDone = 0, photosOffered = 0;
        Prefs prefs = new Prefs(c);

        // 1. the settings
        try {
            JSONObject file = readExport(c);
            if (file == null) {
                source = "fallback";
                file = fallbackFile(c);
            }
            if (file == null) {
                problem = "settings not readable";
            } else {
                file.remove("home");
                String bad = SettingsBackup.validate(file);
                if (bad != null) problem = "settings refused: " + bad;
                else changes = SettingsBackup.apply(c, file);
            }
        } catch (Throwable t) {
            problem = "settings: " + t;
        }

        // 2. the tasks
        try {
            List<String> texts = new ArrayList<>();
            List<Boolean> finished = new ArrayList<>();
            try (Cursor cur = c.getContentResolver().query(BASE.buildUpon().appendPath("tasks").build(), null, null, null, null)) {
                while (cur != null && cur.moveToNext()) {
                    texts.add(Prefs.cleanTask(cur.getString(cur.getColumnIndexOrThrow("t"))));
                    finished.add(cur.getInt(cur.getColumnIndexOrThrow("done")) != 0);
                }
            }
            tasksOffered = texts.size();
            List<String> have = new ArrayList<>();
            for (Prefs.Task t : prefs.tasks()) have.add(t.text);
            for (int k : MigrationCore.tasksToAdd(have, texts, Prefs.MAX_TASKS - have.size())) {
                if (prefs.addTask(texts.get(k)) != null) continue;
                if (finished.get(k)) prefs.doneTask(prefs.tasks().size());
                tasksDone++;
            }
        } catch (Throwable t) {
            problem = join(problem, "tasks: " + t);
        }

        // 3. the person's own photos
        try {
            int offered = 0;
            String mode = Own.MIX;
            try (Cursor cur = c.getContentResolver().query(BASE.buildUpon().appendPath("own").build(), null, null, null, null)) {
                if (cur != null && cur.moveToFirst() && cur.getColumnIndex("n") >= 0) {
                    offered = cur.getInt(cur.getColumnIndex("n"));
                    if (cur.getColumnIndex("mode") >= 0 && Own.ONLY.equals(cur.getString(cur.getColumnIndex("mode")))) mode = Own.ONLY;
                }
            }
            photosOffered = Math.max(0, offered);
            int copy = MigrationCore.photosToCopy(offered, Own.count(c));
            for (int n = 1; n <= copy; n++) {
                if (copyPhoto(c, n)) photosDone++;
            }
            if (photosDone > 0) prefs.setOwnMode(mode);
        } catch (Throwable t) {
            problem = join(problem, "photos: " + t);
        }

        finish(c, MigrationCore.outcome(source, changes, tasksDone, tasksOffered, photosDone, photosOffered, problem), true);
    }

    private static String join(String a, String b) { return a == null ? b : a + "; " + b; }

    /** The old app's settings file (3.185 and after), or null when it has no /export. */
    private static JSONObject readExport(Context c) {
        try (Cursor cur = c.getContentResolver().query(BASE.buildUpon().appendPath("export").build(), null, null, null, null)) {
            if (cur == null || !cur.moveToFirst()) return null;
            int col = cur.getColumnIndex("json");   // an older app answers its ordinary row, which has no such column
            String json = col < 0 ? null : cur.getString(col);
            return json == null ? null : new JSONObject(json);
        } catch (Throwable t) {
            return null;
        }
    }

    /** What an old app (before 3.185) gives: its page fragment, the home cards, the greeting and the adhan bar. */
    private static JSONObject fallbackFile(Context c) throws Exception {
        try (Cursor cur = c.getContentResolver().query(BASE, null, null, null, null)) {
            if (cur == null || !cur.moveToFirst()) return null;
            Map<String, Object> m = MigrationCore.fallbackSettings(text(cur, "fragment"), text(cur, "home_panels"), text(cur, "greeting"), text(cur, "adhan_bar"));
            JSONObject settings = new JSONObject();
            for (Map.Entry<String, Object> e : m.entrySet()) settings.put(e.getKey(), e.getValue());
            return new JSONObject().put("app", "ChouiTV").put("schema", 1).put("settings", settings);
        }
    }

    private static String text(Cursor cur, String column) {
        int i = cur.getColumnIndex(column);
        return i < 0 ? null : cur.getString(i);
    }

    private static boolean copyPhoto(Context c, int n) {
        File tmp = new File(c.getCacheDir(), "migrate-" + n + ".jpg");
        try {
            Uri uri = BASE.buildUpon().appendPath("own").appendPath(String.valueOf(n)).build();
            try (InputStream in = c.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(tmp)) {
                if (in == null) return false;
                byte[] buf = new byte[16384];
                int len;
                long total = 0;
                while ((len = in.read(buf)) > 0) {
                    total += len;
                    if (total > Own.MAX_BYTES) return false;   // Own.add would refuse it; do not even keep it
                    out.write(buf, 0, len);
                }
            }
            return Own.add(c, tmp) == null;
        } catch (Throwable t) {
            return false;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    // ---------------------------------------------------------------- is the person done? (the row in the settings)

    static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The screensaver the TV has chosen ("pkg/cls"), or null when this Android does not let an app read it. */
    static String chosenDream(Context c) {
        try {
            return Settings.Secure.getString(c.getContentResolver(), "screensaver_components");
        } catch (Throwable t) {
            return null;
        }
    }

    /** The move was started and the person still has a step to do: the old app is there, or the chosen screensaver is not ours. */
    static boolean unfinished(Context c) {
        try {
            SharedPreferences s = state(c);
            if (!s.getBoolean(OPEN, false)) return false;
            if (MigrationCore.moveFinished(installed(c, MigrationCore.OLD_PACKAGE), chosenDream(c), c.getPackageName())) {
                s.edit().putBoolean(OPEN, false).apply();   // done for good: a later pick of another screensaver is the person's own
                return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}

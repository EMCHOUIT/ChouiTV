#!/usr/bin/env python3
"""The move from the two-app ChouiTV to the single package (Migration.java, 4.0): its decisions (MigrationCore.java on tests/migration_cases.tsv)
run on a JVM; what needs Android (the provider reads, the settings row, the manifest) the source must show. Needs javac for the cases.
Run: python3 tests/migration_test.py"""
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SAVER = ROOT / "android/launcher/src/main/java/ma/chouitv/screensaver"
MAIN = ROOT / "android/launcher/src/main"
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok


cases = [l for l in (ROOT / "tests/migration_cases.tsv").read_text(encoding="utf-8").splitlines() if l and not l.startswith("#")]
if shutil.which("javac") is None:
    print("SKIP migration cases: javac not found")
else:
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(SAVER / "MigrationCore.java"), str(ROOT / "tests/MigrationRun.java")], check=True)
        stripped = Path(tmp) / "cases.tsv"
        stripped.write_text("\n".join("\t".join(l.split("\t")[:-1]) for l in cases) + "\n", encoding="utf-8")
        out = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", tmp, "ma.chouitv.screensaver.MigrationRun", str(stripped)],
                             capture_output=True, text=True, encoding="utf-8", check=True).stdout.splitlines()
        for line, got in zip(cases, out):
            f = line.split("\t")
            check(" ".join(f[:-1]) + " -> " + f[-1], got == f[-1], got)
        check("every case ran", len(out) == len(cases), f"{len(out)} of {len(cases)}")

text = lambda p: p.read_text(encoding="utf-8")
manifest = text(MAIN / "AndroidManifest.xml")
mig = text(SAVER / "Migration.java")
main = text(MAIN / "java/ma/chouitv/home/MainActivity.java")
boot = text(SAVER / "BootReceiver.java")
settings = text(SAVER / "SettingsActivity.java")
receiver = text(SAVER / "SaverReceiver.java")

check("manifest: reads the old app's provider (READ_CONFIG of the same key)", '<uses-permission android:name="ma.chouitv.screensaver.READ_CONFIG"' in manifest)
check("manifest: may see the old package (Android 11 package visibility)", '<package android:name="ma.chouitv.screensaver"' in manifest)
check("manifest: may ask to remove the old app", 'android.permission.REQUEST_DELETE_PACKAGES' in manifest)
check("manifest: the old app's READ_CONFIG is not declared here (a permission the old app owns)", 'android:name="ma.chouitv.screensaver.READ_CONFIG"\n' not in manifest
      and '<permission\n        android:name="ma.chouitv.screensaver.READ_CONFIG"' not in manifest)

check("Migration: only an app signed with our certificate is read", "checkSignatures" in mig and "SIGNATURE_MATCH" in mig)
check("Migration: reads /export and falls back for an older app", 'appendPath("export")' in mig and "fallback" in mig and "MigrationCore.fallbackSettings" in mig)
check("Migration: applies through SettingsBackup (validate + apply keeps the undo copy)", "SettingsBackup.validate" in mig and "SettingsBackup.apply" in mig)
check("Migration: the tasks and the photos come through Prefs and Own", "addTask" in mig and "Own.add" in mig and 'appendPath("tasks")' in mig and 'appendPath("own")' in mig)
check("Migration: the flag is set in a finally, and the outcome kept", "finally" in mig and "putBoolean(FLAG, true)" in mig and "OUTCOME" in mig)
check("Migration: nothing escapes (Throwable is caught)", "catch (Throwable" in mig)
check("Migration: nothing runs on the main thread", "new Thread" in mig)
check("Migration: the old app's own settings never change (no insert, update or delete on its provider)",
      re.search(r"getContentResolver\(\)\.(insert|update|delete|call)\(", mig) is None)
check("home MainActivity starts it", "Migration.runOnce(" in main)
check("BootReceiver starts it after boot and after the update", "Migration.runOnce(" in boot)
check("saver.sh get shows the outcome", "migration: " in receiver and "Migration.outcome(" in receiver)
check("settings: the row is first on the first tab and only while unfinished", "Migration.unfinished(" in settings
      and settings.index("Migration.unfinished(") < settings.index('header("الموقع")'))
check("settings: step 1 opens the TV's screensaver choice, step 2 asks to remove the old app",
      "openDreamSettings()" in settings and "Intent.ACTION_DELETE" in settings and "package:" in settings and "إكمال الانتقال إلى النسخة الجديدة" in settings)
sys.exit(1 if failed else 0)

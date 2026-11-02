#!/usr/bin/env python3
"""«تعديل التاريخ الهجري»: the helper on a plain JVM (HijriShift.java), and the wiring by source: the fragment carries #hijri=, the page shifts
every Hijri use but the prayer calculation, the home screen's date line follows, SaverReceiver/saver.sh/backup know the key, the settings has the row.
Run: python3 tests/hijri_shift_test.py"""
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
J = ROOT / "android/launcher/src/main/java/ma/chouitv"
failed = False


def check(name, ok):
    global failed
    print(("PASS " if ok else "FAIL ") + name)
    if not ok:
        failed = True


def src(p):
    return (ROOT / p).read_text(encoding="utf-8")


page = src("src/template.html")
prefs = (J / "screensaver/Prefs.java").read_text(encoding="utf-8")
check("Prefs.fragment() carries the shift", "HijriShift.fragment(hijriShift())" in prefs)
check("page reads #hijri= held to -2..+2", "Math.max(-2, Math.min(2, Math.round(_num('hijri', 0))))" in page)
# every Intl islamic-umalqura use goes through hijriDate(), except the raw month of the prayer calculation
check("date line shifted", "hijriDate(now)" in page and "HIJRI_SHIFT === 0" in page)
check("occasions shifted", "hijri(hijriDate(d))" in page and "hf.formatToParts(hijriDate(d))" in page)
check("fasting shifted", "FAST_HIJRI.formatToParts(hijriDate(date))" in page)
check("Ramadan mode shifted, the Isha of Ramadan not", "hijriMonthNow(true) === 9" in page and "hijriMonthNow() === 9 && PRAYERS.length" in page)
check("dist/tv.html is rebuilt with it", "HIJRI_SHIFT" in src("dist/tv.html"))
check("home date line follows", "hijriShift()" in (J / "home/ConfigSnapshot.java").read_text(encoding="utf-8")
      and "cfg.hijriShift()" in (J / "home/MainActivity.java").read_text(encoding="utf-8"))
rec = (J / "screensaver/SaverReceiver.java").read_text(encoding="utf-8")
check("SET knows hijri", 'getStringExtra("hijri")' in rec and 'p.setChoice("hijri"' in rec and '"\\nhijri: "' in rec)
check("saver.sh has hijri", re.search(r"^\s*hijri\)\s+set1 hijri", src("optimize/saver.sh"), re.M) is not None)
check("backup label", '{"c_hijri", "تعديل التاريخ الهجري"}' in (J / "screensaver/SettingsBackup.java").read_text(encoding="utf-8"))
check("settings row", '"تعديل التاريخ الهجري"' in (J / "screensaver/SettingsActivity.java").read_text(encoding="utf-8"))

if not shutil.which("javac"):
    print("SKIP the helper on the JVM: no javac here")
else:
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(J / "home/HijriShift.java"), str(ROOT / "tests/HijriShiftRun.java")], check=True)
        run = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", tmp, "ma.chouitv.home.HijriShiftRun"], capture_output=True)
    out = run.stdout.decode("utf-8")
    print(out, end="")
    if run.returncode != 0 or "FAIL" in out:
        print(run.stderr.decode("utf-8", "replace"))
        failed = True
sys.exit(1 if failed else 0)

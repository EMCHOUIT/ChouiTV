#!/usr/bin/env python3
"""The adhan group of the settings (tab الصلاة, «أذان الصلوات»): the lines on a plain JVM (AdhanSettingsText.java), and the wiring by source:
every change goes through the code the listening screen used (Prefs + AdhanScheduler.schedule), the facade is public, the screen opens
AdhanActivity and PrayerOptionsActivity, refreshes in onResume and ends a ringing adhan with any key.   Run: python3 tests/adhan_settings_test.py"""
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TIL = ROOT / "android/launcher/src/main/java/ma/chouitv/home/tilawah"
failed = False


def check(name, ok):
    global failed
    print(("PASS " if ok else "FAIL ") + name)
    if not ok:
        failed = True


facade = (TIL / "AdhanSettings.java").read_text(encoding="utf-8")
screen = (ROOT / "android/launcher/src/main/java/ma/chouitv/screensaver/SettingsActivity.java").read_text(encoding="utf-8")

check("facade is public", "public final class AdhanSettings" in facade)
check("switch: Prefs then the alarm is set again", re.search(r"setAdhanOn\(on\);\s*AdhanScheduler\.schedule\(", facade) is not None)
check("one prayer: Prefs then the alarm is set again", re.search(r"setAdhanPrayerOn\(slot, on\);\s*AdhanScheduler\.schedule\(", facade) is not None)
check("volume: 10 steps, 10..100, heard 2 s", "Math.max(10, Math.min(100, was + step))" in facade and "MusicService.adhan(c, " in facade and ", 2)" in facade)
check("the voice picker and the prayer options are the existing screens", "AdhanActivity.class" in facade and "PrayerOptionsActivity.class" in facade)

check("group name", 'header("أذان الصلوات")' in screen)
for title in ("الأذان التلقائي", "صوت المؤذّن", "مستوى الصوت", "الصلوات اليوم"):
    check("row «%s»" % title, '"%s"' % title in screen)
check("row «الصلوات»", re.search(r'row\("الصلوات"', screen) is not None)
check("left/right on the volume row", "AdhanSettings.changeVolume(" in screen and "KEYCODE_DPAD_LEFT" in screen)
check("the screen has no copy of the adhan logic", "setAdhanOn" not in screen and "AdhanScheduler" not in screen)
check("onResume redraws", re.search(r"void onResume\(\) \{[^}]*render\(\)", screen, re.S) is not None)
check("any key ends a ringing adhan", "AdhanSettings.onKey(" in screen and "public static boolean onKey" in facade and "AdhanStop.onKey(" in facade)

if not shutil.which("javac"):
    print("SKIP the lines on the JVM: no javac here")
else:
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(TIL / "AdhanSettingsText.java"), str(ROOT / "tests/AdhanSettingsTextRun.java")], check=True)
        run = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", tmp, "ma.chouitv.home.tilawah.AdhanSettingsTextRun"], capture_output=True)
    out = run.stdout.decode("utf-8")
    print(out, end="")
    if run.returncode != 0 or "FAIL" in out:
        print(run.stderr.decode("utf-8", "replace"))
        failed = True
sys.exit(1 if failed else 0)

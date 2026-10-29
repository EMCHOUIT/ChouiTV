#!/usr/bin/env python3
"""4.0: the screensaver's settings are an activity of the home app. They must still be among the apps of the grid, the «شاشة التوقف» pill must not
depend on another package being installed, and the favourites/hidden/names that named the old package follow to this one.
Run: python3 tests/home_settings_test.py"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
H = ROOT / "android/launcher/src/main/java/ma/chouitv/home"
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok


dock = (H / "Dock.java").read_text(encoding="utf-8")
main = (H / "MainActivity.java").read_text(encoding="utf-8")
names = (H / "AppNames.java").read_text(encoding="utf-8")
manifest = (ROOT / "android/launcher/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

check("the settings activity is a launcher entry of the app", 'android:name="ma.chouitv.screensaver.SettingsActivity"' in manifest
      and "LEANBACK_LAUNCHER" in manifest.split('ma.chouitv.screensaver.SettingsActivity')[1].split("</activity>")[0])
check("the grid skips this app's activities except its settings",
      "p.equals(c.getPackageName()) && !SETTINGS_ACTIVITY.equals(ri.activityInfo.name)" in dock)
check("a favourite that names the old settings package means this app", "static String current(Context c, String pkg)" in dock and "OLD_SETTINGS_PACKAGE" in dock
      and "current(c, p)" in dock)
check("hidden/shown and the given names follow too", dock.count("current(c, ") >= 2 and "Dock.current(c," in names)
check("the «شاشة التوقف» pill is always shown (no check of another package)", "row.add(saver);" in main and "isInstalled(SAVER_PKG)" not in main)
check("its long press opens the settings of this app", "openSaverSettings()" in main and "Dock.SETTINGS_ACTIVITY" in main)
check("no leftover reference to the old package in the home screen code", "SAVER_PKG" not in main)
settings_block = manifest.split('ma.chouitv.screensaver.SettingsActivity"')[1].split(">")[0]
check("the settings live in their own task (the home task has clearTaskOnLaunch: a launcher intent would reset it and drop the settings at once)",
      'android:taskAffinity="ma.chouitv.home.settings"' in settings_block)
sys.exit(1 if failed else 0)

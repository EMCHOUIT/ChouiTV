#!/usr/bin/env python3
"""The update button of the screensaver's settings (تحديث التطبيق والشاشة الآن): it asks the home app for the apps, not only the page; the update
rows are the last of the tab; the install is started with the home screen in front (Android refuses its «install?» screen to a background app);
a missing «install unknown apps» permission is asked for, and an install that never ended does not block the next one for ever.
Run: python3 tests/update_now_test.py"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok


settings = (ROOT / "android/app/src/main/java/ma/chouitv/screensaver/SettingsActivity.java").read_text(encoding="utf-8")
home = ROOT / "android/launcher/src/main"
selfu = (home / "java/ma/chouitv/home/SelfUpdate.java").read_text(encoding="utf-8")
manifest = (home / "AndroidManifest.xml").read_text(encoding="utf-8")

check("the button asks the home app for the apps (UPDATE_NOW), not only the page", '"ma.chouitv.home.UPDATE_NOW"' in settings and "askAppsUpdate(page)" in settings)
check("the home app answers only the two ChouiTV apps (the BACKUP permission, same signing key)",
      'android:name=".UpdateNowReceiver"' in manifest and 'android:permission="ma.chouitv.home.permission.BACKUP"' in manifest)
check("the check comes first (dry), then the home screen is put in front, then the install",
      'putExtra("dry", dry)' in settings and "CATEGORY_HOME" in settings and "askAppsUpdate(page, false)" in settings and 'return "available " + latest;' in selfu)
check("a missing «install unknown apps» permission opens Android's page for it", "ACTION_MANAGE_UNKNOWN_APP_SOURCES" in settings and "canRequestPackageInstalls()" in selfu)
check("an install that never ended is forgotten after ten minutes or when the person asks again", "updStartedAt" in selfu and "10 * 60_000L" in selfu)
i, j = settings.index('header("نسخ الإعدادات")'), settings.index('header("التحديث")')
check("the update rows come after the settings backup, the last of the tab", i < j and settings.index('note(last, Updater.versionLines(this))') > j)
sys.exit(1 if failed else 0)

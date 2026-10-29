#!/usr/bin/env python3
"""The update button of the settings (تحديث التطبيق والشاشة الآن), 4.0: the settings live in the SAME process as the updater, so there is no
UpdateNowReceiver, no UPDATE_NOW broadcast and no signature permission any more: the settings call the public static SelfUpdate.checkNow(Context,
boolean dry, Callback). The ADB entry (UPDATE_CHECK, DUMP-gated) stays. A missing «install unknown apps» permission is asked for, the update rows
are the last of the tab, and an install that never ended does not block the next one for ever.
Run: python3 tests/update_now_test.py"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok


main = ROOT / "android/launcher/src/main"
settings = (main / "java/ma/chouitv/screensaver/SettingsActivity.java").read_text(encoding="utf-8")
selfu = (main / "java/ma/chouitv/home/SelfUpdate.java").read_text(encoding="utf-8")
manifest = (main / "AndroidManifest.xml").read_text(encoding="utf-8")
planner = (main / "java/ma/chouitv/home/UpdatePlanner.java").read_text(encoding="utf-8")
receiver = (main / "java/ma/chouitv/home/UpdateReceiver.java").read_text(encoding="utf-8")

check("UpdateNowReceiver is gone (file, manifest entry, action)", not (main / "java/ma/chouitv/home/UpdateNowReceiver.java").exists()
      and "UpdateNowReceiver" not in manifest and "UPDATE_NOW" not in manifest)
check("the settings no longer broadcast UPDATE_NOW to another package", "UPDATE_NOW" not in settings)
check("SelfUpdate is public with a public static checkNow(Context, boolean dry, Callback) and a public Callback",
      "public final class SelfUpdate" in selfu and "public static void checkNow(Context ctx, boolean dry, final Callback cb)" in selfu
      and "public interface Callback" in selfu)
check("the settings call SelfUpdate.checkNow", "SelfUpdate.checkNow(" in settings)
check("checkNow forces the check (the 6 h wait and a recent refusal are ignored)", "maybeStart(ctx, true, dry," in selfu)
check("the ADB entry stays, DUMP-gated, with status/force/dry",
      'android:name=".UpdateReceiver"' in manifest and "ma.chouitv.home.UPDATE_CHECK" in manifest
      and re.search(r'\.UpdateReceiver"\s+android:exported="true"\s+android:permission="android.permission.DUMP"', manifest) is not None
      and '"status"' in receiver and '"force"' in receiver and '"dry"' in receiver)
check("the dry check answers whether a version is available, installing nothing", 'return "available " + latest' in selfu)
check("a missing «install unknown apps» permission opens Android's page for it", "ACTION_MANAGE_UNKNOWN_APP_SOURCES" in settings and "canRequestPackageInstalls()" in selfu)
check("an install that never ended is forgotten after ten minutes or when the person asks again", "updStartedAt" in selfu and "10 * 60_000L" in planner)
if 'header("نسخ الإعدادات")' in settings and 'header("التحديث")' in settings:
    i, j = settings.index('header("نسخ الإعدادات")'), settings.index('header("التحديث")')
    check("the update rows come after the settings backup, the last of the tab", i < j)
sys.exit(1 if failed else 0)

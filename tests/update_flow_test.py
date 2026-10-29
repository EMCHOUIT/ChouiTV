#!/usr/bin/env python3
"""The update path, as source checks (the numbers are in update_planner_test.py): the installs are driven from a screen in the front, not from a
receiver; both apps are looked at; a failure keeps its cause; the version comes from one file; nothing is downloaded from outside the project's releases.
Run: python3 tests/update_flow_test.py"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
H = ROOT / "android/launcher/src/main/java/ma/chouitv/home"
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok


su = (H / "SelfUpdate.java").read_text(encoding="utf-8")
ua = (H / "UpdateActivity.java").read_text(encoding="utf-8")
ir = (H / "InstallResultReceiver.java").read_text(encoding="utf-8")
un = (H / "UpdateNowReceiver.java").read_text(encoding="utf-8")
man = (ROOT / "android/launcher/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
saver = (ROOT / "android/app/src/main/java/ma/chouitv/screensaver/Updater.java").read_text(encoding="utf-8")

check("the installs are driven by UpdateActivity, started and waited for before anything is installed", "launchUi(c)" in su and "UpdateActivity.live" in su)
check("the result receiver shows nothing itself (a background app cannot start Android's install screen)", "startActivity" not in ir and "live" in ir)
check("UpdateActivity starts Android's install screen from the front", "startActivity(confirm)" in ua and "deliver(" in ua)
check("the update screen is declared", 'android:name=".UpdateActivity"' in man)
check("both apps are looked at (the plan), the home app last, only the ones behind are downloaded", "UpdatePlanner.plan(" in su and "if (!plan.contains(pkg)) continue;" in su)
check("the plan survives an interruption and resumes in front of the person", 'putString("updPlan"' in su and "resumed" in su)
check("this app's own replacement is recognised in the new process (its plan entry is dropped when it is at the target version)", "it.remove()" in su and "versionCode(c, it.next()) >= target" in su)
check("a failure keeps its cause, logged and stored; a refusal by the person is told apart from a technical failure",
      'putString("updWhy"' in su and "Log.w(TAG" in su and "static void failed(Context c, String why, boolean userDeclined)" in su and "updFailedAt" in su)
check("a failed session is abandoned", "abandonSession" in su)
check("an Error cannot leave the check marked busy", "catch (Throwable e)" in su)
check("an APK comes only from the project's releases", 'APK_HOST = "https://github.com/EMCHOUIT/ChouiTV/releases/download/"' in su and "startsWith(APK_HOST)" in su)
check("the manifest is read with a size cap and its status checked", "64 * 1024" in su and 'getResponseCode() != 200' in su)
check("the real update answers at once (no download inside a broadcast), the check alone waits", 'setResultData("update: started")' in un)
check("the screensaver reads the newest version from latest.json, like the home app", "latest.json" in saver and "releases/latest" not in saver)
sys.exit(1 if failed else 0)

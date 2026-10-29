#!/usr/bin/env python3
"""The update path, as source checks (the numbers are in update_planner_test.py), for the ONE package of 4.0: the install is driven from a screen in
the front, not from a receiver; a one-entry manifest works and an old two-entry one only acts on our own package; the new APK is compared with OUR
signature; installing ourselves ends cleanly (the new process drops the plan entry at the target version); a failure keeps its cause; nothing is
downloaded from outside the project's releases.
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
up = (H / "UpdatePlanner.java").read_text(encoding="utf-8")
ua = (H / "UpdateActivity.java").read_text(encoding="utf-8")
ir = (H / "InstallResultReceiver.java").read_text(encoding="utf-8")
man = (ROOT / "android/launcher/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

check("the installs are driven by UpdateActivity, started and waited for before anything is installed", "launchUi(c)" in su and "UpdateActivity.live" in su)
check("the result receiver shows nothing itself (a background app cannot start Android's install screen)", "startActivity" not in ir and "live" in ir)
check("UpdateActivity starts Android's install screen from the front", "startActivity(confirm)" in ua and "deliver(" in ua)
check("the update screen is declared", 'android:name=".UpdateActivity"' in man)
check("the result receiver is declared, not exported", '".InstallResultReceiver" android:exported="false"' in man)
check("one package: the plan is decided by UpdatePlanner.plan, only what it lists is downloaded", "UpdatePlanner.plan(" in su and "if (!plan.contains(pkg)) continue;" in su)
check("a manifest entry that is not ours is ignored, not an error (an old two-entry manifest; no regex on package names)",
      "UpdatePlanner.HOME.equals(pkg)" in su and "matches(" not in su and "unexpected package" not in su)
check("no sibling package is consulted for the signature", "sibling" not in su and "verifyNew" not in su and "UpdatePlanner.mixed" not in su)
check("the new APK is compared with OUR installed signature and refused through UpdatePlanner.refusal (older / not newer / other key)",
      "getPackageInfo(UpdatePlanner.HOME" in su and "UpdatePlanner.refusal(" in su and "UpdatePlanner.sameSigner(" in su and "static String refusal(" in up)
check("the plan survives an interruption and resumes in front of the person", 'putString("updPlan"' in su and "resumed" in su)
check("installing ourselves ends cleanly: in the new process the plan entry at the target version is dropped (UpdatePlanner.remaining) and the plan finished",
      "UpdatePlanner.remaining(" in su and "if (left.isEmpty()) { if (!dry) finished(c)" in su)
check("a failure keeps its cause, logged and stored; a refusal by the person is told apart from a technical failure",
      'putString("updWhy"' in su and "Log.w(TAG" in su and "static void failed(Context c, String why, boolean userDeclined)" in su and "updFailedAt" in su)
check("a refusal waits a day, a technical failure 30 minutes", "RETRY_AFTER_DECLINE_MS = 24L * 3600 * 1000" in up and "RETRY_AFTER_FAILURE_MS = 30 * 60_000L" in up)
check("a failed session is abandoned", "abandonSession" in su)
check("an Error cannot leave the check marked busy", "catch (Throwable e)" in su)
check("an APK comes only from the project's releases", 'APK_HOST = "https://github.com/EMCHOUIT/ChouiTV/releases/download/"' in su and "startsWith(APK_HOST)" in su)
check("the APK is checked against the manifest's SHA-256", 'sha.equalsIgnoreCase(a.getString("sha256"))' in su)
check("the manifest is read with a size cap and its status checked", "64 * 1024" in su and 'getResponseCode() != 200' in su)
check("the manifest request carries the cache-busting minute and the signature is verified, a mismatch is retried in 30 minutes",
      '"?t=" + (now / 60_000L)' in su and "ManifestTrust.verify(file, signature)" in su and "now - CHECK_EVERY_MS + 30 * 60_000L" in su)
check("status is reported for UPDATE_CHECK --ez status true", "static String status(Context c)" in su and "why=" in su)
sys.exit(1 if failed else 0)

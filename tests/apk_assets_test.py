#!/usr/bin/env python3
"""The one APK (4.0) carries only what it uses: not urtv.html (the stand-alone page for a browser, 3.5 MB); the page the screensaver shows,
the fonts, the photos and the Quran lists are in it. Source check always; when the release APK was built, its file list is checked too.
Run: python3 tests/apk_assets_test.py"""
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok


gradle = (ROOT / "android/launcher/build.gradle").read_text(encoding="utf-8")
check("the build leaves urtv.html out (the Quran lists stay: the app uses them)", "ignoreAssetsPattern '!urtv.html'" in gradle)

apk = ROOT / "android/launcher/build/outputs/apk/release/launcher-release.apk"
if apk.exists():
    names = zipfile.ZipFile(apk).namelist()
    check("the built APK has no urtv.html", "assets/urtv.html" not in names)
    check("the built APK has the Quran lists", any(n.startswith("assets/quran/") for n in names))
    check("the built APK has the screensaver's page, fonts and photos",
          "assets/tv.html" in names and any(n.startswith("assets/fonts/") for n in names) and any(n.startswith("assets/backgrounds/") for n in names))
else:
    print("SKIP built-APK checks: no release APK")
sys.exit(1 if failed else 0)

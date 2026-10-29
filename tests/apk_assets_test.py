#!/usr/bin/env python3
"""The screensaver APK carries only what it uses: not urtv.html (the stand-alone page for a browser, 3.5 MB) and not the launcher's Quran lists.
Source check always; when the release APK was built, its file list is checked too.
Run: python3 tests/apk_assets_test.py"""
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok


gradle = (ROOT / "android/app/build.gradle").read_text(encoding="utf-8")
check("the screensaver build leaves urtv.html and the Quran lists out", "ignoreAssetsPattern '!urtv.html:!quran'" in gradle)

apk = ROOT / "android/app/build/outputs/apk/release/app-release.apk"
if apk.exists():
    names = zipfile.ZipFile(apk).namelist()
    check("the built screensaver APK has no urtv.html", "assets/urtv.html" not in names)
    check("the built screensaver APK has no Quran lists", not any(n.startswith("assets/quran/") for n in names))
    check("the built screensaver APK still has its page, fonts and photos",
          "assets/tv.html" in names and any(n.startswith("assets/fonts/") for n in names) and any(n.startswith("assets/backgrounds/") for n in names))
    check("the screensaver APK is under 4 MB", apk.stat().st_size < 4_000_000, str(apk.stat().st_size))
else:
    print("SKIP built-APK checks: no release APK")
sys.exit(1 if failed else 0)

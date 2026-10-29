#!/usr/bin/env python3
"""RAM: the home screen lets go of the card's WebView (and its renderer process) when it is not in front, and drops its icon caches when asked.
Run: python3 tests/ram_test.py"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok


main = (ROOT / "android/launcher/src/main/java/ma/chouitv/home/MainActivity.java").read_text(encoding="utf-8")
stop = re.search(r"protected void onStop\(\) \{(.*?)\n    \}", main, re.S)
check("MainActivity.onStop destroys the card's WebView", bool(stop) and "homeCard.destroy()" in stop.group(1))
check("onResume builds it again", "homeCard.refresh()" in main)
trim = re.search(r"public void onTrimMemory\(int level\) \{(.*?)\n    \}", main, re.S)
check("onTrimMemory drops the icon caches once the screen is hidden",
      bool(trim) and "TRIM_MEMORY_UI_HIDDEN" in trim.group(1) and "artCache.evictAll()" in trim.group(1) and "colorCache.clear()" in trim.group(1))
sys.exit(1 if failed else 0)

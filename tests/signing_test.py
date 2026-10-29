#!/usr/bin/env python3
"""The signing key: the pinned fingerprint exists, a release build refuses to sign with anything else than the ChouiTV key, and the check script is there.
Run: python3 tests/signing_test.py"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok


pin = (ROOT / ".github/signer-sha256.txt").read_text(encoding="utf-8").strip()
check("the pinned signer fingerprint is a SHA-256", re.fullmatch(r"[0-9a-f]{64}", pin) is not None, pin)
for module in ("launcher", "app"):
    g = (ROOT / "android" / module / "build.gradle").read_text(encoding="utf-8")
    check(f"{module}: a release build without ANDROID_KEYSTORE fails", "throw new GradleException('ANDROID_KEYSTORE is not set" in g)
    check(f"{module}: no fallback to the debug key", "signingConfigs.debug" not in g)
script = (ROOT / "optimize/check-signer.sh").read_text(encoding="utf-8")
check("check-signer.sh compares with the pinned fingerprint", "signer-sha256.txt" in script and "apksigner" in script and "exit $bad" in script)
doc = (ROOT / "docs/TILAWAH.md").read_text(encoding="utf-8")
check("the docs say to keep two offline copies of the key", "two offline copies" in doc and "check-signer.sh" in doc)
sys.exit(1 if failed else 0)

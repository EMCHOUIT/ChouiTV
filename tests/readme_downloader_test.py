#!/usr/bin/env python3
"""Install option 4 in the three READMEs (ar, fr, en): from the TV itself with the Downloader app and its short code, which points to the
stable link of the latest release (the link never changes from one version to the next, so neither does the code). The full address stays
beside the code as the fallback. Run: python3 tests/readme_downloader_test.py"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CODE = "9833321"
STABLE = "https://github.com/EMCHOUIT/ChouiTV/releases/latest/download/chouitv-home.apk"
# the words that said "three ways" before this option existed
OLD_COUNT = {"README.en.md": "three ways", "README.fr.md": "trois façons", "README.md": "ثلاث طرق"}
NEW_COUNT = {"README.en.md": "four ways", "README.fr.md": "quatre façons", "README.md": "أربع طرق"}
failed = 0

def check(ok, msg):
    global failed
    print(("PASS " if ok else "FAIL ") + msg)
    failed += not ok

for name in ("README.md", "README.fr.md", "README.en.md"):
    t = (ROOT / name).read_text(encoding="utf-8")
    check(f"**{CODE}**" in t, f"{name}: the code {CODE} is shown")
    sec = re.search(r'<a id="install-downloader"></a>(.*?)(?=\n<p align="center"><a href="https://github.com/EMCHOUIT/ChouiTV/releases/latest">)', t, re.S)
    check(sec is not None, f"{name}: a section anchored install-downloader, before the download button")
    if sec:
        body = sec.group(1)
        check(CODE in body and STABLE in body, f"{name}: the section gives the code and the full address as the fallback")
        check("Downloader" in body, f"{name}: the section names the Downloader app")
        check("ChouiTV-Installer" not in body or "Companion" in body, f"{name}: the section points to the phone app by its name")
    check("(#install-downloader)" in t, f"{name}: the options table links to the section")
    intro = re.sub(r"<img[^>]*>", "", t.split('<a id="install-phone">')[0].split('<a id="install"></a>')[-1])   # the guide picture still shows three
    check(NEW_COUNT[name] in intro and OLD_COUNT[name] not in intro, f"{name}: the intro text counts four ways, not three")
    check(t.count(CODE) == 2, f"{name}: the code appears exactly twice (table row has none; text and bold), found {t.count(CODE)}")
sys.exit(1 if failed else 0)

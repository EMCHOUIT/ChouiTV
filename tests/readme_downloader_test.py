#!/usr/bin/env python3
"""Install option 4 in the three READMEs (ar, fr, en): from the TV itself with the Downloader app and its short code, which points to the
stable link of the latest release (the link never changes from one version to the next, so neither does the code). The full address stays
beside the code as the fallback. Each language has five explanatory pictures (docs/media/guide-downloader-N-{ar,fr,en}.png), one per
step, for people who do not know GitHub. Run: python3 tests/readme_downloader_test.py"""
import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CODE = "9833321"
STABLE = "https://github.com/EMCHOUIT/ChouiTV/releases/latest/download/chouitv-home.apk"
LANGS = {  # readme: (picture suffix, "three ways" words, "four ways" words, "not a password" words, "no GitHub account" words)
    "README.md": ("ar", "ثلاث طرق", "أربع طرق", "ليس كلمة سر", "حساب GitHub"),
    "README.fr.md": ("fr", "trois façons", "quatre façons", "pas un mot de passe", "compte GitHub"),
    "README.en.md": ("en", "three ways", "four ways", "not a password", "GitHub account"),
}
failed = 0

def check(ok, msg):
    global failed
    print(("PASS " if ok else "FAIL ") + msg)
    failed += not ok

for name, (lang, old_count, new_count, no_pw, no_gh) in LANGS.items():
    t = (ROOT / name).read_text(encoding="utf-8")
    check(f"**{CODE}**" in t, f"{name}: the code {CODE} is shown")
    sec = re.search(r'<a id="install-downloader"></a>(.*?)(?=\n<p align="center"><a href="https://github.com/EMCHOUIT/ChouiTV/releases/latest">)', t, re.S)
    check(sec is not None, f"{name}: a section anchored install-downloader, before the download button")
    if sec:
        body = sec.group(1)
        check(CODE in body and STABLE in body, f"{name}: the section gives the code and the full address as the fallback")
        check("Downloader" in body, f"{name}: the section names the Downloader app")
        check("(#install-phone)" in body and "Companion" in body, f"{name}: the section points to option 1 (ChouiTV Companion)")
        imgs = re.findall(r'<img src="([^"]+)" alt="([^"]*)"', body)
        check(len(imgs) == 5, f"{name}: five step pictures in the section, found {len(imgs)}")
        for n, (src, alt) in enumerate(imgs, 1):
            check(src == f"docs/media/guide-downloader-{n}-{lang}.png", f"{name}: picture {n} is docs/media/guide-downloader-{n}-{lang}.png ({src})")
            f = ROOT / src
            ok = f.exists() and f.read_bytes()[:8] == b"\x89PNG\r\n\x1a\n"
            check(ok, f"{name}: picture {n} exists and is a PNG")
            if ok:
                w, h = struct.unpack(">II", f.read_bytes()[16:24])
                check((w, h) == (1200, 640), f"{name}: picture {n} is 1200x640 ({w}x{h})")
            check(len(alt) > 15, f"{name}: picture {n} has an alternative text")
        if len(imgs) == 5:
            check(CODE in imgs[1][1], f"{name}: the alternative text of step 2 gives the code")
            check("github.com/EMCHOUIT/ChouiTV/releases/latest/download/chouitv-home.apk" in imgs[4][1], f"{name}: the alternative text of the fallback picture gives the address")
        check(no_pw in body.lower() and no_gh.lower() in body.lower(), f"{name}: says the code is not a password and needs no GitHub account")
    check("(#install-downloader)" in t, f"{name}: the options table links to the section")
    intro = re.sub(r"<img[^>]*>", "", t.split('<a id="install-phone">')[0].split('<a id="install"></a>')[-1])   # the guide picture still shows three
    check(new_count in intro and old_count not in intro, f"{name}: the intro text counts four ways, not three")
sys.exit(1 if failed else 0)

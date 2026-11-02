#!/usr/bin/env python3
"""The Installer QR (Settings > الرئيسية والنظام): the picture decodes to the stable link, is square and big, black on white,
the link is the one of the README, and the settings screen has the row and the dialog. Decoding needs OpenCV (skipped without)."""
import os
import re
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PNG = os.path.join(ROOT, "android/launcher/src/main/res/drawable-nodpi/qr_installer.png")
LINK = "https://github.com/EMCHOUIT/ChouiTV-Installer/releases/latest/download/ChouiTV-Installer.apk"
fails = []


def check(name, ok):
    print(("ok   " if ok else "FAIL ") + name)
    if not ok:
        fails.append(name)


def read(rel):
    with open(os.path.join(ROOT, rel), encoding="utf-8") as f:
        return f.read()


def png_header(path):
    with open(path, "rb") as f:
        data = f.read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    w, h, depth, ctype = struct.unpack(">IIBB", data[16:26])
    return data, w, h, depth, ctype


check("the picture is there", os.path.exists(PNG))
data, w, h, depth, ctype = png_header(PNG)
check("square", w == h)
check("at least 800 px", w >= 800)
check("RGB without alpha (colour type 2, 8 bits)", ctype == 2 and depth == 8)

# the link in the generator, in the screen and in the READMEs is one and the same
gen = read("optimize/make_qr.py")
check("generator encodes the stable link", ('LINK = "%s"' % LINK) in gen)
check("generator: error correction M or higher, quiet zone 4",
      re.search(r"ERROR_CORRECT_[MQH]", gen) is not None and "QUIET = 4" in gen)
settings = read("android/launcher/src/main/java/ma/chouitv/screensaver/SettingsActivity.java")
check("settings shows the same link", LINK in settings)
check("README (ar) links the same file", LINK in read("README.md"))
check("README (en) links the same file", LINK in read("README.en.md"))

# the screen: a row near the version line, a glass dialog, a picture at least 360 dp, back closes
check("settings row title", "تحميل ChouiTV Installer لتلفزيون آخر" in settings)
check("settings row subtitle", "اعرض الرمز على ضيوفك ليثبّتوا ChouiTV على تلفزيونهم" in settings)
check("dialog line under the code", "امسح الرمز بهاتفك: يُنزَّل ChouiTV Installer على هاتفك" in settings)
check("the row opens the explanation first, then the code", "showInstallerHelp(); }" in settings and "(x, w) -> showInstallerQr()" in settings)
for words in ("١  امسح الرمز بهاتفك", "٢  ثبّت ChouiTV Installer على هاتفك", "٣  من الهاتف، ثبّت ChouiTV على تلفزيونك (على نفس شبكة الواي فاي)",
              "لا يُثبَّت شيء على هذا التلفزيون", "عرض الرمز"):
    check("explanation says «%s»" % words, words in settings)
check("the button gets the focus", "go.requestFocus()" in settings)
check("the dialog uses the picture", "R.drawable.qr_installer" in settings)
check("the code is at least 360 dp", re.search(r"QR_DP\s*=\s*(\d+)", settings) is not None
      and int(re.search(r"QR_DP\s*=\s*(\d+)", settings).group(1)) >= 360)
check("the row comes before the version line", settings.index("qrInstallerRow()") < settings.index("note(last, Updater.versionLines"))

# all pixels pure black or pure white
try:
    import cv2
    import numpy as np
except ImportError:
    cv2 = None
if cv2 is None:
    print("skip: OpenCV (cv2) is missing, the code is not decoded here (run it with ~/mvenv/bin/python)")
else:
    img = cv2.imread(PNG, cv2.IMREAD_COLOR)
    check("opens as a colour picture", img is not None and img.shape[0] == h and img.shape[1] == w)
    flat = img.reshape(-1, 3)
    check("only black and white", bool(np.all((flat == 0).all(axis=1) | (flat == 255).all(axis=1))))
    text, _, _ = cv2.QRCodeDetector().detectAndDecode(img)
    check("decodes to the link", text == LINK)
    small = cv2.resize(img, (w // 3, h // 3), interpolation=cv2.INTER_AREA)   # a phone far away sees it smaller
    text2, _, _ = cv2.QRCodeDetector().detectAndDecode(small)
    check("still decodes at a third of the size", text2 == LINK)

print("FAILED: %s" % fails if fails else "all ok")
sys.exit(1 if fails else 0)

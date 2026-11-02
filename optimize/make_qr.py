#!/usr/bin/env python3
"""Makes the QR code of «تحميل ChouiTV Installer لتلفزيون آخر» (Settings, tab الرئيسية والنظام).

The app has no QR library and must not get one: the code is a picture made here, once, and shipped as a resource
(android/launcher/src/main/res/drawable-nodpi/qr_installer.png). Run again only if LINK changes:

    ~/mvenv/bin/pip install qrcode pillow      # once
    ~/mvenv/bin/python optimize/make_qr.py

The picture: 900x900, RGB without alpha, pure black modules on white, error correction M, quiet zone of 4 modules, every
module a whole number of pixels (the largest that fits; the rest is white margin, split evenly), so a phone reads it from
the sofa. tests/qr_installer_test.py decodes it again and compares it with LINK.
"""
import os
import sys

# The stable alias of the latest release: it always gives the newest installer (see the installer's release assets).
LINK = "https://github.com/EMCHOUIT/ChouiTV-Installer/releases/latest/download/ChouiTV-Installer.apk"
SIZE = 900
QUIET = 4
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "android", "launcher", "src", "main", "res",
                   "drawable-nodpi", "qr_installer.png")


def build(link=LINK):
    import qrcode
    from PIL import Image
    qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, border=0)
    qr.add_data(link)
    qr.make(fit=True)
    grid = qr.get_matrix()
    n = len(grid)
    box = SIZE // (n + 2 * QUIET)       # whole pixels per module
    if box < 8:
        sys.exit("the link is too long for a code that big")
    side = box * n
    start = (SIZE - side) // 2           # the quiet zone is at least QUIET modules; the spare pixels widen it
    img = Image.new("RGB", (SIZE, SIZE), (255, 255, 255))
    px = img.load()
    for r in range(n):
        for c in range(n):
            if grid[r][c]:
                for y in range(box):
                    for x in range(box):
                        px[start + c * box + x, start + r * box + y] = (0, 0, 0)
    return img, n, box


if __name__ == "__main__":
    image, modules, box_px = build()
    path = os.path.normpath(OUT)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, optimize=True)
    print("%s: %d modules, %d px each, %dx%d" % (path, modules, box_px, SIZE, SIZE))

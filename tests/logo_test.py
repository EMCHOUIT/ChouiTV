"""The new ChouiTV logo (v16, piste A: zellige star + play) and the new banner text. Files only; no TV needed.

    python3 tests/logo_test.py
"""
import hashlib, os, struct, sys, zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android/launcher/src/main/res")
BRAND = os.path.join(ROOT, ".github/brand")
FAILS = []

def check(cond, msg):
    if not cond:
        FAILS.append(msg)
        print("FAIL", msg)

def read(path):
    with open(path, "rb") as f:
        return f.read()

def png(path):
    """(width, height, color_type, rows) of a non-interlaced 8-bit RGB/RGBA PNG; rows are lists of (r, g, b, a)."""
    data = read(path)
    check(data[:8] == b"\x89PNG\r\n\x1a\n", f"{os.path.relpath(path, ROOT)}: not a PNG")
    pos, idat, w = 8, b"", None
    while pos < len(data):
        n, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        if kind == b"IHDR":
            w, h, depth, ctype, _, _, inter = struct.unpack(">IIBBBBB", body)
            check(depth == 8 and ctype in (2, 6) and inter == 0, f"{os.path.relpath(path, ROOT)}: need 8-bit RGB(A), not interlaced")
        elif kind == b"IDAT":
            idat += body
        pos += 12 + n
    bpp = 4 if ctype == 6 else 3
    raw, stride, prev, rows, p = zlib.decompress(idat), w * bpp, bytearray(w * bpp), [], 0
    for _ in range(h):
        f, line = raw[p], bytearray(raw[p + 1:p + 1 + stride]); p += 1 + stride
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if f == 1: line[i] = (line[i] + a) & 255
            elif f == 2: line[i] = (line[i] + b) & 255
            elif f == 3: line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append([(line[x * bpp], line[x * bpp + 1], line[x * bpp + 2], line[x * bpp + 3] if bpp == 4 else 255) for x in range(w)])
        prev = line
    return w, h, ctype, rows

def gold_share(rows):
    px = [p for r in rows for p in r if p[3] > 200]
    return sum(1 for r, g, b, _ in px if r > 190 and 140 < g < 235 and b < 150) / max(1, len(px))

def dark_green_share(rows):
    px = [p for r in rows for p in r if p[3] > 200]
    return sum(1 for r, g, b, _ in px if r < 40 and g < 70 and b < 50 and g >= r) / max(1, len(px))

# the files of the OLD logo (screen + sun + dune): every one of them must have been redrawn
OLD = {
    "android/launcher/src/main/res/mipmap-xhdpi/ic_launcher.png": "3af3b0aaff206639b65ab7c399552ef8e97211f665a960511f54c2a714aa1326",
    "android/launcher/src/main/res/mipmap-xxhdpi/ic_launcher.png": "37ae5caf8aaa87623ce8e12d0a39222f7ee4196ae9178d5162c2b8245c536b2c",
    "android/launcher/src/main/res/mipmap-xxxhdpi/ic_launcher.png": "874d08eb68078058e3815681e1894493ba1fbefb5bb25a73096ae3259a5f58a4",
    "android/launcher/src/main/res/drawable-xhdpi/banner.png": "d388c6393d62c17d4f1dcb9536a5fbcf225f0629719791f95c715a4d0490614a",
    "android/launcher/src/main/res/drawable-xxhdpi/banner.png": "c2a34b57ee78dd5d5f2a0a60d546d247c00a263c628a1193fd88ee8c3682018c",
    ".github/brand/banner.png": "943e017a5df67a6b86c5a4d9d49d6749f0158f83c2ebe2f7b433c70903b0055f",
    ".github/brand/icon-512.png": "2068ce9e5330c00138baddc6576b12575691af21c67c155a6fc19939794c77e2",
    ".github/brand/hero.png": "ff4191368e3c59ce2ae26980ee24f7acb4be805d04ffc7ae884e1cc7fdda8476",
}
for rel, old in OLD.items():
    check(hashlib.sha256(read(os.path.join(ROOT, rel))).hexdigest() != old, f"{rel}: still the old logo")

# sizes Android needs
SIZES = {
    "android/launcher/src/main/res/mipmap-xhdpi/ic_launcher.png": (96, 96),
    "android/launcher/src/main/res/mipmap-xxhdpi/ic_launcher.png": (144, 144),
    "android/launcher/src/main/res/mipmap-xxxhdpi/ic_launcher.png": (192, 192),
    "android/launcher/src/main/res/drawable-xhdpi/banner.png": (320, 180),
    "android/launcher/src/main/res/drawable-xxhdpi/banner.png": (480, 270),
    ".github/brand/banner.png": (640, 360),
    ".github/brand/icon-512.png": (512, 512),
    ".github/brand/hero.png": (1600, 500),
}
for rel, (ew, eh) in SIZES.items():
    w, h, ctype, rows = png(os.path.join(ROOT, rel))
    check((w, h) == (ew, eh), f"{rel}: {w}x{h}, expected {ew}x{eh}")
    check(0.01 < gold_share(rows) < 0.6, f"{rel}: gold share {gold_share(rows):.3f} out of range (no gold mark?)")
    check(dark_green_share(rows) > 0.25, f"{rel}: not mostly dark green")
    if rel.endswith("ic_launcher.png") or rel.endswith("icon-512.png"):
        check(ctype == 6 and rows[0][0][3] < 40, f"{rel}: rounded-square icon should have transparent corners")

# vector sources
svg = read(os.path.join(BRAND, "mark.svg")).decode("utf-8")
check('id="khatam"' in svg and 'id="play"' in svg, "mark.svg: needs the star (id=khatam) and the play button (id=play)")
check("clip-path" not in svg and "screen" not in svg.lower(), "mark.svg: still the old TV screen")
check(os.path.exists(os.path.join(BRAND, "banner.svg")), ".github/brand/banner.svg missing (source of the banners)")
if os.path.exists(os.path.join(BRAND, "banner.svg")):
    b = read(os.path.join(BRAND, "banner.svg")).decode("utf-8")
    check("ChouiTV" in b and "شويتيفي" in b, "banner.svg: needs ChouiTV and شويتيفي")
    check("UrTV" not in b and "تلفزيونك" not in b, "banner.svg: old name left")
    check("Thmanyah" in b, "banner.svg: should use the Thmanyah font")

check(os.path.exists(os.path.join(BRAND, "hero.svg")), ".github/brand/hero.svg missing (source of hero.png)")
if os.path.exists(os.path.join(BRAND, "hero.svg")):
    hs = read(os.path.join(BRAND, "hero.svg")).decode("utf-8")
    check("ChouiTV" in hs and "UrTV" not in hs and "تلفزيونك" not in hs.replace("غيّر تلفزيونك", ""),
          "hero.svg: needs ChouiTV, and no UrTV / old name outside the slogan")
    check("Saudi" not in hs, "hero.svg: the old Saudi tagline is left")
    check("Change Your TV" in hs and "غيّر تلفزيونك" in hs, "hero.svg: the slogan is «Change Your TV» / «غيّر تلفزيونك»")
    check("Moroccan touch" not in hs, "hero.svg: the previous tagline must be replaced by the slogan")

# the name shown under the icon on the TV
import re
manifest = read(os.path.join(ROOT, "android/launcher/src/main/AndroidManifest.xml")).decode("utf-8")
app = re.search(r"<application\b[^>]*>", manifest, re.S).group(0)
label = re.search(r'android:label="([^"]*)"', app).group(1)
check(label == "ChouiTV", f"AndroidManifest application label is {label!r}, expected 'ChouiTV'")

# the two other names the TV shows (accessibility service, screensaver), and the messages that quote them
LAUNCHER = os.path.join(ROOT, "android/launcher/src/main")
strings = read(os.path.join(LAUNCHER, "res/values/strings.xml")).decode("utf-8")
sleep = re.search(r'<string name="sleep_service_label">([^<]*)</string>', strings).group(1)
saver = re.search(r'<string name="saver_name">([^<]*)</string>', strings).group(1)
check("تلفزيونك" not in sleep and "ChouiTV" in sleep, f"sleep_service_label is {sleep!r}: should carry ChouiTV, not the old name")
check("تلفزيونك" not in saver and "ChouiTV" in saver, f"saver_name is {saver!r}: should carry ChouiTV, not the old name")
main_java = read(os.path.join(LAUNCHER, "java/ma/chouitv/home/MainActivity.java")).decode("utf-8")
check("«" + sleep + "»" in main_java, "MainActivity: the hint must quote the accessibility service by its real name (sleep_service_label)")
settings_java = read(os.path.join(LAUNCHER, "java/ma/chouitv/screensaver/SettingsActivity.java")).decode("utf-8")
check("«" + saver + "»" in settings_java, "SettingsActivity: the migration dialog must quote the new screensaver by its real name (saver_name)")
check("«شاشة تلفزيونك»" in settings_java, "SettingsActivity: the OLD app is still called «شاشة تلفزيونك» in the migration texts")

# the app's own name in the screens and toasts: ChouiTV. The word «تلفزيونك» may stay only where it means "your TV"
# (a plain word) or names the OLD app in the migration texts.
def src(rel):
    return read(os.path.join(LAUNCHER, "java/ma/chouitv", rel)).decode("utf-8")
upd, selfupd = src("home/UpdateActivity.java"), src("home/SelfUpdate.java")
for rel_text, old in ((settings_java, 'title.setText("تلفزيونك")'), (settings_java, 'header("الشاشة الرئيسية (تلفزيونك)")'),
                      (settings_java, "ثبّت الواجهة الرئيسية «تلفزيونك»"), (upd, 'title.setText("تحديث تلفزيونك")'),
                      (selfupd, '"تحديث تلفزيونك "'), (selfupd, "أُلغي تحديث تلفزيونك"), (selfupd, "لم يكتمل تحديث تلفزيونك")):
    check(old not in rel_text, f"old app name still shown: {old}")
for rel_text, new in ((settings_java, 'title.setText("ChouiTV")'), (settings_java, "الشاشة الرئيسية (ChouiTV)"),
                      (settings_java, "ثبّت الواجهة الرئيسية «ChouiTV»"), (upd, 'title.setText("تحديث ChouiTV")'),
                      (selfupd, '"تحديث ChouiTV "'), (selfupd, "أُلغي تحديث ChouiTV"), (selfupd, "لم يكتمل تحديث ChouiTV")):
    check(new in rel_text, f"missing new text: {new}")
check("على تلفزيونك" in settings_java and "لتلفزيونك" in settings_java, "the plain word «your TV» must stay where it means 'your TV'")

if FAILS:
    print(f"logo tests: {len(FAILS)} failure(s)")
    sys.exit(1)
print("logo tests: all passed")

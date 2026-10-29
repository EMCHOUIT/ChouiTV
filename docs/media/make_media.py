"""Builds the README pictures of ChouiTV from real TV screenshots (full size PNGs in RAW) and open-licence fonts (Noto Kufi Arabic, OFL).
Run: python make_media.py RAW OUT      (Pillow with raqm is needed for the Arabic shaping)
One group only: ONLY=hero|guide|misc|shots python make_media.py RAW OUT   (several: ONLY=hero,guide)
The release-notes pictures are made by whatsnew.py."""
import os, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops

RAW = os.path.expanduser(sys.argv[1] if len(sys.argv) > 1 else "~/media_raw")
OUT = os.path.expanduser(sys.argv[2] if len(sys.argv) > 2 else "~/chouitv-readme/docs/media")
FONT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fonts", "NotoKufiArabic[wght].ttf")
os.makedirs(OUT, exist_ok=True)
GOLD, GREEN, DARK, SAND = (220, 184, 111), (63, 122, 92), (10, 29, 20), (246, 239, 224)

def font(size, weight=600):
    f = ImageFont.truetype(FONT, size, layout_engine=ImageFont.Layout.RAQM)
    try: f.set_variation_by_axes([weight])
    except Exception: pass
    return f

def text(d, xy, s, size, fill=SAND, weight=600, anchor="ra"):
    d.text(xy, s, font=font(size, weight), fill=fill, anchor=anchor, direction="rtl", language="ar", features=["kern", "liga"])

def load(name):
    return clean(Image.open(os.path.join(RAW, name + ".png")).convert("RGB"))

_MASK = None
def icon_mask():
    """The mute icon is in the same place on every capture: its shape (grown by 5 px for the soft edge) comes from one plain corner."""
    global _MASK
    if _MASK is None:
        # l_ma_old.png is a capture that has the icon; a newer l_ma.png may not have it (then the mask would be empty)
        ref_name = "l_ma_old" if os.path.exists(os.path.join(RAW, "l_ma_old.png")) else "l_ma"
        ref = Image.open(os.path.join(RAW, ref_name + ".png")).convert("RGB")
        px = ref.load()
        box = (1780, 940, 1910, 1050)
        m = {(x, y) for x in range(box[0], box[2]) for y in range(box[1], box[3]) if min(px[x, y]) > 175}
        grown = set()
        for (x, y) in m:
            for dx in range(-5, 6):
                for dy in range(-5, 6):
                    grown.add((x + dx, y + dy))
        _MASK = grown
    return _MASK

def clean(im):
    """The TV draws its own mute icon over the bottom right corner: paint it out from the pixels around it."""
    px = im.load()
    mask = set(icon_mask())
    for _ in range(80):
        if not mask: break
        done = set()
        for (x, y) in mask:
            ns = [px[x + dx, y + dy] for dx in (-2, -1, 0, 1, 2) for dy in (-2, -1, 0, 1, 2) if (x + dx, y + dy) not in mask]
            if len(ns) >= 5:
                px[x, y] = tuple(sum(c[i] for c in ns) // len(ns) for i in range(3))
                done.add((x, y))
        mask -= done
    return im

def rounded(im, r):
    m = Image.new("L", im.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, im.width - 1, im.height - 1), r, fill=255)
    out = im.convert("RGBA"); out.putalpha(m)
    return out

def bezel(shot, width=1500, bg=None, pad=26):
    """The screenshot inside a thin TV frame with a soft shadow and a small stand."""
    h = round(width * 9 / 16)
    s = rounded(shot.resize((width, h), Image.LANCZOS), 10)
    fw, fh = width + 2 * pad, h + 2 * pad + 10
    W, H = fw + 120, fh + 120
    canvas = Image.new("RGBA", (W, H), bg or (0, 0, 0, 0))
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((60, 78, 60 + fw, 78 + fh), 22, fill=(0, 0, 0, 170))
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(26)))
    d = ImageDraw.Draw(canvas)
    d.rounded_rectangle((60, 60, 60 + fw, 60 + fh), 22, fill=(22, 34, 27), outline=(48, 72, 58), width=3)
    canvas.alpha_composite(s, (60 + pad, 60 + pad))
    d.rounded_rectangle((60 + fw // 2 - 90, 60 + fh - 14, 60 + fw // 2 + 90, 60 + fh - 8), 3, fill=(58, 84, 68))
    return canvas

def mark(im, n, xy, label, side="right"):
    """A numbered gold disc on the picture (coordinates in the full 1920x1080 frame)."""
    d = ImageDraw.Draw(im)
    x, y = xy
    d.ellipse((x - 26, y - 26, x + 26, y + 26), fill=GOLD + (255,), outline=(10, 29, 20), width=4)
    d.text((x, y + 1), str(n), font=font(30, 700), fill=DARK, anchor="mm")

def annotated(name, marks, out):
    shot = load(name)
    d = ImageDraw.Draw(shot)
    for n, xy in marks:
        mark(shot, n, xy, "")
    bezel(shot, 1500).save(os.path.join(OUT, out))

def save_bezel(name, out, width=1500):
    bezel(load(name), width).save(os.path.join(OUT, out))

def gif(frames, out, ms):
    frames[0].save(os.path.join(OUT, out), save_all=True, append_images=frames[1:], duration=ms, loop=0, optimize=True, disposal=2)

# ---------------------------------------------------------------- the cover, the dividers, the numbers, the button
import math, random
from PIL import ImageChops

def lin_grad(size, c1, c2):
    g = Image.new("RGB", (1, 256))
    for i in range(256):
        t = i / 255
        g.putpixel((0, i), tuple(int(c1[k] + (c2[k] - c1[k]) * t) for k in range(3)))
    return g.resize(size, Image.BICUBIC)

def zellige(size, step=130, color=GOLD, alpha=24):
    """A faint Moroccan star lattice: an eight-pointed star (two squares) in every cell, joined by thin lines."""
    w, h = size
    lay = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    r = step * 0.34
    for row, y in enumerate(range(-step, h + step, step)):
        for x in range(-step, w + step, step):
            cx, cy = x + (step / 2 if row % 2 else 0), y
            for rot in (0, math.pi / 4):
                d.polygon([(cx + r * math.cos(rot + k * math.pi / 2), cy + r * math.sin(rot + k * math.pi / 2)) for k in range(4)], outline=color + (alpha,))
            d.ellipse((cx - 4, cy - 4, cx + 4, cy + 4), outline=color + (alpha,))
            d.line((cx + r, cy, cx + step - r, cy), fill=color + (alpha // 2,))
    return lay

def metal(canvas, xy, s, fnt, top=(255, 240, 196), bottom=(178, 126, 48), glow=GOLD, anchor="ra", rtl=False, glow_amount=0.8):
    """Text filled with a vertical gold gradient, with an outer glow, a dark edge and a shine on its upper half."""
    W, H = canvas.size
    kw = dict(font=fnt, anchor=anchor)
    if rtl: kw.update(direction="rtl", language="ar", features=["kern", "liga"])
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).text(xy, s, fill=255, **kw)
    b = mask.getbbox()
    if not b: return
    halo = Image.new("RGBA", (W, H), glow + (0,))
    halo.putalpha(mask.filter(ImageFilter.GaussianBlur(22)).point(lambda v: int(v * glow_amount)))
    canvas.alpha_composite(halo)
    edge = Image.new("RGBA", (W, H), (18, 12, 4, 0))
    edge.putalpha(mask.filter(ImageFilter.MaxFilter(5)).point(lambda v: int(v * 0.85)))
    canvas.alpha_composite(edge, (3, 5))
    fill = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    fill.paste(lin_grad((b[2] - b[0], b[3] - b[1]), top, bottom).convert("RGBA"), (b[0], b[1]))
    fill.putalpha(mask)
    canvas.alpha_composite(fill)
    shine = Image.new("L", (W, H), 0)
    ImageDraw.Draw(shine).rectangle((b[0], b[1], b[2], b[1] + int((b[3] - b[1]) * 0.40)), fill=60)
    sh = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    sh.putalpha(ImageChops.multiply(shine, mask))
    canvas.alpha_composite(sh)

def sparkles(canvas, n, box, seed=7, color=(255, 236, 190), avoid=None):
    rnd = random.Random(seed)
    lay = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    for _ in range(n):
        x, y = rnd.uniform(box[0], box[2]), rnd.uniform(box[1], box[3])
        if avoid and avoid[0] <= x <= avoid[2] and avoid[1] <= y <= avoid[3]: continue
        s = rnd.choice([1, 1, 1, 2, 2, 3, 6])
        a = rnd.randint(50, 190)
        if s >= 6:
            d.line((x - 16, y, x + 16, y), fill=color + (a,), width=2)
            d.line((x, y - 16, x, y + 16), fill=color + (a,), width=2)
            d.ellipse((x - 3, y - 3, x + 3, y + 3), fill=color + (255,))
        else:
            d.ellipse((x - s, y - s, x + s, y + s), fill=color + (a,))
    canvas.alpha_composite(lay.filter(ImageFilter.GaussianBlur(0.7)))

def icon(canvas, kind, cx, cy, r=34):
    """Small drawn icons in a gold disc: beads, crescent, waves, download, star."""
    d = ImageDraw.Draw(canvas)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(28, 52, 38, 255), outline=GOLD + (255,), width=3)
    g = GOLD + (255,)
    if kind == "beads":
        for k in range(9):
            a = math.pi * 0.15 + k * (math.pi * 1.7 / 8)
            x, y = cx + r * 0.55 * math.cos(a), cy - 4 + r * 0.55 * math.sin(a)
            d.ellipse((x - 4, y - 4, x + 4, y + 4), fill=g)
        d.line((cx, cy + r * 0.45, cx, cy + r * 0.78), fill=g, width=3)
    elif kind == "moon":
        m = Image.new("L", canvas.size, 0)
        md = ImageDraw.Draw(m)
        md.ellipse((cx - r * 0.55, cy - r * 0.55, cx + r * 0.55, cy + r * 0.55), fill=255)
        md.ellipse((cx - r * 0.30, cy - r * 0.68, cx + r * 0.72, cy + r * 0.40), fill=0)
        lay = Image.new("RGBA", canvas.size, g); lay.putalpha(m); canvas.alpha_composite(lay)
        d.ellipse((cx + r * 0.25 - 4, cy - r * 0.05 - 4, cx + r * 0.25 + 4, cy - r * 0.05 + 4), fill=g)
    elif kind == "waves":
        d.ellipse((cx - 5, cy - 5, cx + 5, cy + 5), fill=g)
        for k, rr in enumerate((14, 23)):
            d.arc((cx - rr, cy - rr, cx + rr, cy + rr), -50, 50, fill=g, width=3)
            d.arc((cx - rr, cy - rr, cx + rr, cy + rr), 130, 230, fill=g, width=3)
    elif kind == "down":
        d.line((cx, cy - r * 0.5, cx, cy + r * 0.25), fill=g, width=4)
        d.line((cx - r * 0.32, cy, cx, cy + r * 0.3), fill=g, width=4)
        d.line((cx + r * 0.32, cy, cx, cy + r * 0.3), fill=g, width=4)
        d.line((cx - r * 0.45, cy + r * 0.52, cx + r * 0.45, cy + r * 0.52), fill=g, width=4)
    elif kind == "star":
        rr = r * 0.5
        for rot in (0, math.pi / 4):
            d.polygon([(cx + rr * math.cos(rot + k * math.pi / 2), cy + rr * math.sin(rot + k * math.pi / 2)) for k in range(4)], fill=g)

def hero():
    W, H = 1800, 920
    bg = Image.new("RGBA", (W, H), (6, 20, 13, 255))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((-300, -380, 900, 520), fill=GOLD + (80,))
    gd.ellipse((1000, 420, 2100, 1250), fill=GREEN + (120,))
    gd.ellipse((380, 200, 900, 760), fill=GOLD + (40,))
    bg.alpha_composite(glow.filter(ImageFilter.GaussianBlur(140)))
    rays = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    rd = ImageDraw.Draw(rays)
    for k in range(7):
        a = math.radians(200 + k * 9)
        rd.polygon([(W + 40, -60), (W + 40 + 2400 * math.cos(a), -60 - 2400 * math.sin(a) * -1), (W + 40 + 2400 * math.cos(a + 0.035), -60 + 2400 * math.sin(a + 0.035) * -1)], fill=(255, 236, 190, 16))
    bg.alpha_composite(rays.filter(ImageFilter.GaussianBlur(18)))
    z = zellige((W, H))
    fade = Image.new("L", (W, H), 0)
    ImageDraw.Draw(fade).ellipse((700, -500, 2600, 1400), fill=255)
    z.putalpha(ImageChops.multiply(z.getchannel("A"), fade.filter(ImageFilter.GaussianBlur(160))))
    bg.alpha_composite(z)
    # the TV, with a golden halo behind it and its reflection on the floor
    tv = bezel(load("tb_8"), 700)
    halo = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(halo).ellipse((40, 200, 860, 720), fill=GOLD + (70,))
    bg.alpha_composite(halo.filter(ImageFilter.GaussianBlur(90)))
    tx, ty = 10, 210
    bg.alpha_composite(tv, (tx, ty))
    refl = tv.transpose(Image.FLIP_TOP_BOTTOM).crop((0, 0, tv.width, 220))
    ra = Image.new("L", refl.size, 0)
    for y in range(refl.height):
        ImageDraw.Draw(ra).line((0, y, refl.width, y), fill=int(70 * (1 - y / refl.height)))
    refl.putalpha(ImageChops.multiply(refl.getchannel("A"), ra))
    bg.alpha_composite(refl, (tx, ty + tv.height - 40))
    # the words
    R = W - 90
    d = ImageDraw.Draw(bg)
    pill = "جديد · الإصدار 4.0"
    pw = int(d.textlength(pill, font=font(28, 600), direction="rtl", language="ar")) + 120
    d.rounded_rectangle((R - pw, 92, R, 146), 27, fill=(28, 52, 38, 230), outline=GOLD + (220,), width=2)
    text(d, (R - 45, 119), pill, 28, GOLD, 600, anchor="rm")
    icon(bg, "star", R - pw + 34, 119, 15)
    metal(bg, (R + 6, 168), "ChouiTV", script_font(212), glow_amount=0.95)
    swash(bg, R - 600, R - 20, 412)
    metal(bg, (R, 440), "تلفزيونك بلمسة مغربية", ruqaa(84), top=(255, 250, 236), bottom=(222, 196, 140), glow=(255, 236, 190), rtl=True, glow_amount=0.45)
    feats = [("beads", "تسبيح موجَّه تتبعه بعينك"), ("moon", "آيات وأذكار وحكمة من التاريخ"), ("waves", "قرآن وإذاعات مغربية مباشرة")]
    for i, (k, s) in enumerate(feats):
        y = 600 + i * 64
        icon(bg, k, R - 30, y, 28)
        text(d, (R - 78, y), s, 38, SAND, 600, anchor="rm")
    # the call to action
    bx1, by1, bx2, by2 = R - 560, 790, R, 872
    btn = lin_grad((bx2 - bx1, by2 - by1), (255, 226, 150), (196, 142, 58)).convert("RGBA")
    bm = Image.new("L", btn.size, 0)
    ImageDraw.Draw(bm).rounded_rectangle((0, 0, btn.width - 1, btn.height - 1), 41, fill=255)
    btn.putalpha(bm)
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle((bx1 + 6, by1 + 14, bx2 + 6, by2 + 14), 41, fill=(0, 0, 0, 150))
    bg.alpha_composite(sh.filter(ImageFilter.GaussianBlur(14)))
    gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(gl).rounded_rectangle((bx1, by1, bx2, by2), 41, fill=GOLD + (160,))
    bg.alpha_composite(gl.filter(ImageFilter.GaussianBlur(24)))
    bg.alpha_composite(btn, (bx1, by1))
    d = ImageDraw.Draw(bg)
    text(d, (bx2 - 110, (by1 + by2) // 2), "ثبّته الآن مجانًا", 40, DARK, 800, anchor="rm")
    d.ellipse((bx2 - 92, by1 + 13, bx2 - 36, by2 - 13), fill=DARK)
    icon(bg, "down", bx2 - 64, (by1 + by2) // 2, 24)
    sparkles(bg, 120, (0, 0, W, H), seed=11, avoid=(tx + 40, ty + 40, tx + tv.width - 40, ty + tv.height - 40))
    vig = Image.new("L", (W, H), 0)
    ImageDraw.Draw(vig).rectangle((0, 0, W, H), fill=255)
    vig = ImageChops.subtract(vig, Image.new("L", (W, H), 0))
    edge = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(edge).rectangle((0, 0, W, H), outline=(0, 0, 0, 160), width=60)
    bg.alpha_composite(edge.filter(ImageFilter.GaussianBlur(50)))
    bg.convert("RGB").save(os.path.join(OUT, "hero.png"), optimize=True)

def divider():
    W, H = 1600, 70
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for x in range(0, W // 2 - 60):
        a = int(200 * (x / (W // 2)) ** 1.6)
        d.point((x, H // 2), fill=GOLD + (a,))
        d.point((W - 1 - x, H // 2), fill=GOLD + (a,))
    cx, cy = W // 2, H // 2
    for rot in (0, math.pi / 4):
        d.polygon([(cx + 22 * math.cos(rot + k * math.pi / 2), cy + 22 * math.sin(rot + k * math.pi / 2)) for k in range(4)], outline=GOLD + (255,), width=2)
    d.ellipse((cx - 5, cy - 5, cx + 5, cy + 5), fill=GOLD + (255,))
    for s in (-1, 1):
        x = cx + s * 46
        d.polygon([(x, cy - 6), (x + 6, cy), (x, cy + 6), (x - 6, cy)], fill=GOLD + (220,))
    im.save(os.path.join(OUT, "divider.png"), optimize=True)

def stats():
    W, H = 1600, 250
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    panel = lin_grad((W, H), (16, 38, 27), (8, 22, 15)).convert("RGBA")
    m = Image.new("L", (W, H), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, W - 1, H - 1), 28, fill=255)
    panel.putalpha(m)
    im.alpha_composite(panel)
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, W - 1, H - 1), 28, outline=GOLD + (120,), width=2)
    items = [("12", "صيغة تسبيح"), ("311", "قناة عربية"), ("37", "إذاعة مغربية"), ("40", "قارئًا للقرآن"), ("90", "مدينة مغربية"), ("5", "تبويبات")]
    cw = W / len(items)
    for i, (n, lab) in enumerate(items):
        cx = W - cw * i - cw / 2
        metal(im, (cx, 105), n, font(96, 800), anchor="mm", glow_amount=0.5)
        text(d, (cx, 190), lab, 34, SAND, 600, anchor="mm")
        if i: d.line((W - cw * i, 50, W - cw * i, H - 50), fill=GOLD + (70,), width=2)
    im.save(os.path.join(OUT, "stats.png"), optimize=True)

def button():
    W, H = 900, 150
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(gl).rounded_rectangle((30, 28, W - 30, H - 28), 46, fill=GOLD + (170,))
    im.alpha_composite(gl.filter(ImageFilter.GaussianBlur(16)))
    btn = lin_grad((W - 80, H - 60), (255, 226, 150), (196, 142, 58)).convert("RGBA")
    bm = Image.new("L", btn.size, 0)
    ImageDraw.Draw(bm).rounded_rectangle((0, 0, btn.width - 1, btn.height - 1), 45, fill=255)
    btn.putalpha(bm)
    im.alpha_composite(btn, (40, 30))
    d = ImageDraw.Draw(im)
    text(d, (W - 150, H // 2), "ثبّت ChouiTV الآن", 46, DARK, 800, anchor="rm")
    d.ellipse((W - 130, 42, W - 64, 108), fill=DARK)
    icon(im, "down", W - 97, 75, 26)
    im.save(os.path.join(OUT, "install-button.png"), optimize=True)


# ---------------------------------------------------------------- handwriting, guide pictures, channels card
FDIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fonts")
def script_font(size): return ImageFont.truetype(os.path.join(FDIR, "GreatVibes-Regular.ttf"), size, layout_engine=ImageFont.Layout.RAQM)
def ruqaa(size): return ImageFont.truetype(os.path.join(FDIR, "ArefRuqaa-Bold.ttf"), size, layout_engine=ImageFont.Layout.RAQM)

def khatam(d, cx, cy, r, fill=None, outline=None, width=2):
    """The Moroccan eight-pointed star (two squares)."""
    for rot in (0, math.pi / 4):
        pts = [(cx + r * math.cos(rot + k * math.pi / 2), cy + r * math.sin(rot + k * math.pi / 2)) for k in range(4)]
        d.polygon(pts, fill=fill, outline=outline, width=width)

def swash(canvas, x1, x2, y, color=GOLD):
    """A hand-drawn underline that swells in the middle, with a star at its start."""
    lay = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    n = 160
    top, bot = [], []
    for i in range(n + 1):
        u = i / n
        x = x1 + (x2 - x1) * u
        yy = y + 10 * math.sin(u * math.pi * 2.0) * (1 - u)
        w = 0.8 + 7 * math.sin(u * math.pi) ** 1.4
        top.append((x, yy - w)); bot.append((x, yy + w))
    d.polygon(top + bot[::-1], fill=color + (255,))
    khatam(d, x1 - 24, y, 15, fill=color + (255,))
    canvas.alpha_composite(lay.filter(ImageFilter.GaussianBlur(0.6)))

def icon2(canvas, kind, cx, cy, r=60):
    """Line icons for the guide: tv, laptop, clip, cup, plug, down, trash, check, broadcast."""
    d = ImageDraw.Draw(canvas)
    g = GOLD + (255,)
    w = max(3, r // 12)
    if kind == "tv":
        d.rounded_rectangle((cx - r, cy - r * 0.62, cx + r, cy + r * 0.48), 10, outline=g, width=w)
        d.line((cx - r * 0.35, cy + r * 0.78, cx + r * 0.35, cy + r * 0.78), fill=g, width=w)
        d.line((cx, cy + r * 0.48, cx, cy + r * 0.78), fill=g, width=w)
        khatam(d, cx, cy - r * 0.07, r * 0.26, outline=g, width=w - 1)
    elif kind == "laptop":
        d.rounded_rectangle((cx - r * 0.78, cy - r * 0.62, cx + r * 0.78, cy + r * 0.32), 8, outline=g, width=w)
        d.polygon([(cx - r, cy + r * 0.44), (cx + r, cy + r * 0.44), (cx + r * 0.88, cy + r * 0.62), (cx - r * 0.88, cy + r * 0.62)], outline=g, width=w)
        d.text((cx, cy - r * 0.15), ">_", font=font(int(r * 0.5), 700), fill=g, anchor="mm")
    elif kind == "clip":
        d.rounded_rectangle((cx - r * 0.62, cy - r * 0.68, cx + r * 0.62, cy + r * 0.82), 10, outline=g, width=w)
        d.rounded_rectangle((cx - r * 0.3, cy - r * 0.84, cx + r * 0.3, cy - r * 0.56), 6, fill=(16, 38, 27, 255), outline=g, width=w)
        for k in range(3):
            d.line((cx - r * 0.36, cy - r * 0.2 + k * r * 0.32, cx + r * 0.36, cy - r * 0.2 + k * r * 0.32), fill=g, width=w)
    elif kind == "cup":
        d.rounded_rectangle((cx - r * 0.58, cy - r * 0.2, cx + r * 0.36, cy + r * 0.66), 14, outline=g, width=w)
        d.arc((cx + r * 0.2, cy - r * 0.05, cx + r * 0.72, cy + r * 0.45), -90, 90, fill=g, width=w)
        for k in range(3):
            x = cx - r * 0.38 + k * r * 0.26
            d.arc((x - 8, cy - r * 0.78, x + 8, cy - r * 0.32), 100, 260, fill=g, width=w - 1)
    elif kind == "plug":
        d.rounded_rectangle((cx - r * 0.42, cy - r * 0.3, cx + r * 0.42, cy + r * 0.38), 10, outline=g, width=w)
        for s in (-1, 1):
            d.line((cx + s * r * 0.2, cy - r * 0.3, cx + s * r * 0.2, cy - r * 0.68), fill=g, width=w)
        d.line((cx, cy + r * 0.38, cx, cy + r * 0.82), fill=g, width=w)
    elif kind == "down":
        d.line((cx, cy - r * 0.7, cx, cy + r * 0.3), fill=g, width=w)
        d.line((cx - r * 0.42, cy - r * 0.08, cx, cy + r * 0.34), fill=g, width=w)
        d.line((cx + r * 0.42, cy - r * 0.08, cx, cy + r * 0.34), fill=g, width=w)
        d.line((cx - r * 0.7, cy + r * 0.7, cx + r * 0.7, cy + r * 0.7), fill=g, width=w)
    elif kind == "trash":
        d.line((cx - r * 0.66, cy - r * 0.5, cx + r * 0.66, cy - r * 0.5), fill=g, width=w)
        d.rounded_rectangle((cx - r * 0.2, cy - r * 0.72, cx + r * 0.2, cy - r * 0.5), 4, outline=g, width=w)
        d.polygon([(cx - r * 0.52, cy - r * 0.4), (cx + r * 0.52, cy - r * 0.4), (cx + r * 0.4, cy + r * 0.78), (cx - r * 0.4, cy + r * 0.78)], outline=g, width=w)
    elif kind == "check":
        d.ellipse((cx - r * 0.8, cy - r * 0.8, cx + r * 0.8, cy + r * 0.8), outline=g, width=w)
        d.line((cx - r * 0.38, cy, cx - r * 0.08, cy + r * 0.3, cx + r * 0.42, cy - r * 0.3), fill=g, width=w + 1, joint="curve")
    elif kind == "broadcast":
        d.ellipse((cx - 9, cy - 9, cx + 9, cy + 9), fill=g)
        for rr in (r * 0.42, r * 0.72):
            d.arc((cx - rr, cy - rr, cx + rr, cy + rr), -45, 45, fill=g, width=w)
            d.arc((cx - rr, cy - rr, cx + rr, cy + rr), 135, 225, fill=g, width=w)
        d.line((cx, cy, cx - r * 0.35, cy + r * 0.85), fill=g, width=w)
        d.line((cx, cy, cx + r * 0.35, cy + r * 0.85), fill=g, width=w)
    elif kind == "phone":
        d.rounded_rectangle((cx - r * 0.42, cy - r * 0.95, cx + r * 0.42, cy + r * 0.95), 12, outline=g, width=w)
        d.line((cx - r * 0.12, cy - r * 0.78, cx + r * 0.12, cy - r * 0.78), fill=g, width=w)
        khatam(d, cx, cy - r * 0.05, r * 0.2, outline=g, width=w - 1)
        d.ellipse((cx - 4, cy + r * 0.68, cx + 4, cy + r * 0.68 + 8), fill=g)

def panel(size, radius=30):
    p = lin_grad(size, (20, 44, 31), (9, 24, 16)).convert("RGBA")
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius, fill=255)
    p.putalpha(m)
    ImageDraw.Draw(p).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius, outline=GOLD + (110,), width=2)
    return p

def pattern_on(im, radius, step=110, alpha=16):
    W, H = im.size
    z = zellige((W, H), step=step, alpha=alpha)
    m = Image.new("L", (W, H), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, W - 1, H - 1), radius, fill=255)
    z.putalpha(ImageChops.multiply(z.getchannel("A"), m))
    im.alpha_composite(z)

def steps_image(steps, out, title, subtitle):
    """Cards from right to left, numbered, with an icon, a title and two short lines, joined by gold chevrons."""
    n = len(steps)
    cw, ch, gap = 330, 430, 54
    W = n * cw + (n - 1) * gap + 120
    H = ch + 250
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    im.alpha_composite(panel((W, H), 36))
    pattern_on(im, 36)
    metal(im, (W - 70, 52), title, font(52, 800), rtl=True, glow_amount=0.4)
    d = ImageDraw.Draw(im)
    text(d, (W - 70, 148), subtitle, 28, (214, 205, 184), 500, anchor="ra")
    for i, (kind, t, lines) in enumerate(steps):
        x2 = W - 60 - i * (cw + gap); x1 = x2 - cw; y1 = 220
        im.alpha_composite(panel((cw, ch), 26), (x1, y1))
        glow = Image.new("RGBA", im.size, (0, 0, 0, 0))
        ImageDraw.Draw(glow).ellipse((x1 + cw / 2 - 90, y1 + 40, x1 + cw / 2 + 90, y1 + 210), fill=GOLD + (55,))
        im.alpha_composite(glow.filter(ImageFilter.GaussianBlur(30)))
        d = ImageDraw.Draw(im)
        d.ellipse((x2 - 74, y1 - 26, x2 - 14, y1 + 34), fill=GOLD + (255,), outline=DARK + (255,), width=4)
        d.text((x2 - 44, y1 + 5), str(i + 1), font=font(32, 800), fill=DARK, anchor="mm")
        icon2(im, kind, x1 + cw / 2, y1 + 125, 62)
        d = ImageDraw.Draw(im)
        text(d, (x1 + cw / 2, y1 + 262), t, 34, SAND, 800, anchor="mm")
        for k, l in enumerate(lines):
            text(d, (x1 + cw / 2, y1 + 322 + k * 44), l, 25, (196, 186, 164), 500, anchor="mm")
        if i < n - 1:
            ax = x1 - gap / 2; ay = y1 + ch / 2
            d.line((ax + 12, ay - 16, ax - 6, ay, ax + 12, ay + 16), fill=GOLD + (230,), width=5, joint="curve")
    im.save(os.path.join(OUT, out), optimize=True)

CHOOSE = {
    "ar": dict(badge="موصى به", cards=[
        ("phone", "من هاتفك", ["بلا كمبيوتر", "نحو دقيقتين، بثلاث لغات", "تنظيف اختياري قابل للتراجع"]),
        ("laptop", "بالذكاء الاصطناعي", ["تلصق جملة واحدة في Claude أو Codex", "يتكفّل بالباقي من حاسوبك", "نحو 40 دقيقة دون أوامر"]),
        ("plug", "يدويًا بـ ADB", ["تثبيت التطبيق فقط", "خمس دقائق وبضعة أوامر", "لمن يحب التحكم الكامل"])]),
    "fr": dict(badge="Recommandé", cards=[
        ("phone", "Depuis votre téléphone", ["Sans ordinateur", "Environ 2 minutes, en 3 langues", "Nettoyage facultatif, annulable"]),
        ("laptop", "Avec une IA", ["Collez une phrase dans Claude ou Codex", "Il fait le reste depuis votre PC", "Environ 40 minutes sans commande"]),
        ("plug", "À la main (ADB)", ["Installe seulement l'appli", "Cinq minutes et quelques commandes", "Pour garder le contrôle total"])]),
    "en": dict(badge="Recommended", cards=[
        ("phone", "From your phone", ["No computer", "About 2 minutes, in 3 languages", "Optional cleanup, with undo"]),
        ("laptop", "With an AI", ["Paste one sentence into Claude or Codex", "It does the rest from your PC", "About 40 minutes, no commands"]),
        ("plug", "By hand (ADB)", ["Installs just the app", "Five minutes and a few commands", "For full control"])]),
}

def _lines(s, size, weight, maxw, rtl):
    """Words into lines that fit maxw; fails loudly instead of letting text run out of its card."""
    f = font(size, weight)
    kw = dict(direction="rtl", language="ar") if rtl else dict(language="fr")
    d = ImageDraw.Draw(Image.new("L", (4, 4)))
    out, cur = [], ""
    for w in s.split(" "):
        t = (cur + " " + w).strip()
        if cur and d.textlength(t, font=f, **kw) > maxw:
            out.append(cur); cur = w
        else:
            cur = t
    out.append(cur)
    for l in out:
        assert d.textlength(l, font=f, **kw) <= maxw + 1, "does not fit: " + l
    return out

def choose_images():
    """The three ways to install, one picture per language (guide-choose-ar/fr/en.png), 1800 x 580, transparent around the cards."""
    W, H, cw, gap = 1800, 580, 558, 63
    for lang, spec in CHOOSE.items():
        rtl = lang == "ar"
        kw = dict(direction="rtl", language="ar", features=["kern", "liga"]) if rtl else dict(language=lang)
        im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        for i, (kind, title, bullets) in enumerate(spec["cards"]):
            x1 = (W - cw - i * (cw + gap)) if rtl else i * (cw + gap)
            x2 = x1 + cw
            if i == 0:
                gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
                ImageDraw.Draw(gl).rounded_rectangle((x1, 52, x2, 565), 34, fill=GOLD + (120,))
                im.alpha_composite(gl.filter(ImageFilter.GaussianBlur(26)))
            im.alpha_composite(panel((cw, 513), 34), (x1, 52))
            d = ImageDraw.Draw(im)
            if i == 0:
                bw = int(d.textlength(spec["badge"], font=font(26, 800), **kw)) + 56
                bx = (x2 - 28 - bw) if rtl else x1 + 28
                d.rounded_rectangle((bx, 24, bx + bw, 76), 26, fill=GOLD + (255,))
                d.text((bx + bw / 2, 50), spec["badge"], font=font(26, 800), fill=DARK, anchor="mm", **kw)
            ix = x2 - 80 if rtl else x1 + 80
            icon2(im, kind, ix, 168, 58)
            tx = ix - 78 if rtl else ix + 78
            tlines = _lines(title, 38, 800, cw - 220, rtl)
            for k, l in enumerate(tlines):
                y = 168 + (k - (len(tlines) - 1) / 2) * 50
                d = ImageDraw.Draw(im)
                d.text((tx, y), l, font=font(38, 800), fill=SAND, anchor=("rm" if rtl else "lm"), **kw)
            bs = 30 if rtl else 27
            lh = round(bs * 1.25)
            y = 292
            for b in bullets:
                bl = _lines(b, bs, 500, cw - 110, rtl)
                bx = x2 - 50 if rtl else x1 + 50
                d = ImageDraw.Draw(im)
                khatam(d, bx, y + (len(bl) - 1) * lh / 2, 9, fill=GOLD + (255,))
                for k, l in enumerate(bl):
                    d.text((bx - 26 if rtl else bx + 26, y + k * lh), l, font=font(bs, 500), fill=SAND, anchor=("rm" if rtl else "lm"), **kw)
                y += lh * len(bl) + 46
            assert y - 46 - lh / 2 < 545, "the bullets run out of the card"
        im.save(os.path.join(OUT, "guide-choose-%s.png" % lang), optimize=True)

def channels_card():
    W, H = 1600, 560
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    im.alpha_composite(panel((W, H), 36))
    pattern_on(im, 36, step=120, alpha=18)
    gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(gl).ellipse((60, 60, 520, 500), fill=GOLD + (70,))
    im.alpha_composite(gl.filter(ImageFilter.GaussianBlur(60)))
    icon2(im, "broadcast", 290, 190, 110)
    metal(im, (290, 380), "311", font(130, 800), anchor="mm", glow_amount=0.6)
    d = ImageDraw.Draw(im)
    text(d, (290, 480), "قناة عربية", 36, SAND, 700, anchor="mm")
    metal(im, (W - 70, 56), "تطبيق القنوات", font(64, 800), rtl=True, glow_amount=0.4)
    d = ImageDraw.Draw(im)
    text(d, (W - 70, 170), "بثّ مباشر لقنوات مغربية وعربية، مرتبة في مجموعات", 32, (214, 205, 184), 500, anchor="ra")
    groups = ["المغرب", "أخبار", "منوعات", "عامة", "دينية", "موسيقى", "أفلام", "مسلسلات", "رياضة", "أطفال", "ثقافة", "وثائقية", "تعليم", "طبخ"]
    x, y = W - 70, 250
    for gname in groups:
        f = font(28, 600)
        w = int(d.textlength(gname, font=f, direction="rtl", language="ar")) + 54
        if x - w < 640:
            x, y = W - 70, y + 74
        d.rounded_rectangle((x - w, y, x, y + 54), 27, fill=(28, 56, 40, 255), outline=GOLD + (150,), width=2)
        text(d, (x - w / 2, y + 27), gname, 28, SAND, 600, anchor="mm")
        x -= w + 14
    im.save(os.path.join(OUT, "channels.png"), optimize=True)


if __name__ == "__main__":
    only = set(filter(None, os.environ.get("ONLY", "").split(",")))
    def want(g): return not only or g in only
    if want("hero"): hero()
    if want("misc"): divider(); stats(); button(); channels_card()
    if want("guide"):
        choose_images()
        steps_image([("tv", "فعّل وضع المطوّر", ["Build سبع مرات", "ثم USB debugging"]),
                     ("laptop", "افتح المساعد", ["Claude Code أو Codex", "على كمبيوتر في بيتك"]),
                     ("clip", "الصق الجملة", ["جملة واحدة تجدها أسفله", "وأجب عن ثلاثة أسئلة"]),
                     ("cup", "استرح", ["نحو 40 دقيقة", "والتلفزيون جاهز"])],
                    "guide-auto.png", "التثبيت التلقائي في أربع خطوات", "مساعد ذكاء اصطناعي يشغّل الأوامر على كمبيوترك، وأنت تجيب فقط")
        steps_image([("plug", "اتصل بالتلفزيون", ["adb connect", "ثم Allow على الشاشة"]),
                     ("down", "نزّل التطبيق", ["chouitv-home.apk"]),
                     ("trash", "انقل النسخة القديمة", ["إن كانت مثبتة فقط", "والسكربت ينقل إعداداتك"]),
                     ("tv", "ثبّت واضبط", ["adb install", "واختر شاشة التوقف"]),
                     ("check", "استمتع", ["والتحديثات القادمة", "تصلك تلقائيًا"])],
                    "guide-manual.png", "التثبيت اليدوي في خمس خطوات", "لمن يحب أن يرى كل أمر بنفسه")
    if want("shots"):
        annotated("tb_8", [(1, (440, 150)), (2, (1255, 80)), (3, (1886, 268)), (4, (1012, 460)), (5, (1012, 628)), (6, (1886, 800))], "screensaver-annotated.png")
        for name, out in [("home", "home.png"), ("l_ma", "listen-morocco.png"), ("l_quran", "listen-quran.png"), ("l_adhan", "listen-adhan.png"),
                          ("l_reciters", "listen-reciters.png"), ("player", "player-oscilloscope.png"), ("sleep", "sleep-timer.png"),
                          ("set1", "settings-1-basics.png"), ("set2", "settings-2-screen.png"), ("set3", "settings-3-content.png"),
                          ("set4", "settings-4-prayer.png"), ("set5", "settings-5-home.png")]:
            save_bezel(name, out)
        corner = [load(n).crop((20, 20, 680, 250)) for n in ("ss_6", "ss_8", "ss_2", "ss_5")]
        gif([c.convert("P", palette=Image.ADAPTIVE) for c in corner], "corner.gif", 2200)
        band = [load("tb_%d" % i).crop((60, 730, 1860, 860)).resize((1200, 87), Image.LANCZOS) for i in range(1, 15)]
        gif([b.convert("P", palette=Image.ADAPTIVE) for b in band], "tasbih.gif", 700)
    print("done")
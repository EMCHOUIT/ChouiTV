"""The pictures of the release notes: a banner and four cards, drawn in the house style (dark green, gold, zellige).
Version 4.0 (one app). The cards are illustrations, not screenshots: they show numbers and ideas that no screenshot can show.
Only the banner carries a real picture: a capture of the screensaver of a TV running 4.0 (RAW/dream40.png, taken with `adb shell screencap -p`).
Run: python whatsnew.py RAW OUT      (RAW holds dream40.png; OUT is docs/media). Needs Pillow built with raqm (Arabic shaping)."""
import os, sys, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.argv = [sys.argv[0]] + sys.argv[1:3]
import make_media as m
from make_media import *

DIM = (176, 168, 148)
RED = (214, 112, 98)
INK = (16, 38, 27)

def shot(name):
    return Image.open(os.path.join(m.RAW, name + ".png")).convert("RGB")

# ---------------------------------------------------------------- small helpers
def tw(d, s, f):
    return d.textlength(s, font=f, direction="rtl", language="ar")

def wrap(s, f, maxw):
    """Words into lines no wider than maxw (the picture is Arabic, right to left)."""
    d = ImageDraw.Draw(Image.new("L", (4, 4)))
    lines, cur = [], ""
    for w in s.split(" "):
        t = (cur + " " + w).strip()
        if cur and tw(d, t, f) > maxw:
            lines.append(cur); cur = w
        else:
            cur = t
    lines.append(cur)
    for l in lines:
        assert tw(d, l, f) <= maxw + 1, "does not fit: " + l
    return lines

def put(d, xy, s, size, fill=SAND, weight=600, anchor="rm", maxw=None, lh=1.35):
    """Text with an automatic wrap when maxw is given; returns the number of lines."""
    f = font(size, weight)
    lines = wrap(s, f, maxw) if maxw else [s]
    x, y = xy
    for i, l in enumerate(lines):
        d.text((x, y + i * size * lh), l, font=f, fill=fill, anchor=anchor, direction="rtl", language="ar", features=["kern", "liga"])
    return len(lines)

def latin(d, xy, s, size, fill=SAND, weight=600, anchor="mm"):
    d.text(xy, s, font=font(size, weight), fill=fill, anchor=anchor)

def glow(im, box, color=GOLD, alpha=60, blur=40, shape="ellipse"):
    g = Image.new("RGBA", im.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(g)
    if shape == "ellipse": gd.ellipse(box, fill=color + (alpha,))
    else: gd.rounded_rectangle(box, 30, fill=color + (alpha,))
    im.alpha_composite(g.filter(ImageFilter.GaussianBlur(blur)))

def arrow(im, x1, x2, y, color=GOLD, width=6, head=22):
    """A gold arrow from x1 to x2 (it points to x2, usually to the left)."""
    d = ImageDraw.Draw(im)
    s = 1 if x2 > x1 else -1
    d.line((x1, y, x2 - s * head * 0.4, y), fill=color + (255,), width=width)
    d.line((x2 - s * head, y - head * 0.75, x2, y, x2 - s * head, y + head * 0.75), fill=color + (255,), width=width, joint="curve")

def tile(im, cx, cy, w, h, draw_icon, dim=False, accent=False, radius=30):
    """An app tile: a dark rounded square with a thin gold edge; the icon is drawn by draw_icon(im, cx, cy, size)."""
    box = (cx - w // 2, cy - h // 2, cx + w // 2, cy + h // 2)
    if accent:
        glow(im, (box[0] - 30, box[1] - 30, box[2] + 30, box[3] + 30), GOLD, 70, 36, "rounded")
    p = lin_grad((w, h), (30, 62, 44) if not dim else (22, 40, 30), (12, 30, 21)).convert("RGBA")
    mk = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mk).rounded_rectangle((0, 0, w - 1, h - 1), radius, fill=255)
    p.putalpha(mk)
    im.alpha_composite(p, (box[0], box[1]))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle(box, radius, outline=GOLD + ((255 if accent else 120 if dim else 190),), width=3 if accent else 2)
    draw_icon(im, cx, cy, min(w, h))

def ic_tv(im, cx, cy, s):
    icon2(im, "tv", cx, cy, int(s * 0.30))
def ic_moon(im, cx, cy, s):
    r = int(s * 0.30); g = GOLD + (255,)
    mk = Image.new("L", im.size, 0); md = ImageDraw.Draw(mk)
    md.ellipse((cx - r, cy - r, cx + r, cy + r), fill=255)
    md.ellipse((cx - r * 0.35, cy - r * 1.15, cx + r * 1.35, cy + r * 0.75), fill=0)
    lay = Image.new("RGBA", im.size, g); lay.putalpha(mk); im.alpha_composite(lay)
def ic_star(im, cx, cy, s):
    d = ImageDraw.Draw(im)
    khatam(d, cx, cy, s * 0.30, outline=GOLD + (255,), width=4)
    khatam(d, cx, cy, s * 0.13, fill=GOLD + (255,))
def ic_gear(im, cx, cy, r, fill=GOLD + (255,)):
    d = ImageDraw.Draw(im)
    for k in range(8):
        a = k * math.pi / 4
        d.polygon([(cx + (r * 0.62) * math.cos(a - 0.2) , cy + (r * 0.62) * math.sin(a - 0.2)), (cx + r * math.cos(a - 0.13), cy + r * math.sin(a - 0.13)),
                   (cx + r * math.cos(a + 0.13), cy + r * math.sin(a + 0.13)), (cx + (r * 0.62) * math.cos(a + 0.2), cy + (r * 0.62) * math.sin(a + 0.2))], fill=fill)
    d.ellipse((cx - r * 0.7, cy - r * 0.7, cx + r * 0.7, cy + r * 0.7), fill=fill)
    d.ellipse((cx - r * 0.3, cy - r * 0.3, cx + r * 0.3, cy + r * 0.3), fill=(22, 44, 31, 255))
def ic_tasks(im, cx, cy, r):
    d = ImageDraw.Draw(im); g = GOLD + (255,)
    for k in range(3):
        y = cy - r * 0.55 + k * r * 0.55
        d.line((cx - r * 0.9, y, cx - r * 0.75, y + r * 0.14, cx - r * 0.5, y - r * 0.16), fill=g, width=4, joint="curve")
        d.line((cx - r * 0.2, y, cx + r * 0.9, y), fill=g, width=4)
def ic_photo(im, cx, cy, r):
    d = ImageDraw.Draw(im); g = GOLD + (255,)
    d.rounded_rectangle((cx - r * 0.95, cy - r * 0.72, cx + r * 0.95, cy + r * 0.72), 8, outline=g, width=4)
    d.polygon([(cx - r * 0.8, cy + r * 0.58), (cx - r * 0.2, cy - r * 0.05), (cx + r * 0.2, cy + r * 0.35), (cx + r * 0.45, cy + r * 0.12), (cx + r * 0.8, cy + r * 0.58)], fill=g)
    d.ellipse((cx + r * 0.3, cy - r * 0.5, cx + r * 0.55, cy - r * 0.25), fill=g)
def ic_key(im, cx, cy, r):
    d = ImageDraw.Draw(im); g = GOLD + (255,)
    d.ellipse((cx + r * 0.25, cy - r * 0.45, cx + r * 0.95, cy + r * 0.25), outline=g, width=5)
    d.line((cx + r * 0.3, cy + r * 0.1, cx - r * 0.9, cy + r * 0.78), fill=g, width=5)
    d.line((cx - r * 0.55, cy + r * 0.58, cx - r * 0.55, cy + r * 0.2), fill=g, width=5)
    d.line((cx - r * 0.85, cy + r * 0.74, cx - r * 0.85, cy + r * 0.38), fill=g, width=5)
def ic_file(im, cx, cy, r, color=GOLD + (255,), mark="check"):
    d = ImageDraw.Draw(im)
    d.polygon([(cx - r * 0.62, cy - r * 0.8), (cx + r * 0.25, cy - r * 0.8), (cx + r * 0.62, cy - r * 0.43), (cx + r * 0.62, cy + r * 0.8), (cx - r * 0.62, cy + r * 0.8)], outline=color, width=4)
    d.line((cx + r * 0.25, cy - r * 0.8, cx + r * 0.25, cy - r * 0.43, cx + r * 0.62, cy - r * 0.43), fill=color, width=4)
    for k in range(2):
        d.line((cx - r * 0.38, cy - r * 0.18 + k * r * 0.26, cx + r * 0.38, cy - r * 0.18 + k * r * 0.26), fill=color, width=4)
    if mark == "check":
        d.ellipse((cx + r * 0.05, cy + r * 0.28, cx + r * 0.85, cy + r * 1.08), fill=(20, 44, 31, 255), outline=color, width=4)
        d.line((cx + r * 0.22, cy + r * 0.68, cx + r * 0.4, cy + r * 0.84, cx + r * 0.72, cy + r * 0.5), fill=color, width=5, joint="curve")
def cross(im, cx, cy, r, color=RED, width=9):
    d = ImageDraw.Draw(im)
    d.line((cx - r, cy - r, cx + r, cy + r), fill=color + (255,), width=width)
    d.line((cx - r, cy + r, cx + r, cy - r), fill=color + (255,), width=width)
def check(im, cx, cy, r, color=GOLD, width=9):
    d = ImageDraw.Draw(im)
    d.line((cx - r, cy, cx - r * 0.3, cy + r * 0.7, cx + r, cy - r * 0.6), fill=color + (255,), width=width, joint="curve")

def icon40(canvas, kind, cx, cy, r=34):
    """Header and chip icons in a gold disc: the ones of make_media plus 'one', 'move', 'leaf' and 'lock'."""
    if kind in ("beads", "moon", "waves", "down", "star"):
        return icon(canvas, kind, cx, cy, r)
    d = ImageDraw.Draw(canvas)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(28, 52, 38, 255), outline=GOLD + (255,), width=3)
    g = GOLD + (255,)
    w = max(3, r // 9)
    if kind == "one":
        d.rounded_rectangle((cx - r * 0.42, cy - r * 0.42, cx + r * 0.42, cy + r * 0.42), 6, outline=g, width=w)
        khatam(d, cx, cy, r * 0.22, fill=g)
    elif kind == "move":
        ic_gear(canvas, cx, cy, r * 0.6)
    elif kind == "leaf":
        d.polygon([(cx - r * 0.5, cy + r * 0.45), (cx - r * 0.35, cy - r * 0.15), (cx + r * 0.1, cy - r * 0.5), (cx + r * 0.5, cy - r * 0.5), (cx + r * 0.45, cy - r * 0.05), (cx + r * 0.05, cy + r * 0.4)], fill=g)
        d.line((cx - r * 0.55, cy + r * 0.6, cx + r * 0.1, cy - r * 0.05), fill=(28, 52, 38, 255), width=w)
    elif kind == "lock":
        d.rounded_rectangle((cx - r * 0.42, cy - r * 0.05, cx + r * 0.42, cy + r * 0.52), 5, fill=g)
        d.arc((cx - r * 0.28, cy - r * 0.55, cx + r * 0.28, cy + r * 0.12), 180, 360, fill=g, width=w + 1)
        d.line((cx - r * 0.28, cy - r * 0.22, cx - r * 0.28, cy), fill=g, width=w + 1)
        d.line((cx + r * 0.28, cy - r * 0.22, cx + r * 0.28, cy), fill=g, width=w + 1)

# ---------------------------------------------------------------- the banner
def banner(pill_txt, title_lines, chips, out, shotname="dream40"):
    W, H = 1800, 640
    bg = Image.new("RGBA", (W, H), (6, 20, 13, 255))
    gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(gl)
    gd.ellipse((-300, -380, 900, 420), fill=GOLD + (80,))
    gd.ellipse((1000, 300, 2100, 1050), fill=GREEN + (120,))
    bg.alpha_composite(gl.filter(ImageFilter.GaussianBlur(140)))
    z = zellige((W, H))
    fade = Image.new("L", (W, H), 0)
    ImageDraw.Draw(fade).ellipse((500, -400, 2400, 1100), fill=255)
    z.putalpha(ImageChops.multiply(z.getchannel("A"), fade.filter(ImageFilter.GaussianBlur(160))))
    bg.alpha_composite(z)
    tvb = bezel(shot(shotname), 540, pad=14)
    bg.alpha_composite(tvb, (30, 70))
    R = W - 90
    d = ImageDraw.Draw(bg)
    pw = int(tw(d, pill_txt, font(30, 600))) + 120
    d.rounded_rectangle((R - pw, 60, R, 116), 28, fill=(28, 52, 38, 230), outline=GOLD + (220,), width=2)
    text(d, (R - 45, 88), pill_txt, 30, GOLD, 600, anchor="rm")
    icon(bg, "star", R - pw + 34, 88, 15)
    metal(bg, (R + 6, 130), "ChouiTV", script_font(190), glow_amount=0.95)
    metal(bg, (R, 330), title_lines, ruqaa(70), top=(255, 250, 236), bottom=(222, 196, 140), glow=(255, 236, 190), rtl=True, glow_amount=0.45)
    x = R
    d = ImageDraw.Draw(bg)
    for kind, label in chips:
        f = font(30, 600)
        w = int(tw(d, label, f)) + 130
        d.rounded_rectangle((x - w, 524, x, 586), 31, fill=(28, 56, 40, 255), outline=GOLD + (170,), width=2)
        icon40(bg, kind, x - 34, 555, 18)
        d = ImageDraw.Draw(bg)
        text(d, (x - 68, 555), label, 30, SAND, 600, anchor="rm")
        x -= w + 16
    assert x > 40, "the chips do not fit"
    sparkles(bg, 90, (0, 0, W, H), seed=5)
    edge = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(edge).rectangle((0, 0, W, H), outline=(0, 0, 0, 160), width=60)
    bg.alpha_composite(edge.filter(ImageFilter.GaussianBlur(50)))
    bg.convert("RGB").save(os.path.join(m.OUT, out), optimize=True)

# ---------------------------------------------------------------- the cards: 1700 x 620, the title on the right, one picture below it
CW, CH = 1700, 620
R = CW - 70

def card_base(kind, title, sub):
    im = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
    im.alpha_composite(panel((CW, CH), 36))
    pattern_on(im, 36, step=120, alpha=18)
    icon40(im, kind, R - 36, 84, 36)
    metal(im, (R - 92, 40), title, font(70, 800), rtl=True, glow_amount=0.4)
    d = ImageDraw.Draw(im)
    text(d, (R, 150), sub, 36, (214, 205, 184), 500, anchor="ra")
    return im

def number(im, cx, cy, s, size, unit, color="gold", unit_size=36):
    """A big number with its unit on its left (Arabic reads the number first, then the unit)."""
    d = ImageDraw.Draw(im)
    f = font(size, 800)
    nw = d.textlength(s, font=f)
    uw = tw(d, unit, font(unit_size, 600))
    total = nw + 18 + uw
    xr = cx + total / 2            # right edge of the group
    if color == "gold":
        metal(im, (xr, cy), s, f, anchor="rm", glow_amount=0.55)
    else:
        d.text((xr, cy), s, font=f, fill=DIM, anchor="rm")
    d = ImageDraw.Draw(im)
    text(d, (xr - nw - 18, cy + size * 0.16), unit, unit_size, SAND if color == "gold" else DIM, 600, anchor="rm")
    return xr - total

def card_one(out):
    im = card_base("one", "تطبيق واحد بدل اثنين", "الرئيسية وشاشة التوقف والإعدادات والاستماع في حزمة واحدة")
    # before: two apps (right), after: one (left)
    cyt = 310
    tile(im, 1480, cyt, 170, 170, ic_tv, dim=True)
    tile(im, 1230, cyt, 170, 170, ic_moon, dim=True)
    d = ImageDraw.Draw(im)
    text(d, (1355, cyt), "+", 60, DIM, 500, anchor="mm")
    text(d, (1480, cyt + 118), "الرئيسية", 32, DIM, 600, anchor="mm")
    text(d, (1230, cyt + 118), "شاشة التوقف", 32, DIM, 600, anchor="mm")
    xl = number(im, 1355, 525, "6.7", 96, "ميغابايت", color="dim")
    d = ImageDraw.Draw(im)
    nw = d.textlength("6.7", font=font(96, 800))
    xr = xl + (nw + 18 + tw(d, "ميغابايت", font(36, 600)))
    d.line((xr - nw - 12, 528, xr + 12, 520), fill=RED + (220,), width=6)   # struck out: the old total
    arrow(im, 1060, 780, cyt, width=7, head=26)
    latin(d, (920, cyt - 40), "4.0", 40, GOLD, 800)
    tile(im, 440, cyt - 10, 230, 230, ic_star, accent=True, radius=40)
    d = ImageDraw.Draw(im)
    latin(d, (440, cyt + 135), "ChouiTV", 32, SAND, 700)
    number(im, 440, 525, "3.5", 104, "ميغابايت")
    im.save(os.path.join(m.OUT, out), optimize=True)

def card_move(out):
    im = card_base("move", "إعداداتك تنتقل معك", "عند أول تشغيل، دون أن تفعل شيئًا")
    cy = 300
    tile(im, 1545, cy, 150, 150, ic_moon, dim=True)
    d = ImageDraw.Draw(im)
    text(d, (1545, cy + 108), "الشاشة القديمة", 30, DIM, 600, anchor="mm")
    tile(im, 170, cy, 190, 190, ic_star, accent=True, radius=34)
    d = ImageDraw.Draw(im)
    text(d, (170, cy + 128), "ChouiTV 4.0", 30, GOLD, 700, anchor="mm")
    # the three things that come over
    items = [(ic_gear, "الإعدادات"), (ic_tasks, "المهام"), (ic_photo, "الصور")]
    for i, (fn, lab) in enumerate(items):
        cx = [1260, 905, 550][i]
        d = ImageDraw.Draw(im)
        d.rounded_rectangle((cx - 145, cy - 78, cx + 145, cy + 78), 30, fill=(28, 56, 40, 255), outline=GOLD + (160,), width=2)
        fn(im, cx + 92, cy, 38)
        d = ImageDraw.Draw(im)
        text(d, (cx + 40, cy), lab, 36, SAND, 700, anchor="rm")
    for xa, xb in ((1452, 1412), (1108, 1058), (752, 702), (396, 280)):
        arrow(im, xa, xb, cy, width=5, head=16)
    # the last two steps, which only a person can do
    for i, (n, t) in enumerate([("1", "اختر «شاشة تلفزيونك» التي بلا علامة"), ("2", "ثم أزل التطبيق القديم")]):
        x2 = R - i * 800
        x1 = x2 - 740
        d = ImageDraw.Draw(im)
        d.rounded_rectangle((x1, 470, x2, 565), 30, fill=(26, 52, 38, 255), outline=GOLD + (140,), width=2)
        d.ellipse((x2 - 74, 492, x2 - 28, 538), fill=GOLD + (255,))
        d.text((x2 - 51, 516), n, font=font(30, 800), fill=DARK, anchor="mm")
        text(d, (x2 - 98, 518), t, 33, SAND, 600, anchor="rm")
    im.save(os.path.join(m.OUT, out), optimize=True)

def chart(im, x1, y1, x2, y2, kind):
    """One small memory chart (an illustration of the measured range, not a recording): grid lines at 55 and 155 MB."""
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((x1, y1, x2, y2), 26, fill=(14, 32, 22, 255), outline=GOLD + (110,), width=2)
    gx1, gx2 = x1 + 100, x2 - 40
    def yv(v): return y2 - 70 - (v - 0) / 170.0 * (y2 - y1 - 130)
    for v in (55, 155):
        y = yv(v)
        for xx in range(int(gx1), int(gx2), 16):
            d.line((xx, y, xx + 8, y), fill=GOLD + (70,), width=2)
        latin(d, (gx1 - 14, y), str(v), 28, DIM, 500, "rm")
    pts = []
    n = 120
    for i in range(n + 1):
        u = i / n
        x = gx1 + (gx2 - gx1) * u
        if kind == "steady":
            v = 55
        else:   # swings between 55 and 155: three rises and falls, kept in range
            v = 55 + 100 * (0.5 + 0.5 * math.sin(u * math.pi * 5.2 - 1.2)) ** 1.2
        pts.append((x, yv(v)))
    if kind == "steady":
        glw = Image.new("RGBA", im.size, (0, 0, 0, 0))
        ImageDraw.Draw(glw).line(pts, fill=GOLD + (200,), width=14)
        im.alpha_composite(glw.filter(ImageFilter.GaussianBlur(10)))
        d = ImageDraw.Draw(im)
    d.line(pts, fill=(GOLD if kind == "steady" else DIM) + (255,), width=7 if kind == "steady" else 5, joint="curve")

def card_light(out):
    im = card_base("leaf", "أخفّ على الذاكرة", "الذاكرة الرسومية أثناء عمل شاشة التوقف")
    top, bot = 205, 500
    chart(im, 840, top, R, bot, "swing")
    chart(im, 70, top, 790, bot, "steady")
    d = ImageDraw.Draw(im)
    text(d, (R - 30, top + 40), "قبل", 34, DIM, 700, anchor="rm")
    f = font(46, 800)
    d.text((R - 130, top + 40), "55 – 155", font=f, fill=DIM, anchor="rm")
    text(d, (R - 130 - d.textlength("55 – 155", font=f) - 16, top + 44), "ميغابايت", 28, DIM, 500, anchor="rm")
    text(d, (790 - 30, top + 40), "الآن", 34, GOLD, 700, anchor="rm")
    metal(im, (790 - 130, top + 40), "55", font(58, 800), anchor="rm", glow_amount=0.5)
    d = ImageDraw.Draw(im)
    text(d, (790 - 130 - d.textlength("55", font=font(58, 800)) - 16, top + 46), "ميغابايت ثابتة", 28, SAND, 600, anchor="rm")
    text(d, (R, 556), "ونحو 50 ميغابايت أقل في المجموع، وبطاقة الرئيسية تُحرَّر حين يكون تطبيق آخر أمامك", 31, SAND, 600, anchor="rm")
    text(d, (790 - 20, bot - 24), "رسم توضيحي", 22, (128, 122, 108), 500, anchor="rm")
    text(d, (R - 20, bot - 24), "رسم توضيحي", 22, (128, 122, 108), 500, anchor="rm")
    im.save(os.path.join(m.OUT, out), optimize=True)

def card_signed(out):
    im = card_base("lock", "تحديثات موقَّعة", "التلفزيون يصدّق ما وقّعه المالك فقط")
    ya, yb = 292, 500
    # the real path: key -> signed file -> TV
    tile(im, 1500, ya, 150, 150, lambda im_, cx, cy, s: ic_key(im_, cx, cy, 50), accent=True, radius=30)
    tile(im, 880, ya, 150, 150, lambda im_, cx, cy, s: ic_file(im_, cx, cy, 52), accent=True, radius=30)
    tile(im, 260, ya, 150, 150, ic_tv, accent=True, radius=30)
    d = ImageDraw.Draw(im)
    text(d, (1500, ya + 108), "المالك يوقّع", 32, GOLD, 700, anchor="mm")
    latin(d, (880, ya + 106), "latest.json + .sig", 28, SAND, 600)
    text(d, (260, ya + 108), "التلفزيون يتحقّق", 32, GOLD, 700, anchor="mm")
    arrow(im, 1390, 1010, ya)
    arrow(im, 760, 380, ya)
    check(im, 260 + 56, ya - 56, 15, GOLD, 8)
    # the forged path: a file nobody signed -> nothing happens
    tile(im, 880, yb, 104, 104, lambda im_, cx, cy, s: ic_file(im_, cx, cy, 36, color=DIM + (255,), mark=None), dim=True, radius=24)
    cross(im, 880, yb, 22)
    d = ImageDraw.Draw(im)
    put(d, (R - 30, yb), "ملف بدّله مخترق أو بلا توقيع سليم", 31, DIM, 600, "rm")
    arrow(im, 800, 380, yb, color=DIM, width=5, head=18)
    text(d, (260, yb), "لا تحديث", 36, SAND, 700, anchor="mm")
    im.save(os.path.join(m.OUT, out), optimize=True)

def v400():
    banner("جديد · الإصدار 4.0", "تطبيق واحد أخفّ، وإعداداتك معك",
           [("one", "تطبيق واحد"), ("leaf", "أخفّ"), ("move", "إعداداتك معك"), ("lock", "تحديثات موقَّعة")], "whatsnew-banner.png")
    card_one("whatsnew-one-app.png")
    card_move("whatsnew-settings-move.png")
    card_light("whatsnew-lighter.png")
    card_signed("whatsnew-signed.png")

if __name__ == "__main__":
    v400()
    print("done")

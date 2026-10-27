"""The pictures of the release notes: a banner and one card per novelty (real TV1 screenshots in RAW). Run: python whatsnew.py RAW OUT"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.argv = [sys.argv[0]] + sys.argv[1:3]
import make_media as m
from make_media import *

def shot(name):
    return Image.open(os.path.join(m.RAW, name + ".png")).convert("RGB")

def banner(version, pill_txt, title_lines, chips, out):
    W, H = 1800, 640
    bg = Image.new("RGBA", (W, H), (6, 20, 13, 255))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((-300, -380, 900, 420), fill=GOLD + (80,))
    gd.ellipse((1000, 300, 2100, 1050), fill=GREEN + (120,))
    bg.alpha_composite(glow.filter(ImageFilter.GaussianBlur(140)))
    z = zellige((W, H))
    fade = Image.new("L", (W, H), 0)
    ImageDraw.Draw(fade).ellipse((500, -400, 2400, 1100), fill=255)
    z.putalpha(ImageChops.multiply(z.getchannel("A"), fade.filter(ImageFilter.GaussianBlur(160))))
    bg.alpha_composite(z)
    tvb = bezel(shot('a2'), 540, pad=14)
    bg.alpha_composite(tvb, (30, 70))
    R = W - 90
    d = ImageDraw.Draw(bg)
    pw = int(d.textlength(pill_txt, font=font(30, 600), direction="rtl", language="ar")) + 120
    d.rounded_rectangle((R - pw, 60, R, 116), 28, fill=(28, 52, 38, 230), outline=GOLD + (220,), width=2)
    text(d, (R - 45, 88), pill_txt, 30, GOLD, 600, anchor="rm")
    icon(bg, "star", R - pw + 34, 88, 15)
    metal(bg, (R + 6, 130), "ChouiTV", script_font(190), glow_amount=0.95)
    metal(bg, (R, 330), title_lines, ruqaa(70), top=(255, 250, 236), bottom=(222, 196, 140), glow=(255, 236, 190), rtl=True, glow_amount=0.45)
    x = R
    d = ImageDraw.Draw(bg)
    for kind, label in chips:
        f = font(30, 600)
        w = int(d.textlength(label, font=f, direction="rtl", language="ar")) + 130
        d.rounded_rectangle((x - w, 524, x, 586), 31, fill=(28, 56, 40, 255), outline=GOLD + (170,), width=2)
        icon(bg, kind, x - 34, 555, 18)
        d = ImageDraw.Draw(bg)
        text(d, (x - 68, 555), label, 30, SAND, 600, anchor="rm")
        x -= w + 16
    sparkles(bg, 90, (0, 0, W, H), seed=5)
    edge = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(edge).rectangle((0, 0, W, H), outline=(0, 0, 0, 160), width=60)
    bg.alpha_composite(edge.filter(ImageFilter.GaussianBlur(50)))
    bg.convert("RGB").save(os.path.join(m.OUT, out), optimize=True)

def card(kind, title, sub, bullets, shotname, out):
    W, H = 1700, 620
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    im.alpha_composite(panel((W, H), 36))
    pattern_on(im, 36, step=120, alpha=18)
    tv = bezel(shot(shotname), 760, pad=18)
    gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(gl).ellipse((20, 60, 800, 560), fill=GOLD + (55,))
    im.alpha_composite(gl.filter(ImageFilter.GaussianBlur(70)))
    im.alpha_composite(tv, (20, (H - tv.height) // 2 + 10))
    R = W - 70
    icon(im, kind, R - 30, 92, 30)
    metal(im, (R - 76, 52), title, font(58, 800), rtl=True, glow_amount=0.4)
    d = ImageDraw.Draw(im)
    text(d, (R, 150), sub, 30, (214, 205, 184), 500, anchor="ra")
    y = 250
    for b in bullets:
        d.ellipse((R - 18, y + 8, R, y + 26), fill=GOLD + (255,))
        text(d, (R - 40, y), b, 34, SAND, 600, anchor="ra")
        y += 76
    im.save(os.path.join(m.OUT, out), optimize=True)

if __name__ == "__main__":
    banner("3.173", "جديد · الإصدار 3.173", "أذان على مقاسك، وإعداداتك في جيبك",
           [("down", "نسخ الإعدادات"), ("moon", "أذان لكل صلاة"), ("waves", "القرّاء في شبكة"), ("star", "قنوات 311")], "whatsnew-banner.png")
    card("down", "انسخ إعداداتك واستعدها", "تصدير واستيراد بضغطة واحدة، تنقلها بين تلفزيوناتك",
         ["ملف واحد chouitv-settings.json في التنزيلات", "معاينة لما سيتغيّر قبل أن يُطبَّق", "زر «التراجع» يعيد ما كان قبل الاستيراد"], "set_system", "whatsnew-backup.png")
    card("moon", "الأذان كما تريده", "خيارات لكل صلاة وتحكّم كامل في الصوت",
         ["مفتاح التفعيل في أول القسم", "تشغيل أو إيقاف كل صلاة على حدة", "أي زر في الريموت يوقف الأذان فورًا", "يعمل حتى أثناء شاشة التوقف"], "a2", "whatsnew-adhan.png")
    card("waves", "القرّاء في شبكة", "أربعة قرّاء في كل صف، والتنقل بين الأقسام يمينًا ويسارًا",
         ["بطاقات واضحة بعدد السور", "الأسهم تغيّر القسم دون الدخول في الخيارات", "إذاعات مغربية، القرآن مباشر، والأذان"], "t0", "whatsnew-reciters.png")
    card("star", "توقيع بتاريخ البناء", "تعرف أي نسخة عندك بنظرة واحدة",
         ["ChouiTV 3.173 · 2026-10-27 · EMChouit", "تنقّل أسهل صعودًا ونزولًا في الإعدادات", "إشارة تظهر فقط حين يتوفر إصدار أحدث"], "set_system_end", "whatsnew-signature.png")
    print("done")

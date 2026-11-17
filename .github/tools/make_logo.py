"""Draws the ChouiTV logo (v16, piste A: zellige star + play button) and the TV banner (variant 1), then renders the PNGs.

Sources written: .github/brand/mark.svg, .github/brand/banner.svg. PNGs written: the launcher icons, the TV banners and the
.github/brand images. Needs Chrome (or Edge) for the rendering; from WSL set CHROME to its Windows path, e.g.

    python3 .github/tools/make_logo.py
"""
import math, os, shutil, subprocess, sys, tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BRAND = os.path.join(ROOT, ".github/brand")
RES = os.path.join(ROOT, "android/launcher/src/main/res")
FONTS = os.path.join(ROOT, "android/launcher/src/main/assets/fonts")
CHROME = os.environ.get("CHROME", "/mnt/c/Program Files/Google/Chrome/Application/chrome.exe")

def star(cx, cy, R):
    """Eight-pointed star (khatam): 16 vertices, inner radius R * 0.7654."""
    r, pts = R * 0.7654, []
    for k in range(16):
        a = (k % 2 and 22.5 + (k - 1) * 22.5 or k * 22.5) - 90
        rad = r if k % 2 else R
        pts.append("%.1f,%.1f" % (cx + rad * math.cos(math.radians(a)), cy + rad * math.sin(math.radians(a))))
    return "M" + "L".join(pts) + "Z"

DEFS = ('<linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#12331f"/><stop offset="1" stop-color="#05140d"/></linearGradient>'
        '<linearGradient id="gold" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#f3d98f"/><stop offset="1" stop-color="#c49a52"/></linearGradient>')
ART = ('<path id="khatam" d="%s" fill="#fff" fill-opacity=".06" stroke="url(#gold)" stroke-width="18" stroke-linejoin="round"/>'
       '<polygon id="play" points="224,190 224,322 336,256" fill="url(#gold)" stroke="url(#gold)" stroke-width="18" stroke-linejoin="round"/>') % star(256, 256, 178)

MARK = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">\n'
        '  <!-- ChouiTV mark: a zellige eight-pointed star with a play button. Gold on deep green. Made by .github/tools/make_logo.py -->\n'
        '  <defs>%s</defs>\n  <rect width="512" height="512" rx="112" fill="url(#bg)"/>\n  %s\n</svg>\n') % (DEFS, ART)

BANNER = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 480 270">\n'
          '  <!-- ChouiTV TV banner, 16:9: the mark and the name (Latin, then Arabic). Fonts: Thmanyah. Made by .github/tools/make_logo.py -->\n'
          '  <defs>%s</defs>\n'
          '  <rect width="480" height="270" fill="url(#bg)"/>\n'
          '  <path d="M0 232Q90 196 180 222T360 214T480 226V270H0Z" fill="#fff" opacity=".04"/>\n'
          '  <path d="M0 250Q120 224 240 244T480 240V270H0Z" fill="#000" opacity=".25"/>\n'
          '  <g transform="translate(34 40) scale(.44)">%s</g>\n'
          '  <text x="358" y="136" text-anchor="middle" font-size="50" font-weight="700" fill="#f6ecd2" textLength="196" lengthAdjust="spacingAndGlyphs" font-family="Thmanyah, Segoe UI, sans-serif">ChouiTV</text>\n'
          '  <text x="358" y="196" text-anchor="middle" direction="rtl" font-size="46" font-weight="500" fill="#e3c27a" font-family="Thmanyah, Segoe UI, sans-serif">شويتيفي</text>\n'
          '</svg>\n') % (DEFS, ART)

HERO = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1600 500">\n'
        '  <!-- ChouiTV GitHub banner (repository image), 1600x500. Made by .github/tools/make_logo.py -->\n'
        '  <defs>%s<radialGradient id="glow" cx="0.82" cy="0.45" r="0.6"><stop offset="0" stop-color="#c49a52" stop-opacity=".28"/><stop offset="1" stop-color="#c49a52" stop-opacity="0"/></radialGradient></defs>\n'
        '  <rect width="1600" height="500" fill="url(#bg)"/>\n'
        '  <rect width="1600" height="500" fill="url(#glow)"/>\n'
        '  <path d="M0 400Q260 340 520 380T1040 366T1600 392V500H0Z" fill="#fff" opacity=".04"/>\n'
        '  <path d="M0 440Q320 396 640 430T1280 424T1600 440V500H0Z" fill="#000" opacity=".25"/>\n'
        '  <g transform="translate(1180 110) scale(.56)"><rect width="512" height="512" rx="112" fill="url(#bg)" stroke="#c49a52" stroke-opacity=".35" stroke-width="4"/>%s</g>\n'
        '  <text x="1080" y="236" text-anchor="end" font-size="150" font-weight="700" fill="#f6ecd2" font-family="Thmanyah, Segoe UI, sans-serif">ChouiTV</text>\n'
        '  <text x="1080" y="318" text-anchor="start" direction="rtl" font-size="68" font-weight="500" fill="#e3c27a" font-family="Thmanyah, Segoe UI, sans-serif">شويتيفي</text>\n'
        '  <text x="1080" y="392" text-anchor="end" font-size="38" fill="#e3c27a" fill-opacity=".9" letter-spacing="3" font-family="Thmanyah, Segoe UI, sans-serif">Change Your TV</text>\n'
        '  <text x="1080" y="446" text-anchor="start" direction="rtl" font-size="40" font-weight="500" fill="#e3c27a" fill-opacity=".9" font-family="Thmanyah, Segoe UI, sans-serif">غيّر تلفزيونك</text>\n'
        '</svg>\n') % (DEFS, ART)

def page(svg, w, h):
    return ('<!doctype html><meta charset="utf-8"><style>'
            "@font-face{font-family:Thmanyah;font-weight:700;src:url(ThmanyahDisplay-Bold.otf)}"
            "@font-face{font-family:Thmanyah;font-weight:500;src:url(Thmanyah-Medium.otf)}"
            "html,body{margin:0;background:transparent;overflow:hidden}svg{display:block;width:%dpx;height:%dpx}</style>%s") % (w, h, svg)

def render(work, svg, w, h, dest):
    name = "r%dx%d_%d" % (w, h, abs(hash(dest)) % 10000)
    html, shot = os.path.join(work, name + ".html"), os.path.join(work, name + ".png")
    open(html, "w", encoding="utf-8").write(page(svg, w, h))
    win = lambda p: subprocess.check_output(["wslpath", "-w", p]).decode().strip() if CHROME.startswith("/mnt/") else p
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--default-background-color=00000000",
                    "--force-device-scale-factor=1", "--virtual-time-budget=4000", "--window-size=%d,%d" % (w, h),
                    "--screenshot=" + win(shot), "file:///" + win(html).replace("\\", "/")], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    shutil.copy(shot, dest)

def main():
    open(os.path.join(BRAND, "mark.svg"), "w", encoding="utf-8").write(MARK)
    open(os.path.join(BRAND, "banner.svg"), "w", encoding="utf-8").write(BANNER)
    work = os.environ.get("WORK") or tempfile.mkdtemp(prefix="logo", dir="/mnt/c/restore" if CHROME.startswith("/mnt/") else None)
    for f in ("ThmanyahDisplay-Bold.otf", "Thmanyah-Medium.otf"):
        shutil.copy(os.path.join(FONTS, f), work)
    for size, d in ((96, "xhdpi"), (144, "xxhdpi"), (192, "xxxhdpi")):
        render(work, MARK, size, size, os.path.join(RES, "mipmap-" + d, "ic_launcher.png"))
    render(work, MARK, 512, 512, os.path.join(BRAND, "icon-512.png"))
    render(work, BANNER, 320, 180, os.path.join(RES, "drawable-xhdpi/banner.png"))
    render(work, BANNER, 480, 270, os.path.join(RES, "drawable-xxhdpi/banner.png"))
    render(work, BANNER, 640, 360, os.path.join(BRAND, "banner.png"))
    open(os.path.join(BRAND, "hero.svg"), "w", encoding="utf-8").write(HERO)
    render(work, HERO, 1600, 500, os.path.join(BRAND, "hero.png"))
    print("logo: written (work dir %s)" % work)

if __name__ == "__main__":
    main()

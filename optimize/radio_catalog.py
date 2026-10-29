#!/usr/bin/env python3
"""Builds the radio catalogue the home screen's «الإذاعات» tab downloads: one small JSON per country, only streams that answered.

    optimize/radio_catalog.py OUT_DIR [CC ...]      (no countries = all the ones below)

The stations come from Radio Browser (the directory behind alondrilich/radio-playlists), most voted first. Each one is
tested (a real piece of the stream is read), family-filtered, sorted into one of five sections and kept to MAX_PER_COUNTRY,
so a TV only ever downloads a few kilobytes. Morocco is not here: its radios are chosen by hand and ship with the app.
Writes OUT_DIR/<cc>.json and OUT_DIR/index.json.
"""
import concurrent.futures
import datetime
import json
import os
import sys
import urllib.parse
import urllib.request

API = "https://de1.api.radio-browser.info/json/stations/bycountrycodeexact/%s"
MAX_PER_COUNTRY = 120
FETCH_LIMIT = 400          # asked per country, so that enough survive the test
TEST_TIMEOUT = 6
MIN_BYTES = 1024

# the countries the app offers (Arabic name, then French and English are in the app); order = order of the list
COUNTRIES = ["DZ", "TN", "EG", "SA", "AE", "QA", "KW", "JO", "LB", "IQ", "LY", "SD",
             "FR", "BE", "ES", "DE", "GB", "US", "CA", "TR", "IT", "NL", "SN"]

# what must never reach a family screen (a tag or a word of the name)
ADULT = ("adult", "porn", "xxx", "sex", "erotic", "erotica", "18+", "nsfw", "escort", "fetish", "hentai", "strip")

SECTIONS = {
    "religion": ("quran", "koran", "coran", "islam", "religio", "relig", "muslim", "adhan", "azan", "christian", "gospel", "bible", "church", "sunnah", "dhikr", "tilawa"),
    "news": ("news", "talk", "info", "politic", "actualit", "journal", "press"),
    "sport": ("sport", "football", "soccer", "basket", "tennis", "racing"),
    "culture": ("culture", "classical", "jazz", "education", "public radio", "arts", "literature", "history", "science", "documentary"),
}
ORDER = ("news", "music", "culture", "sport", "religion")


def is_family_safe(name, tags):
    """False when the name or a tag looks adult."""
    hay = (name or "").lower() + " " + (tags or "").lower()
    return not any(w in hay for w in ADULT)


def section_of(name, tags):
    """One of ORDER: the first section whose words appear in the tags (or the name); music otherwise."""
    hay = ((tags or "") + " " + (name or "")).lower()
    for section in ("religion", "sport", "news", "culture"):
        if any(w in hay for w in SECTIONS[section]):
            return section
    return "music"


def kind_of(codec, hls):
    """The short format shown next to the name: HLS, MP3, AAC…"""
    if hls:
        return "HLS"
    c = (codec or "").upper().replace("AAC+", "AAC")
    return c if c in ("MP3", "AAC", "OGG", "FLAC") else (c or "")


def pick(stations):
    """Family-safe, one per stream URL and per name, most voted first, at most MAX_PER_COUNTRY; each as the small record the TV reads."""
    seen_urls, seen_names, out = set(), set(), []
    for s in sorted(stations, key=lambda s: -int(s.get("votes") or 0)):
        url = (s.get("url_resolved") or s.get("url") or "").strip()
        name = " ".join((s.get("name") or "").split())
        if not url.startswith("https://") or not name:   # the app allows no cleartext traffic (network_security_config.xml)
            continue
        if not s.get("lastcheckok") or not is_family_safe(name, s.get("tags")):
            continue
        key = name.lower()
        if url in seen_urls or key in seen_names:
            continue
        seen_urls.add(url)
        seen_names.add(key)
        out.append({"i": (s.get("stationuuid") or "")[:8], "n": name, "u": url,
                    "c": section_of(name, s.get("tags")), "f": kind_of(s.get("codec"), s.get("hls")),
                    "t": (s.get("state") or "").strip()})
    return out


def plays(url):
    """True when a real piece of the stream comes back (audio, an ICY answer or an HLS playlist)."""
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "ChouiTV/1.0", "Icy-MetaData": "0"})
        with urllib.request.urlopen(req, timeout=TEST_TIMEOUT) as r:
            ctype = (r.headers.get("Content-Type") or "").lower()
            data = r.read(4096)
            if len(data) < MIN_BYTES and not data.startswith(b"#EXTM3U"):
                return False
            if data.startswith(b"#EXTM3U"):
                return True
            return ctype.startswith("audio/") or "ogg" in ctype or "mpegurl" in ctype or "octet-stream" in ctype \
                or r.headers.get("icy-name") is not None
    except Exception:
        return False


def build_country(cc, tester=plays, fetch=None):
    """The catalogue of one country: tested, filtered, sorted by section then by votes (the order `pick` left them in)."""
    if fetch is None:
        with urllib.request.urlopen(urllib.request.Request(
                API % cc + "?hidebroken=true&order=votes&reverse=true&limit=%d" % FETCH_LIMIT,
                headers={"User-Agent": "ChouiTV/1.0"}), timeout=30) as r:
            raw = json.load(r)
    else:
        raw = fetch(cc)
    candidates = pick(raw)
    with concurrent.futures.ThreadPoolExecutor(max_workers=32) as ex:
        ok = list(ex.map(lambda s: tester(s["u"]), candidates))
    alive = [s for s, good in zip(candidates, ok) if good][:MAX_PER_COUNTRY]
    alive.sort(key=lambda s: ORDER.index(s["c"]))   # stable: votes order kept inside a section
    return {"v": 1, "cc": cc, "stations": alive}


def main(argv):
    out = argv[1]
    countries = argv[2:] or COUNTRIES
    os.makedirs(out, exist_ok=True)
    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%MZ")
    index = {"v": 1, "updated": now, "countries": []}
    for cc in countries:
        try:
            data = build_country(cc)
        except Exception as e:
            print(cc, "failed:", e)
            continue
        data["updated"] = now
        with open(os.path.join(out, cc.lower() + ".json"), "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
        index["countries"].append({"cc": cc, "n": len(data["stations"])})
        print(cc, len(data["stations"]), "stations")
    with open(os.path.join(out, "index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, separators=(",", ":"))


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(sys.argv)

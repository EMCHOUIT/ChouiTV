#!/usr/bin/env python3
"""Builds optimize/lists/cleanup.json: the one file the phone installer reads to plan a cleanup. Standard library only.

It gathers what the shell scripts already know, so the two never disagree:
  keep.txt             protected packages (never in a plan, whatever a list says)
  home-replaced.txt    helpers of the stock home (handled by the home switch, never by the cleanup)
  tcl-safe.txt         our list for TCL sets
  tcl-optional.txt     what someone might use (off by default in the plan)
  community/*.txt      the CleanYourTV per-model lists (MIT, see community/LICENSE-CleanYourTV.txt)
  names-ar.txt         readable names

The result has a table of packages, each with a group (tracking, ads, preinstalled, optional), and a list of «lists», each one
a source (ours, a community model, or the consensus) that points into the table. The consensus is the packages that at least
CONSENSUS_MIN community lists turn off, that are not protected and not «optional» in kind: the careful plan for a model nobody listed.

    optimize/gen-cleanup-json.py            writes optimize/lists/cleanup.json
    optimize/gen-cleanup-json.py --check    exits 1 when the file on disk is not what the lists give now
"""
import json, os, re, sys, hashlib
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import community as C

HERE = os.path.dirname(os.path.abspath(__file__))
L = os.path.join(HERE, "lists")
OUT = os.path.join(L, "cleanup.json")
CONSENSUS_MIN = 20

TRACKING = re.compile(r"(analytics|statistic|logkit|\.bi$|\.bi\.|feedback|bugreport|telemetry|tracking|alphonso|usagestats|crash|datacollect|dataservice|metrics|\.stats)", re.I)
ADS = re.compile(r"(recommendation|autoinstalls|partnersetup|promo|messagebox|esticker|advert|\.ads?\b|adservice|showcase|exhibit|partnercustomizer|overseasappshow|onetimeinitializer|retaildemo|demo)", re.I)
# user-facing apps someone may use: never on by default
OPTIONAL = re.compile(r"(netflix|amazon\.amazonvideo|youtube\.tvmusic|youtube\.tvkids|android\.videos|play\.games|gamebar|dashboard|channelplus|waterfall|hungama|airtel|freeview|tvbrowser|gamecenter|tcl\.guard|dreams\.basic)", re.I)


# Brand names, the same in every language: shown in the installer's checklists instead of the package name.
DISPLAY = {
    "com.netflix.ninja": "Netflix", "com.netflix.mediaclient": "Netflix", "com.netflix.ninja.tv": "Netflix",
    "com.amazon.amazonvideo.livingroom": "Prime Video", "com.amazon.avod.thirdpartyclient": "Prime Video",
    "com.google.android.youtube.tvmusic": "YouTube Music", "com.google.android.youtube.tvkids": "YouTube Kids",
    "com.google.android.videos": "Google Play Films", "com.google.android.play.games": "Google Play Games",
    "com.disney.disneyplus": "Disney+", "com.spotify.tv.android": "Spotify", "tv.twitch.android.app": "Twitch",
    "com.hbo.hbonow": "HBO Max", "com.apple.atve.androidtv.appletv": "Apple TV", "com.cbs.ott": "Paramount+",
    "com.hulu.livingroomplus": "Hulu", "com.plexapp.android": "Plex", "org.xbmc.kodi": "Kodi", "org.videolan.vlc": "VLC",
    "net.mbc.shahidTV": "Shahid", "org.schabi.newpipe": "NewPipe", "com.phlox.tvwebbrowser": "TV Bro",
    "com.esaba.downloader": "Downloader", "org.localsend.localsend_app": "LocalSend", "com.ionitech.airscreen": "AirScreen",
    "com.lagradost.cloudstream3.prerelease": "CloudStream", "io.privado.android": "Privado VPN", "com.alphainventor.filemanager": "File Manager",
    "com.tcl.channelplus": "TCL Channel+", "com.tcl.guard": "TCL Guard", "com.tcl.gamebar": "TCL Game Bar",
    "com.android.printspooler": "Print spooler", "com.xiaomi.mitv.tvmanager": "Mi TV Manager",
}

def pkgs_of(path):
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            s = line.strip()
            if not s or s.startswith("#"):
                continue
            out.append(re.sub(r"^package:", "", s.split()[0]))
    return out


def group_of(pkg, optional_ours):
    if pkg in optional_ours or OPTIONAL.search(pkg):
        return "optional"
    if TRACKING.search(pkg):
        return "tracking"
    if ADS.search(pkg):
        return "ads"
    return "preinstalled"


def build():
    keep = sorted(set(pkgs_of(os.path.join(L, "keep.txt"))))
    keepset = set(keep)
    homes = sorted(set(pkgs_of(os.path.join(L, "home-replaced.txt"))))
    homeset = set(homes)
    safe = pkgs_of(os.path.join(L, "tcl-safe.txt"))
    optional_ours = set(pkgs_of(os.path.join(L, "tcl-optional.txt")))
    names = {}
    with open(os.path.join(L, "names-ar.txt"), encoding="utf-8") as f:
        for line in f:
            s = line.strip()
            if s and not s.startswith("#"):
                parts = s.split(None, 1)
                if len(parts) == 2:
                    names[parts[0]] = parts[1]
    table, index, lists = [], {}, []

    def idx(p):
        if p not in index:
            index[p] = len(table)
            table.append(p)
        return index[p]

    def ok(p):
        return p not in keepset and p not in homeset and not C.NOT_FOR_US.search(p)

    counts = {}
    comm_dir = os.path.join(L, "community")
    for n in sorted(os.listdir(comm_dir)):
        if not n.endswith(".txt") or n.startswith(("LICENSE", "README")):
            continue
        meta, pk = C.load(os.path.join(comm_dir, n))
        pk = [p for p in dict.fromkeys(pk) if ok(p)]
        for p in pk:
            counts[p] = counts.get(p, 0) + 1
        lists.append({"id": n[:-4], "source": "community", "brands": sorted(meta["brands"]), "models": sorted(meta["models"]),
                      "devices": sorted(meta["devices"]), "p": [idx(p) for p in pk]})
    lists.insert(0, {"id": "tcl", "source": "ours", "brands": ["TCL"], "models": [], "devices": [],
                     "p": [idx(p) for p in dict.fromkeys(safe + sorted(optional_ours)) if ok(p)]})
    consensus = sorted(p for p, c in counts.items() if c >= CONSENSUS_MIN and group_of(p, optional_ours) != "optional")
    lists.append({"id": "consensus", "source": "consensus", "brands": [], "models": [], "devices": [], "p": [idx(p) for p in consensus]})
    groups = [group_of(p, optional_ours) for p in table]
    used = set(table)
    return {
        "schema": 1,
        "note": "Made by optimize/gen-cleanup-json.py from optimize/lists. Community lists: CleanYourTV (MIT, github.com/Gathin23/cleanyourtv).",
        "consensusMin": CONSENSUS_MIN,
        "keep": keep,
        "homes": homes,
        "genericModels": sorted(C.GENERIC_MODELS),
        "genericDevices": sorted(C.GENERIC_DEVICES),
        "packages": table,
        "groups": groups,
        "names": {"ar": {p: v for p, v in names.items() if p in used}},
        "display": DISPLAY,
        "lists": lists,
    }


def dump(data):
    return json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n"


def main(argv):
    data = build()
    text = dump(data)
    if "--check" in argv:
        cur = open(OUT, encoding="utf-8").read() if os.path.exists(OUT) else ""
        print("up to date" if cur == text else "cleanup.json is not what the lists give now: run optimize/gen-cleanup-json.py")
        return 0 if cur == text else 1
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    g = {}
    for x in data["groups"]:
        g[x] = g.get(x, 0) + 1
    print("wrote %s: %d bytes, %d packages (%s), %d lists, consensus %d, sha256 %s" % (
        OUT, len(text.encode()), len(data["packages"]), ", ".join("%s %d" % kv for kv in sorted(g.items())), len(data["lists"]),
        len(data["lists"][-1]["p"]), hashlib.sha256(text.encode()).hexdigest()[:12]))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

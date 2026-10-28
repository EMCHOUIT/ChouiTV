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


# Readable names for the installer's checklists, so nobody has to read a package name. DISPLAY is the same in every language (brands and
# technical names); NAMES_FR is a French name where an English one would not help. Anything missing is made readable by the installer.
DISPLAY = {
    # streaming and entertainment
    "com.netflix.ninja": "Netflix", "com.netflix.mediaclient": "Netflix", "com.netflix.ninja.tv": "Netflix",
    "com.amazon.amazonvideo.livingroom": "Prime Video", "com.amazon.avod.thirdpartyclient": "Prime Video",
    "com.google.android.youtube.tvmusic": "YouTube Music", "com.google.android.youtube.tvkids": "YouTube Kids", "com.google.android.youtube.tv": "YouTube",
    "com.google.android.videos": "Google Play Movies & TV", "com.google.android.play.games": "Google Play Games", "com.google.android.music": "Google Play Music",
    "com.disney.disneyplus": "Disney+", "com.spotify.tv.android": "Spotify", "tv.twitch.android.app": "Twitch", "com.hbo.hbonow": "HBO Max",
    "com.apple.atve.androidtv.appletv": "Apple TV", "com.cbs.ott": "Paramount+", "com.hulu.livingroomplus": "Hulu", "com.plexapp.android": "Plex",
    "tv.pluto.android": "Pluto TV", "tv.wuaki.apptv": "Rakuten TV", "com.ted.android.tv": "TED", "com.amazon.minitv.android.app": "Amazon miniTV",
    "com.amazon.bueller.music": "Amazon Music", "com.amazon.bueller.photos": "Amazon Photos", "com.amazon.tahoe": "Amazon Kids", "com.amazon.gamehub": "Amazon Game Hub",
    "org.xbmc.kodi": "Kodi", "org.videolan.vlc": "VLC", "net.mbc.shahidTV": "Shahid", "org.schabi.newpipe": "NewPipe", "com.phlox.tvwebbrowser": "TV Bro",
    "com.esaba.downloader": "Downloader", "org.localsend.localsend_app": "LocalSend", "com.ionitech.airscreen": "AirScreen",
    "com.lagradost.cloudstream3.prerelease": "CloudStream", "io.privado.android": "Privado VPN", "com.alphainventor.filemanager": "File Manager",
    "io.gh.reisxd.tizentube.cobalt": "TizenTube", "com.irishin.buttonsremapper": "Button Mapper", "moe.shizuku.privileged.api": "Shizuku",
    "com.yablio.sendfilestotv": "Send Files to TV",
    # Google and Android services
    "com.google.android.feedback": "Google Feedback", "com.google.android.tvrecommendations": "Home screen recommendations",
    "com.google.android.partnersetup": "Google Partner Setup", "com.google.android.syncadapters.calendar": "Google Calendar sync",
    "com.google.android.backdrop": "Google Backdrop screensaver", "com.google.android.apps.tv.dreamx": "Google ambient screensaver",
    "com.google.android.onetimeinitializer": "Google one-time setup", "com.google.android.tv.bugreportsender": "Bug report sender",
    "com.android.tv.feedbackconsent": "Feedback consent", "com.google.android.tts": "Google Text-to-speech", "com.google.android.speech.pumpkin": "Google offline speech",
    "com.google.android.apps.nbu.smartconnect.tv": "Google Smart Connect", "android.autoinstalls.config.google.gtvpai": "Suggested apps auto-install",
    "com.android.printspooler": "Print service", "com.android.adservices.api": "Android ad services", "com.android.ondevicepersonalization.services": "On-device personalization",
    "com.android.federatedcompute.services": "Federated compute", "com.android.health.connect.backuprestore": "Health Connect backup",
    "com.android.healthconnect.controller": "Health Connect", "com.android.providers.calendar": "Calendar storage", "com.android.wallpaperbackup": "Wallpaper backup",
    "com.android.dreams.basic": "Basic screensaver", "com.android.dynsystem": "Dynamic system updates",
    # TCL
    "com.tcl.channelplus": "TCL Channel+", "com.tcl.guard": "TCL Guard", "com.tcl.gamebar": "TCL Game Bar", "com.tcl.esticker": "TCL e-sticker (promotions)",
    "com.tcl.messagebox": "TCL message box", "com.tcl.usercenter": "TCL user center", "com.tcl.partnercustomizer": "TCL partner offers",
    "com.tcl.waterfall.overseas": "TCL content feed", "com.tcl.ui_mediaCenter": "TCL media center", "com.tcl.bi": "TCL usage tracking",
    "com.tcl.logkit": "TCL log collector", "com.tcl.ocean.instructions": "TCL user guide", "com.tcl.dashboard": "TCL dashboard", "com.tcl.browser": "TCL browser",
    "com.tcl.repairguide": "TCL repair guide", "com.tcl.useragreement": "TCL user agreement", "com.tcl.exhibit": "TCL store demo mode",
    "android.autoinstalls.config.tcl.device": "TCL auto-install config", "com.tcl.t_solo": "TCL content", "com.tcl.assistant": "TCL assistant",
    "com.tcl.UpdatePeripheral": "TCL accessory update", "com.tcl.ttvs": "TCL TV services", "com.tcl.overseasappshow": "TCL app showcase",
    "com.tcl.hotelmenu": "TCL hotel mode", "com.tcl.interactive": "TCL interactive", "com.tcl.gallery": "TCL gallery", "com.tcl.copydatatotv": "TCL copy data to TV",
    "com.tcl.MultiScreenInteraction_TV": "TCL T-Cast", "com.tcl.notereminder": "TCL note reminder", "com.tcl.xian.StartandroidService": "TCL start service",
    # Xiaomi and MediaTek
    "com.xiaomi.mitv.tvmanager": "Mi TV Manager", "com.xm.webcontent": "Xiaomi web content", "com.miui.tv.analytics": "Xiaomi analytics",
    "com.xiaomi.statistic": "Xiaomi statistics", "com.xiaomi.mimusic2": "Mi Music", "com.mitv.gallery": "Mi Gallery", "com.mitv.videoplayer": "Mi Video player",
    "com.mitv.dream": "Mi screensaver", "com.xiaomi.mitv.updateservice": "Mi TV update service", "com.xiaomi.gamecenter.sdk.service.mibox": "Xiaomi game center",
    "android.autoinstalls.config.xiaomi.amelie": "Xiaomi auto-install config", "com.xiaomi.floatingframe": "Xiaomi floating frame",
    "com.mstar.android.tv.disclaimercustomization": "Legal notice customization", "com.mstar.netflixobserver": "Netflix observer", "co.sensara.tv.mitv.res": "Sensara remote helper",
    "fusion.android.tv.demo": "Store demo mode",
    "com.mitv.tvhome.atv": "Xiaomi home (PatchWall)", "com.mitv.tvhome.mitvplus": "Mi TV+",
    # Sony
    "com.sony.dtv.da.service": "Sony usage analytics", "com.qterics.da.product": "Sony data analytics", "com.sony.dtv.sonyselect": "Sony Select",
    "com.sony.dtv.multiscreendemo": "Sony multi-screen demo", "com.sony.dtv.demosupport": "Sony demo support", "com.sony.dtv.smarthelp": "Sony Smart Help",
    "com.sony.dtv.customersupport": "Sony customer support", "com.sony.dtv.promos": "Sony promotions", "android.autoinstalls.config.sony.bravia": "Sony auto-install config",
    "com.sony.dtv.sonybugreportsys": "Sony bug report", "com.sony.dtv.demosystemsupport": "Sony demo system", "com.sonyericsson.idd.agent": "Sony diagnostics",
    "com.sony.dtv.b2b.vendorprotocol": "Sony hotel protocol", "com.sony.dtv.discovery": "Sony Discovery", "com.sony.dtv.bivlinfo": "Sony BIVL info",
    "com.sony.dtv.imanual": "Sony i-Manual", "com.sony.dtv.osat.music": "Sony Music", "com.sony.dtv.sonytab": "Sony tab", "com.sony.dtv.gestureapp": "Sony gestures",
    "com.sony.dtv.demomode": "Sony demo mode", "com.sony.dtv.b2b.hotelmode": "Sony hotel mode", "com.sonyericsson.dlna": "Sony DLNA", "com.sony.huey.dlna.module": "Sony DLNA module",
    "com.sony.dtv.b2b.rs232csupport": "Sony RS-232 support", "com.sony.dtv.usercontext": "Sony user context", "com.sony.huey.dlna.renderersettings": "Sony DLNA renderer",
    # tracking and others
    "tv.samba.ssm": "Samba TV (viewing tracking)", "tv.samba.ssm.ui": "Samba TV", "tv.anoki.acr.anokiacroptin": "Anoki (viewing tracking)",
    "com.amazon.tv.acr": "Amazon viewing tracking", "com.amazon.dp.logger": "Amazon logger", "com.amazon.perfcollection": "Amazon performance data",
    "com.amazon.perfc": "Amazon performance collector", "com.amazon.wirelessmetrics.service": "Amazon wireless metrics", "com.amazon.client.metrics.api": "Amazon metrics",
    "com.amazon.hybridadidservice": "Amazon advertising ID", "com.amazon.shoptv.client": "Amazon Shop TV", "com.amazon.tmm.tutorial": "Fire TV tutorial",
    "com.amazon.storm.lightning.tutorial": "Fire TV remote tutorial", "com.amazon.sneakpeek": "Amazon sneak peek", "com.amazon.naatyam": "Amazon Naatyam",
    "com.amazon.kso.blackbird": "Amazon Blackbird", "com.smartdevice.recommendation": "Smart recommendations", "com.Smartdevice.emanual": "E-manual",
    "com.homwee.store": "Homwee store", "com.homwee.help": "Homwee help", "org.droidtv.candeebug": "Philips debug", "org.droidtv.gel": "Philips content",
}

NAMES_FR = {
    "com.google.android.feedback": "Retour d'expérience Google", "com.google.android.play.games": "Google Play Jeux", "com.google.android.videos": "Google Play Films et séries",
    "com.google.android.tvrecommendations": "Recommandations de l'accueil", "com.google.android.partnersetup": "Configuration partenaires Google",
    "com.android.printspooler": "Service d'impression", "com.google.android.syncadapters.calendar": "Synchro de l'agenda Google",
    "com.google.android.backdrop": "Écran de veille Google", "com.android.tv.feedbackconsent": "Consentement aux retours",
    "com.google.android.tv.bugreportsender": "Envoi de rapports de bug", "com.google.android.onetimeinitializer": "Configuration unique Google",
    "com.google.android.apps.tv.dreamx": "Écran de veille ambiant", "com.android.adservices.api": "Services publicitaires Android",
    "android.autoinstalls.config.google.gtvpai": "Installation auto d'applis suggérées", "fusion.android.tv.demo": "Mode démo de magasin",
    "com.xm.webcontent": "Contenu web Xiaomi", "com.android.ondevicepersonalization.services": "Personnalisation sur l'appareil",
    "com.android.federatedcompute.services": "Calcul fédéré Android", "com.miui.tv.analytics": "Analyses Xiaomi", "com.xiaomi.statistic": "Statistiques Xiaomi",
    "com.tcl.bi": "Suivi d'usage TCL", "com.mitv.tvhome.atv": "Accueil Xiaomi (PatchWall)", "com.mitv.tvhome.mitvplus": "Mi TV+", "com.tcl.logkit": "Collecte de journaux TCL", "com.mstar.android.tv.disclaimercustomization": "Mentions légales du constructeur",
    "com.android.dreams.basic": "Écran de veille de base", "com.xiaomi.mitv.tvmanager": "Gestionnaire TV Xiaomi", "com.tcl.messagebox": "Messages TCL",
    "com.tcl.esticker": "Autocollants promotionnels TCL", "com.tcl.exhibit": "Mode démo de magasin TCL", "com.tcl.hotelmenu": "Mode hôtel TCL",
    "com.google.android.youtube.tvmusic": "YouTube Music", "com.android.healthconnect.controller": "Santé Connect", "com.google.android.tts": "Synthèse vocale Google",
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
        "names": {"ar": {p: v for p, v in names.items() if p in used}, "fr": NAMES_FR},
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

#!/usr/bin/env python3
"""The «القرآن الكريم» tab's lists: the queue of surahs (SurahQueue.java), the saved playlists, the favourite reciters and the chip the tab
opens on (Playlists.java), run on tests/tilawah_lists_cases.tsv; and how the pieces are wired (source checks).
Needs javac for the cases; without it they are skipped with a note.   Run: python3 tests/tilawah_lists_test.py"""
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TIL = ROOT / "android/launcher/src/main/java/ma/chouitv/home/tilawah"
HOME = ROOT / "android/launcher/src/main/java/ma/chouitv/home"
RES = ROOT / "android/launcher/src/main/res"
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok


def text(p):
    return Path(p).read_text(encoding="utf-8")


cases = [l for l in text(ROOT / "tests/tilawah_lists_cases.tsv").splitlines() if l and not l.startswith("#")]
if shutil.which("javac") is None:
    print("SKIP tilawah lists cases: javac not found")
else:
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(TIL / "SurahQueue.java"), str(TIL / "Playlists.java"),
                        str(ROOT / "tests/TilawahListsRun.java")], check=True)
        stripped = Path(tmp) / "cases.tsv"
        stripped.write_text("\n".join("\t".join(l.split("\t")[:-1]) for l in cases) + "\n", encoding="utf-8")
        out = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", tmp, "ma.chouitv.home.tilawah.TilawahListsRun", str(stripped)],
                             capture_output=True, text=True, check=True, encoding="utf-8").stdout.split("\n")
        for line, got in zip(cases, out):
            f = line.split("\t")
            want = "" if f[-1] == "_" else f[-1]
            ok = got.startswith(want) if f[0] == "check" and want != "OK" else got == want
            check(" ".join(x[:24] for x in f[:-1]) + " -> " + want, ok, got)
        check("every case ran", len(out) - 1 == len(cases), f"{len(out) - 1} of {len(cases)}")

# ---- wiring
main = text(TIL / "MainActivity.java")
pane = text(TIL / "QuranPane.java")
service = text(TIL / "MusicService.java")
surah = text(TIL / "SurahActivity.java")
player = text(TIL / "PlayerActivity.java")
prefs = text(TIL / "Prefs.java")
strings = text(RES / "values/strings.xml")
layout = text(RES / "layout/activity_main.xml")
backup = text(TIL / "RadioBackup.java")

check("two tabs only: «القرآن الكريم» and «الإذاعات»", 'name="tab_quran">القرآن الكريم<' in strings and 'name="tab_ma">الإذاعات<' in strings
      and "tab_adhan" not in layout and "tab_stations" in layout)
check("the old «القرآن مباشر» tab is gone", "القرآن مباشر" not in strings.split('name="tab_quran"')[1].split("\n")[0])
check("the quran tab has the chips المفضلة · القرّاء · مباشر", "quran_fav" in strings and "quran_live" in strings
      and "Playlists.CHIP_FAV" in pane and "Playlists.CHIP_LIVE" in main and "quran_reciters" in pane)
check("the adhan tab is not in the listening screen any more", "TAB_ADHAN" not in main and "KIND_PRAYER" not in main)
check("the adhan screens stay compiled for the settings", (TIL / "AdhanActivity.java").exists() and (TIL / "PrayerOptionsActivity.java").exists())
check("the tab opens on the favourites when there are some", "Playlists.defaultChip(" in pane)
check("▲ from the chips goes to the tabs, ▼ into the cards, ▲ from the first row to the open chip",
      "paneHasFocus() && paneAtTop()" in main and "quranPane.focusFirst()" in main and "quranPane.upFromCards()" in main
      and "boolean upFromCards()" in pane)
check("◀ ▶ belong to the chips and the cards while they have the focus, to the tabs otherwise",
      "paneHasFocus() && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT" in main and "tabs.hasFocus() && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT" in main)
check("«رجوع» goes from the cards to the chips, then out", "cardsFocused()" in main and "focusChips();" in main)
check("a long OK on a reciter toggles the favourite, a long OK on a list opens its menu",
      "setOnItemLongClickListener" in pane and "ReciterStore.toggleFavourite(" in pane and "listMenu(" in pane)
check("the surah screen has a favourite button", "toggleFavourite" in surah and "fav_reciter" in strings)
check("the surah screen ticks surahs with a long OK", "setOnItemLongClickListener" in surah and "hint_surah" in strings)
check("the bottom bar: play the ticked, all, clear, save", all(k in strings for k in ("play_ticked", "tick_all", "tick_clear", "save_list")))
check("deleting a list asks first, with «إلغاء» as the first (focused) choice",
      "confirmDelete(" in pane and pane.index("delete_list_no") < pane.index("delete_list_yes", pane.index("confirmDelete(it)") + 1)
      and "ReciterStore.removeList(" in pane)
check("the service steps inside the queue and wraps round", "SurahQueue.step(" in service)
check("the queue is saved with the place for «متابعة»", "setQueue(" in prefs.replace("void setQueue(", "setQueue(") and "queue" in prefs and "SurahQueue.parse(" in service)
check("without a queue next and previous are what they were", "surah >= total ? 1 : surah + 1" in service and "surah <= 1 ? total : surah - 1" in service)
check("the player and the notification say «٢ من ٥» when a list plays",
      "queue_position" in strings and "queue_position" in player and "queue_position" in service)
check("the backup carries the favourites and the lists", "rfav" in backup and "plists" in backup and "Playlists.check(" in backup)
check("an old file without them is accepted: the favourites and lists are emptied, not refused",
      'optString("rfav")' in backup and 'optString("plists")' in backup and "applyReciters(c, want)" in backup)
check("the favourite reciters are a set of ids in their own prefs file, like the radios' favourites",
      'getStringSet("fav"' in text(TIL / "ReciterStore.java") and '"reciters"' in text(TIL / "ReciterStore.java"))
check("the music.sh hook takes a queue", 'EXTRA_QUEUE' in text(TIL / "MusicReceiver.java"))
check("four chips: القرّاء المفضلون · قوائم السور · القرّاء · مباشر",
      'name="quran_fav">القرّاء المفضلون<' in strings and 'name="quran_lists">قوائم السور<' in strings
      and "Playlists.CHIP_LISTS" in pane and "CHIP_LISTS = 1" in text(TIL / "Playlists.java"))
check("each empty chip has its own one-line invitation",
      "لا قرّاء مفضلون بعد: اضغط مطوّلًا على قارئ" in strings and "لا قوائم بعد: حدّد سورًا في صفحة قارئ ثم «حفظ كقائمة»" in strings
      and "quran_no_lists" in pane)
check("the list menu has «إعادة التسمية» before «حذف»",
      pane.index("list_menu_rename") < pane.index("list_menu_delete") and "NameDialog.ask(" in pane and "ReciterStore.renameList(" in pane)
dialog = text(TIL / "NameDialog.java")
check("the name dialog: one-line EditText, keyboard up, empty refused, cut to 40",
      "EditText" in dialog and "SOFT_INPUT_STATE_ALWAYS_VISIBLE" in dialog and "name.isEmpty()" in dialog and "LengthFilter(Playlists.MAX_NAME)" in dialog
      and "Playlists.MAX_NAME = 40" .replace("Playlists.", "") in text(TIL / "Playlists.java").replace(" ", " ") or "MAX_NAME = 40" in text(TIL / "Playlists.java"))
check("«حفظ كقائمة» asks for the name, prefilled with the automatic one", "NameDialog.ask(" in surah and "Playlists.nextName(" in surah and "addNamedList(" in surah)
sys.exit(1 if failed else 0)

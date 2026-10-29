#!/usr/bin/env python3
"""The radios' screens, as source checks: four stations to a row with the countries as chips, a bar that stops the playing station, the countries in a grid
of four with the update button at the top, the progress of the update (a bar and the state of each country) and the date of the last one.
Run: python3 tests/radio_ui_test.py"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TIL = ROOT / "android/launcher/src/main/java/ma/chouitv/home/tilawah"
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok


pane = (TIL / "RadioPane.java").read_text(encoding="utf-8")
countries = (TIL / "RadioCountriesActivity.java").read_text(encoding="utf-8")
store = (TIL / "RadioStore.java").read_text(encoding="utf-8")
strings = (ROOT / "android/launcher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
manifest = (ROOT / "android/launcher/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

check("the stations are a grid of four", "grid.setNumColumns(4)" in pane)
check("the countries are chips (favourites first, the settings last)", "C_FAV" in pane and "C_CHOOSE" in pane and "buildChips()" in pane)
check("a bar while a station plays: OK on it stops", "MusicService.stopAction(getContext())" in pane and "radio_now_bar" in pane)
check("the countries are a grid of four, one tick each", "grid.setNumColumns(4)" in countries and "RadioChoice.toggle" in countries)
check("the update button is at the top, next to the title", "head.addView(update" in countries)
check("the update shows a bar and the state of each country", "Bar" in countries and "drawSteps()" in countries and "Progress.LOADING" in countries)
check("the update ends well, partly or badly, each in its words",
      "radio_update_done" in countries and "radio_update_partial" in countries and "radio_update_failed_title" in countries and "radio_update_failed_hint" in countries)
check("the date of the last update, or that there was none", "RadioStore.stamp(at)" in countries and "radio_never" in countries and "آخر تحديث" in strings)
check("the progress is per country, on the main thread", "interface Progress" in store and "WAITING" in store and "FAILED" in store and "ui.post(() -> progress.step" in store)
check("a country that fails keeps its old list (the file is replaced only by a good one)", ".part" in store and "renameTo" in store)
check("the button says «تحديث الإذاعات»", '<string name="radio_update">تحديث الإذاعات</string>' in strings)
check("the countries page is declared", ".tilawah.RadioCountriesActivity" in manifest)
sys.exit(1 if failed else 0)

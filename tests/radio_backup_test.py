#!/usr/bin/env python3
"""The radios in «تصدير / استيراد الإعدادات»: what a file may say about them (RadioBackupRules.java on tests/radio_backup_cases.tsv), and how
the pieces are wired (source checks). Needs javac for the cases; without it they are skipped with a note.   Run: python3 tests/radio_backup_test.py"""
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TIL = ROOT / "android/launcher/src/main/java/ma/chouitv/home/tilawah"
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok


cases = [l for l in (ROOT / "tests/radio_backup_cases.tsv").read_text(encoding="utf-8").splitlines() if l and not l.startswith("#")]
if shutil.which("javac") is None:
    print("SKIP radio backup cases: javac not found")
else:
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["javac", "-d", tmp, str(TIL / "RadioChoice.java"), str(TIL / "RadioBackupRules.java"), str(ROOT / "tests/RadioBackupRulesRun.java")], check=True)
        out = subprocess.run(["java", "-cp", tmp, "ma.chouitv.home.tilawah.RadioBackupRulesRun", str(ROOT / "tests/radio_backup_cases.tsv")],
                             capture_output=True, text=True, check=True).stdout.splitlines()
        for line, got in zip(cases, out):
            f = line.split("\t")
            want = f[3]
            check(f"{f[0] or '-'} | {f[1] or '-'} | {f[2][:20] or '-'} -> {want}", got.startswith(want) if want != "OK" else got == "OK", got)
        check("every case ran", len(out) == len(cases), f"{len(out)} of {len(cases)}")

data = (ROOT / "android/launcher/src/main/java/ma/chouitv/home/BackupData.java").read_text(encoding="utf-8")
backup = (TIL / "RadioBackup.java").read_text(encoding="utf-8")
saver = (ROOT / "android/launcher/src/main/java/ma/chouitv/screensaver/SettingsBackup.java").read_text(encoding="utf-8")
check("the home export carries the radios", 'o.put("radio", ma.chouitv.home.tilawah.RadioBackup.export(c))' in data)
check("an import checks the radios whole before it writes anything", data.index("RadioBackup.check(radio)") < data.index("int n = write(adhanPrefs(c)"))
check("the radios are written with the adhan and the dock", "RadioBackup.apply(c, radio)" in data)
check("a country of the file with no list here is downloaded after the import", "downloadMissing(c)" in backup and "RadioStore.changed(c)" in backup)
check("the file does not carry the downloaded lists, only the choices", "countries" in backup and "fav" in backup and "downloaded" not in backup.split("export(")[1].split("private static String join")[0])
check("the screensaver compares the radios and names them", '"adhan", "dock", "radio"' in saver and "radio.countries" in saver)
sys.exit(1 if failed else 0)

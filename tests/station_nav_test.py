#!/usr/bin/env python3
"""Which radio comes next or before, and where a remembered stream is now (StationNav.java on tests/station_nav_cases.tsv): ◀ ▶ stay inside the
station's own group, and a station is found again by its stream, not by its place. Needs javac; without it the cases are skipped.
Run: python3 tests/station_nav_test.py"""
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


cases = [l for l in (ROOT / "tests/station_nav_cases.tsv").read_text(encoding="utf-8").splitlines() if l and not l.startswith("#")]
if shutil.which("javac") is None:
    print("SKIP station nav cases: javac not found")
else:
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["javac", "-d", tmp, str(TIL / "StationNav.java"), str(ROOT / "tests/StationNavRun.java")], check=True)
        stripped = Path(tmp) / "cases.tsv"
        stripped.write_text("\n".join("\t".join(l.split("\t")[:-1]) for l in cases) + "\n", encoding="utf-8")
        out = subprocess.run(["java", "-cp", tmp, "ma.chouitv.home.tilawah.StationNavRun", str(stripped)], capture_output=True, text=True, check=True).stdout.splitlines()
        for line, got in zip(cases, out):
            f = line.split("\t")
            check(f"{f[0]} {f[1][:30]} {f[2][:14]} -> {f[-1]}", got == f[-1], got)
        check("every case ran", len(out) == len(cases), f"{len(out)} of {len(cases)}")
sys.exit(1 if failed else 0)

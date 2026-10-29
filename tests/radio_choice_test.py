#!/usr/bin/env python3
"""The radio countries (RadioChoice.java on tests/radio_choice_cases.tsv): three at most, Morocco when nothing is chosen, never none.
Needs javac; without it the cases are skipped with a note.   Run: python3 tests/radio_choice_test.py"""
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


cases = [l for l in (ROOT / "tests/radio_choice_cases.tsv").read_text(encoding="utf-8").splitlines() if l and not l.startswith("#")]
if shutil.which("javac") is None:
    print("SKIP radio choice cases: javac not found")
else:
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["javac", "-d", tmp, str(TIL / "RadioChoice.java"), str(ROOT / "tests/RadioChoiceRun.java")], check=True)
        stripped = Path(tmp) / "cases.tsv"
        stripped.write_text("\n".join("\t".join(l.split("\t")[:-1]) for l in cases) + "\n", encoding="utf-8")
        out = subprocess.run(["java", "-cp", tmp, "ma.chouitv.home.tilawah.RadioChoiceRun", str(stripped)],
                             capture_output=True, text=True, check=True).stdout.splitlines()
        for line, got in zip(cases, out):
            f = line.split("\t")
            check(" ".join(f[:-1]) + " -> " + f[-1], got == f[-1], got)
        check("every case ran", len(out) == len(cases), f"{len(out)} of {len(cases)}")

src = (TIL / "RadioChoice.java").read_text(encoding="utf-8")
check("at most three countries", "static final int MAX = 3;" in src)
sys.exit(1 if failed else 0)

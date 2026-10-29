#!/usr/bin/env python3
"""What an update has to do (UpdatePlanner.java on tests/update_planner_cases.tsv), ONE package since 4.0: a one-entry manifest, an old two-entry one
(only our own package is acted on), our package already at target, a lower or equal versionCode refused, a foreign-signed APK refused, the plan
entry dropped in the new process, a person who refused waits a day and a technical failure only 30 minutes. Needs javac; without it the cases are skipped.
Run: python3 tests/update_planner_test.py"""
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / "android/launcher/src/main/java/ma/chouitv/home"
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok


cases = [l for l in (ROOT / "tests/update_planner_cases.tsv").read_text(encoding="utf-8").splitlines() if l and not l.startswith("#")]
if shutil.which("javac") is None:
    print("SKIP update planner cases: javac not found")
else:
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["javac", "-d", tmp, str(HOME / "UpdatePlanner.java"), str(ROOT / "tests/UpdatePlannerRun.java")], check=True)
        stripped = Path(tmp) / "cases.tsv"
        stripped.write_text("\n".join("\t".join(l.split("\t")[:-1]) for l in cases) + "\n", encoding="utf-8")
        out = subprocess.run(["java", "-cp", tmp, "ma.chouitv.home.UpdatePlannerRun", str(stripped)], capture_output=True, text=True, check=True).stdout.splitlines()
        for line, got in zip(cases, out):
            f = line.split("\t")
            check(" ".join(f[:-1]) + " -> " + f[-1], got == f[-1], got)
        check("every case ran", len(out) == len(cases), f"{len(out)} of {len(cases)}")
sys.exit(1 if failed else 0)

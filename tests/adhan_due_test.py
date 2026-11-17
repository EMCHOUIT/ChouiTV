#!/usr/bin/env python3
"""The adhan is not played once its time is long past (AdhanDue.java): a TV in standby delivers the alarm only when it is switched
on again, hours late, and the adhan must not start then. Exact alarms get a short grace (jitter); without the exact-alarm permission
(Android 12+) Android itself may deliver minutes late, so the grace is longer. Needs javac; without it, skipped with a note.
Run: python3 tests/adhan_due_test.py"""
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if not shutil.which("javac"):
    print("SKIP adhan due: no javac here")
    sys.exit(0)

S, MIN, H = 1000, 60_000, 3_600_000
DUE = 1_794_893_640_000          # a prayer time (ms since 1970)
# (due, now, exact alarms allowed, expected, note)
CASES = [
    (DUE, DUE, True, "play", "on time"),
    (DUE, DUE + 90 * S, True, "play", "an exact alarm 90 s late: jitter, still plays"),
    (DUE, DUE + 2 * MIN, True, "play", "exactly the grace (2 min): still plays"),
    (DUE, DUE + 2 * MIN + S, True, "stale", "2 min 1 s late with an exact alarm: too late"),
    (DUE, DUE + 3 * H, True, "stale", "standby for 3 hours, alarm delivered when the TV is switched on: must NOT play"),
    (DUE, DUE + 25 * MIN, True, "stale", "25 min late: the prayer has passed"),
    (DUE, DUE + 8 * MIN, False, "play", "no exact-alarm permission, 8 min late: Android's own delay, plays"),
    (DUE, DUE + 10 * MIN, False, "play", "exactly the longer grace (10 min): plays"),
    (DUE, DUE + 10 * MIN + S, False, "stale", "no exact permission, 10 min 1 s late: too late"),
    (DUE, DUE + 3 * H, False, "stale", "no exact permission, standby for 3 hours: must NOT play"),
    (0, DUE + 3 * H, True, "play", "an alarm with no prayer time (an older version's): cannot be judged, left to the duplicate rule"),
    (DUE, DUE - 5 * S, True, "play", "delivered 5 s early: plays"),
]
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp,
                    str(ROOT / "android/launcher/src/main/java/ma/chouitv/home/tilawah/AdhanDue.java"),
                    str(ROOT / "tests/AdhanDueRun.java")], check=True)
    args = [str(x) if not isinstance(x, bool) else str(x).lower() for c in CASES for x in c[:3]]
    out = subprocess.run(["java", "-cp", tmp, "ma.chouitv.home.tilawah.AdhanDueRun", *args], check=True, capture_output=True).stdout
got = out.decode("utf-8").split("\n")
failed = 0
for c, g in zip(CASES, got):
    ok = g == c[3]
    print(("PASS " if ok else "FAIL ") + f"adhan due: {c[4]}" + ("" if ok else f"  -> {g!r}, wanted {c[3]!r}"))
    failed += not ok
if len(got) < len(CASES):
    print("FAIL fewer answers than cases")
    failed += 1

# the receiver must actually ask, before playing (the pure rule alone changes nothing)
rx = (ROOT / "android/launcher/src/main/java/ma/chouitv/home/tilawah/AdhanReceiver.java").read_text(encoding="utf-8")
ok = "AdhanDue.stale(" in rx
print(("PASS " if ok else "FAIL ") + "adhan due: AdhanReceiver asks AdhanDue.stale before playing")
failed += not ok
sys.exit(1 if failed else 0)

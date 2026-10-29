#!/usr/bin/env python3
"""Who may announce a version: ManifestTrust.java accepts only the owner's signature of the exact bytes of latest.json, and the file in the repository
is signed with the key the apps carry. Needs javac and openssl; without them the signature cases are skipped.
Run: python3 tests/manifest_trust_test.py"""
import base64
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


trust = (HOME / "ManifestTrust.java").read_text(encoding="utf-8")
pub = (ROOT / "optimize/manifest-pub.b64").read_text().strip()
check("the apps carry the public key of optimize/manifest-pub.b64", f'"{pub}"' in trust)
update = (HOME / "SelfUpdate.java").read_text(encoding="utf-8")
check("SelfUpdate believes a version file only when its signature checks", "ManifestTrust.verify(" in update and ".sig" in update)
check("the signature is checked before the file is parsed, so nothing from an unsigned file is acted on",
      update.index("ManifestTrust.verify(") < update.index("new JSONObject(new String(file"))
check("a signature mismatch is retried in 30 minutes (right after a release the file and its signature can come from two moments)",
      "now - CHECK_EVERY_MS + 30 * 60_000L" in update)

if shutil.which("javac") is None or shutil.which("openssl") is None or shutil.which("java") is None:
    print("SKIP signature cases: javac, java or openssl not found")
else:
    def run(tmp, classes, file, sig, pubfile=None):
        args = ["java", "-cp", classes, "ma.chouitv.home.ManifestTrustRun", str(file), str(sig)] + ([str(pubfile)] if pubfile else [])
        return subprocess.run(args, capture_output=True, text=True, check=True).stdout.strip() == "true"

    with tempfile.TemporaryDirectory() as t:
        tmp = Path(t)
        classes = str(tmp / "classes")
        subprocess.run(["javac", "-d", classes, str(HOME / "ManifestTrust.java"), str(ROOT / "tests/ManifestTrustRun.java")], check=True)

        def keypair(name):
            key = tmp / f"{name}.pem"
            subprocess.run(["openssl", "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", str(key)], check=True, capture_output=True)
            der = subprocess.run(["openssl", "ec", "-in", str(key), "-pubout", "-outform", "DER"], check=True, capture_output=True).stdout
            p = tmp / f"{name}.pub"
            p.write_text(base64.b64encode(der).decode())
            return key, p

        def sign(key, data_file, out):
            der = subprocess.run(["openssl", "dgst", "-sha256", "-sign", str(key), str(data_file)], check=True, capture_output=True).stdout
            out.write_text(base64.b64encode(der).decode())

        key, pubf = keypair("owner")
        other, otherpub = keypair("other")
        manifest = tmp / "latest.json"
        manifest.write_text('{"versionCode": 180, "versionName": "3.180", "apks": []}')
        sig = tmp / "latest.json.sig"
        sign(key, manifest, sig)
        check("the owner's signature of the exact file is accepted", run(tmp, classes, manifest, sig, pubf))
        tampered = tmp / "tampered.json"
        tampered.write_text('{"versionCode": 181, "versionName": "3.181", "apks": []}')
        check("a changed file is refused", not run(tmp, classes, tampered, sig, pubf))
        check("a signature by another key is refused", not run(tmp, classes, manifest, sig, otherpub))
        junk = tmp / "junk.sig"
        junk.write_text("not a signature at all")
        check("a signature that cannot be read is refused", not run(tmp, classes, manifest, junk, pubf))
        empty = tmp / "empty.sig"
        empty.write_text("")
        check("no signature is refused", not run(tmp, classes, manifest, empty, pubf))
        check("a signature with a trailing newline is accepted", run(tmp, classes, manifest, (lambda p: (p.write_text(sig.read_text() + "\n"), p)[1])(tmp / "nl.sig"), pubf))

        # the file in the repository against the key the apps carry
        repo_sig = ROOT / "latest.json.sig"
        check("latest.json.sig exists next to latest.json", repo_sig.exists())
        if repo_sig.exists():
            check("latest.json is signed with the key the apps carry", run(tmp, classes, ROOT / "latest.json", repo_sig))
sys.exit(1 if failed else 0)

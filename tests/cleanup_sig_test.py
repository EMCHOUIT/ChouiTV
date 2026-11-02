"""optimize/lists/cleanup.json must carry a valid signature (cleanup.json.sig, made with the owner's manifest key by optimize/sign-manifest.sh).

The Installer believes the published lists only when this signature matches, so a change to the file without signing it again makes the
phones ignore the online copy. When CHOUITV_INSTALLER_DIR points at the Installer checkout, its bundled copy must be the same file.

    python3 tests/cleanup_sig_test.py
"""
import base64, os, subprocess, sys, tempfile

here = os.path.dirname(os.path.abspath(__file__))
root = os.path.join(here, "..")
lists = os.path.join(root, "optimize", "lists")
failed = 0


def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + detail))
    failed += not ok


def verify(path, sig_path):
    tmp = tempfile.mkdtemp()
    pub, sig, pem = (os.path.join(tmp, n) for n in ("pub.der", "sig.der", "pub.pem"))
    open(pub, "wb").write(base64.b64decode(open(os.path.join(root, "optimize", "manifest-pub.b64")).read()))
    open(sig, "wb").write(base64.b64decode(open(sig_path).read()))
    subprocess.run(["openssl", "ec", "-pubin", "-inform", "DER", "-in", pub, "-outform", "PEM", "-out", pem], check=True, capture_output=True)
    r = subprocess.run(["openssl", "dgst", "-sha256", "-verify", pem, "-signature", sig, path], capture_output=True)
    return r.returncode == 0


cleanup = os.path.join(lists, "cleanup.json")
sigfile = cleanup + ".sig"
check("cleanup.json.sig exists next to cleanup.json", os.path.exists(sigfile), "run optimize/sign-manifest.sh optimize/lists/cleanup.json")
if os.path.exists(sigfile):
    check("the signature matches the owner's manifest key", verify(cleanup, sigfile), "cleanup.json changed after it was signed: sign it again")

inst = os.environ.get("CHOUITV_INSTALLER_DIR")
if inst:
    bundled = os.path.join(inst, "app", "src", "main", "assets", "cleanup.json")
    check("the Installer's bundled cleanup.json is the same file", os.path.exists(bundled) and open(bundled, "rb").read() == open(cleanup, "rb").read(), bundled)
else:
    print("skip the Installer copy check (set CHOUITV_INSTALLER_DIR to the Installer checkout)")
sys.exit(1 if failed else 0)

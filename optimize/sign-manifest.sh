#!/usr/bin/env bash
# Signs the version file with the owner's manifest key, then checks the signature against the public key the apps carry.
#   optimize/sign-manifest.sh [latest.json]        writes latest.json.sig next to it
# The private key stays on this computer: ~/chouitv-signing/manifest-key.pem (override: MANIFEST_KEY=/path/key.pem). Back it up like the APK key.
# Release order: sign, THEN commit latest.json and latest.json.sig in the same push (the TVs read both).
set -euo pipefail
cd "$(dirname "$0")/.."
FILE="${1:-latest.json}"
KEY="${MANIFEST_KEY:-$HOME/chouitv-signing/manifest-key.pem}"
[ -f "$KEY" ] || { echo "!! manifest key not found: $KEY" >&2; exit 1; }
openssl dgst -sha256 -sign "$KEY" -out "$FILE.sig.der" "$FILE"
base64 -w0 "$FILE.sig.der" > "$FILE.sig"
rm -f "$FILE.sig.der"
# the check uses the public key in the repository, the one the apps carry
PUB=$(mktemp)
base64 -d optimize/manifest-pub.b64 > "$PUB"
base64 -d "$FILE.sig" > "$FILE.sig.der"
if openssl dgst -sha256 -verify <(openssl ec -pubin -inform DER -in "$PUB" -outform PEM 2>/dev/null) -signature "$FILE.sig.der" "$FILE" > /dev/null; then
  echo "OK   $FILE signed ($FILE.sig)"
else
  rm -f "$FILE.sig" "$FILE.sig.der" "$PUB"
  echo "!! the signature does not match optimize/manifest-pub.b64 (wrong key?)" >&2
  exit 1
fi
rm -f "$FILE.sig.der" "$PUB"

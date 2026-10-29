#!/usr/bin/env bash
# Checks that APKs are signed with THE ChouiTV key (the one the TVs already trust): its SHA-256 is pinned in .github/signer-sha256.txt.
#   optimize/check-signer.sh chouitv-home.apk chouitv-screensaver.apk      (run it before every release; exit 1 on any mismatch)
set -euo pipefail
cd "$(dirname "$0")/.."
want=$(tr -d '[:space:]:' < .github/signer-sha256.txt | tr 'A-F' 'a-f')
apksigner=$(ls -d "${ANDROID_HOME:-$HOME/android}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)
[ -n "$apksigner" ] || { echo "apksigner not found (set ANDROID_HOME)" >&2; exit 2; }
[ $# -gt 0 ] || { echo "usage: optimize/check-signer.sh APK..." >&2; exit 2; }
bad=0
for f in "$@"; do
  got=$("$apksigner" verify --print-certs "$f" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1 | tr -d '[:space:]:' | tr 'A-F' 'a-f')
  if [ "$got" = "$want" ]; then echo "OK   $f"; else echo "FAIL $f: signed with ${got:-no key} instead of $want" >&2; bad=1; fi
done
exit $bad

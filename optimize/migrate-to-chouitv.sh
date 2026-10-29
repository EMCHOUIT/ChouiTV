#!/usr/bin/env bash
# Moves a TV that still runs the apps under their old names (sa.urtv.screensaver, sa.urtv.home) to ChouiTV 4.0 (one app, ma.chouitv.home).
# They are other apps for Android, so they cannot update into each other: the new one is installed next to the old ones, the settings are carried over,
# the new home screen becomes Home, and only then the old apps are removed.
#   optimize/migrate-to-chouitv.sh                   the settings come from the old screensaver's own export (its build 3.170 or newer)
#   optimize/migrate-to-chouitv.sh settings.json     the settings come from this file (an `optimize/saver.sh export` of this TV or of another one)
#   APK_DIR=/folder optimize/migrate-to-chouitv.sh   the new APKs (chouitv-home.apk, channels-release.apk) from a folder, not downloaded
# What is not carried over: the person's photos and tasks (they belong to the old app's storage). The TV's own settings (Wi-Fi, accounts, other apps) are not touched.
set -euo pipefail
cd "$(dirname "$0")/.."
PY="${PY:-optimize/venv/bin/python}"
OLD_S=sa.urtv.screensaver; OLD_H=sa.urtv.home
NEW_H=ma.chouitv.home
RUN=optimize/run
mkdir -p "$RUN"
tv() { "$PY" -I optimize/tv.py "$@"; }
installed() { tv "pm list packages $1" | tr -d '\r' | grep -qx "package:$1"; }

if ! installed "$OLD_S" && ! installed "$OLD_H"; then
  echo "No apps under the old names (sa.urtv.*) on this TV: nothing to move. A new setup is optimize/run.sh, or optimize/install.sh all."
  exit 0
fi

# 1. the settings to carry over
SETTINGS="${1:-}"
if [ -z "$SETTINGS" ]; then
  out=$(tv "am broadcast -n $OLD_S/.SaverReceiver -a $OLD_S.EXPORT" | sed -n '/data="/,$p' | sed '1s/.*data="//; $s/"$//' || true)
  if [[ "$out" == \{* ]]; then
    printf '%s\n' "$out" > "$RUN/settings-before-migration.json"
    SETTINGS="$RUN/settings-before-migration.json"
    echo "The old screensaver's settings are saved in $SETTINGS."
  else
    echo "!! The old screensaver cannot export its settings (it is older than 3.170). Give a settings file:"
    echo "   optimize/migrate-to-chouitv.sh settings.json      (an export of another TV, e.g. profil-tv1/tv1-reglages.json)"
    exit 1
  fi
fi
[ -f "$SETTINGS" ] || { echo "!! No such file: $SETTINGS"; exit 1; }

# 2. the new app, next to the old ones (the new home screen takes Home behind the safety switch of install.sh)
bash optimize/install.sh all

# 3. the settings in the new app
echo "Carrying the settings over:"
bash optimize/saver.sh import "$SETTINGS" --yes

# 4. only when the new home screen really is Home, the old apps go
res=$(tv "cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN" | tr -d '\r')
if ! grep -q "$NEW_H" <<< "$res"; then
  echo "!! The new home screen is not Home yet: the old apps are left as they are. Run this command again once it is."
  exit 1
fi
for p in "$OLD_H" "$OLD_S"; do
  if installed "$p"; then tv "pm uninstall $p" | tr -d '\r' | tail -1; fi
done
echo "Done: the TV runs ChouiTV (ma.chouitv.home). Press Home to check, then optimize/saver.sh show."

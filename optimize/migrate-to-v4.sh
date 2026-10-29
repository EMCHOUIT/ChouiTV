#!/usr/bin/env bash
# Moves a 3.x TV (TWO apps: the home screen ma.chouitv.home and the screensaver ma.chouitv.screensaver) to ChouiTV 4.0 (ONE app:
# ma.chouitv.home carries the home screen, the «تلاوة» screens, the screensaver and its settings).
# The home app is updated in place (same package, same key); the screensaver app is a separate package that 4.0 no longer
# ships, so its settings are carried over first and the old app is removed only at the end, after everything was checked.
#   optimize/migrate-to-v4.sh                  the settings come from the old screensaver's own export
#   optimize/migrate-to-v4.sh settings.json    the settings come from this file (an `optimize/saver.sh export` of this TV or of another one)
#   optimize/migrate-to-v4.sh --yes            do not ask before removing the old screensaver app
#   APK_DIR=/folder optimize/migrate-to-v4.sh  chouitv-home.apk from a folder, not downloaded (APK_TAG=v4.N: a numbered build)
# Order, and why the TV is never left without a screensaver or a home screen:
#   1. export the old screensaver's settings to optimize/run/settings-before-v4.json (nothing is changed on the TV yet)
#   2. install the 4.0 APK over the home app (optimize/install.sh app: no screensaver or Home switching; the old
#      screensaver app is still there, so the TV keeps a working screensaver the whole time)
#   3. wait for the new settings receiver, then import the settings (action IMPORT, all or nothing)
#   4. point the TV's screensaver at the new dream (only if it used ours, or none; another screensaver is the person's choice)
#   5. check the result; only then, after a question (not with --yes), remove ma.chouitv.screensaver
# What is not carried over: the photos «صوري» and the tasks (they belong to the old app's storage, the export does not hold
# them); optimize/photos.sh adds the photos again. The TV's own settings (Wi-Fi, accounts, other apps) are not touched.
set -euo pipefail
cd "$(dirname "$0")/.."
PY="${PY:-optimize/venv/bin/python}"
OLD=ma.chouitv.screensaver
NEW=ma.chouitv.home
RX_OLD="$OLD/.SaverReceiver"
RX_NEW="$NEW/ma.chouitv.screensaver.SaverReceiver"
DREAM="$NEW/ma.chouitv.screensaver.ScreenDream"
RUN="${URTV_RUN_DIR:-optimize/run}"
mkdir -p "$RUN"
tv() { "$PY" -I optimize/tv.py "$@"; }
installed() { tv "pm list packages $1" | tr -d '\r' | grep -qx "package:$1"; }
data_of() { sed -n '/data="/,$p' | sed '1s/.*data="//; $s/"$//'; }   # the receiver's answer out of `am broadcast`

yes=0; SETTINGS=
for a in "$@"; do
  case "$a" in
    --yes) yes=1 ;;
    -*) echo "usage: optimize/migrate-to-v4.sh [settings.json] [--yes]"; exit 2 ;;
    *) SETTINGS=$a ;;
  esac
done

if ! installed "$OLD"; then
  echo "No separate screensaver app ($OLD) on this TV: nothing to move to 4.0. Update with optimize/install.sh all."
  exit 0
fi
installed "$NEW" || echo "Note: the home app ($NEW) is not on this TV yet: 4.0 is installed fresh."

# 1. the settings to carry over (read only)
if [ -z "$SETTINGS" ]; then
  out=$(tv "am broadcast -n $RX_OLD -a $OLD.EXPORT" | data_of || true)
  if [[ "$out" == \{* ]]; then
    printf '%s\n' "$out" > "$RUN/settings-before-v4.json"
    SETTINGS="$RUN/settings-before-v4.json"
    echo "The old screensaver's settings are saved in $SETTINGS."
  else
    echo "!! The old screensaver cannot export its settings (it is older than 3.170, or it did not answer). Nothing was changed. Give a settings file:"
    echo "   optimize/migrate-to-v4.sh settings.json      (an export of another TV, or update the old screensaver first)"
    exit 1
  fi
fi
[ -f "$SETTINGS" ] || { echo "!! No such file: $SETTINGS"; exit 1; }
"$PY" -I -c 'import json,sys; d=json.load(open(sys.argv[1], encoding="utf-8")); assert isinstance(d, dict) and d' "$SETTINGS" 2> /dev/null \
  || { echo "!! $SETTINGS is not a settings file (a JSON object): nothing was changed."; exit 1; }

# what the TV used before, to put it back if the new one never answers
prev_comps=$(tv "settings get secure screensaver_components" | tr -d '\r' | head -1)
prev_enabled=$(tv "settings get secure screensaver_enabled" | tr -d '\r' | head -1)

# 2. 4.0 over the home app (the old screensaver stays and keeps working until the very end)
bash optimize/install.sh app

# 3. the new receiver, then the settings
echo "Waiting for the 4.0 settings receiver…"
ready=0
for _ in $(seq 1 30); do
  got=$(tv "am broadcast -n $RX_NEW -a $OLD.GET" 2> /dev/null | data_of || true)
  if [ -n "$got" ] && [[ "$got" != error:* ]] && [[ "$got" != *"Error"* ]]; then ready=1; break; fi
  sleep 2
done
if [ "$ready" != 1 ]; then
  echo "!! The 4.0 app does not answer yet. The old screensaver ($OLD) is left installed and in use: nothing is lost."
  echo "   Run this command again once the TV has finished updating (the settings file is kept: $SETTINGS)."
  exit 1
fi
echo "Carrying the settings over:"
bash optimize/saver.sh import "$SETTINGS" --yes || {
  echo "!! The 4.0 app refused the settings (see above). The old screensaver ($OLD) is left installed and in use."
  exit 1
}

# 4. the screensaver of the TV: the new dream, if it was ours (or none)
case "$prev_comps" in
  ""|null|*"$OLD/"*|*"$NEW/"*)
    # a TV whose screensaver switch was off keeps it off (that was the person's choice); otherwise it is on
    en=1; [ "$prev_enabled" != 0 ] || en=0
    printf '%s\n' "settings put secure screensaver_enabled $en" \
      "settings put secure screensaver_components $DREAM" \
      "settings put secure screensaver_default_component $DREAM" | tv --batch > /dev/null
    echo "The TV's screensaver is now the one inside the 4.0 app." ;;
  *) echo "The TV uses another screensaver ($prev_comps): left as it is." ;;
esac

# 5. verify, then remove
fail=0
check() { if "$@"; then echo "   ok   $DESC"; else echo "!! NOT ok: $DESC"; fail=1; fi; }
echo "Checking:"
vn=$(tv "dumpsys package $NEW 2> /dev/null | grep -m1 versionName" | tr -d '\r ' | sed 's/.*=//')
DESC="the app $NEW is installed (version ${vn:-?})"; check installed "$NEW"
DESC="the 4.0 settings receiver answers"
check bash -c '[ -n "$1" ] && [[ "$1" != error:* ]]' _ "$(tv "am broadcast -n $RX_NEW -a $OLD.GET" | data_of || true)"
DESC="the dream ScreenDream is registered in $NEW"
check bash -c 'grep -q "$1" <<< "$2"' _ "ScreenDream" "$(tv "(cmd package query-services --brief -a android.service.dreams.DreamService || pm query-services --brief -a android.service.dreams.DreamService) 2> /dev/null" | tr -d '\r' | grep "$NEW" || true)"
comps=$(tv "settings get secure screensaver_components" | tr -d '\r' | head -1)
case "$comps" in
  *"$OLD/"*) DESC="the TV's screensaver does not point at the old app (it says: $comps)"; check false ;;
  *) echo "   ok   the TV's screensaver is: ${comps:-none}" ;;
esac
home=$(tv "cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN" | tr -d '\r')
if grep -q "$NEW" <<< "$home"; then echo "   ok   Home is ChouiTV"
elif grep -q "$OLD" <<< "$home"; then echo "!! NOT ok: Home points at the old screensaver app"; fail=1
else echo "   ok   Home is not ChouiTV on this TV (the person kept the stock one): left as it is"; fi

if [ "$fail" != 0 ]; then
  echo "!! A check failed: the old screensaver ($OLD) is NOT removed, so the TV still has what it had. Fix the line above, then run this command again."
  # a screensaver pointing at a dream that is not there is worse than the old one: put back what the TV used before step 4
  if [[ "$prev_comps" =~ ^[A-Za-z0-9_.]+/[A-Za-z0-9_.\$]+(,[A-Za-z0-9_.]+/[A-Za-z0-9_.\$]+)*$ ]]; then
    tv "settings put secure screensaver_components '$prev_comps'" > /dev/null || true
  fi
  exit 1
fi

if [ "$yes" != 1 ]; then
  echo
  echo "Everything works from the 4.0 app. The old screensaver app ($OLD) can go now."
  echo "Its photos «صوري» and its tasks are in its own storage, and they go with it (the settings are already carried over)."
  read -r -p "Remove $OLD? [y/N] " ans || ans=n
  case "$ans" in y|Y|yes|YES) ;; *) echo "Left installed. Run this command again with --yes (or: tv pm uninstall $OLD) when you are ready."; exit 0 ;; esac
fi
tv "pm uninstall $OLD" | tr -d '\r' | tail -1
if installed "$OLD"; then echo "!! $OLD is still installed."; exit 1; fi
# the new screensaver must still be the one in use after the removal
comps=$(tv "settings get secure screensaver_components" | tr -d '\r' | head -1)
case "$comps" in
  ""|null) tv "settings put secure screensaver_components $DREAM" > /dev/null; echo "Screensaver set to the 4.0 dream again." ;;
esac
echo "Done: the TV runs ChouiTV 4.0 (ma.chouitv.home, one app). Press Home to check, then optimize/saver.sh show."

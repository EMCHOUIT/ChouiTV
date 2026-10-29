"""optimize/migrate-to-v4.sh: the ADB route from a 3.x TV (two apps) to 4.0 (one app), against a stub tv.py. No TV needed.

    python3 tests/migrate_v4_test.py

Each case copies the scripts into a scratch tree with a fake `python` (PY) that logs every command it is given and answers
like the TV would, and a fake `sleep`. It checks what was sent, in which order, and that the old screensaver app is only
removed when everything worked (the TV is never left without a screensaver or a home screen).
"""
import os, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OLD, NEW = "ma.chouitv.screensaver", "ma.chouitv.home"
DREAM = "ma.chouitv.home/ma.chouitv.screensaver.ScreenDream"

STUB_PY = r'''#!/usr/bin/env bash
# fake: python -I optimize/tv.py <args>; the TV's state lives in files under $T
[ "$2" = -c ] && exec python3 -c "$3" "${@:4}"   # python -I -c CODE ARGS: a real python
shift 2
log() { echo "$*" >> "$T/log"; }
if [ "$1" = --bg ]; then
  shift 2; set -- "$*"
  case "$1" in "pm install"*) log "INSTALL"; grep -qx "$NEWPKG" "$T/installed" || echo "$NEWPKG" >> "$T/installed"; echo Success; exit 0 ;; esac
fi
case "$1" in
  --push-b64) exit 0 ;;
  --batch)
    while IFS= read -r l; do
      log "$l"
      case "$l" in "settings put secure screensaver_components "*) echo "${l##* }" > "$T/comp" ;; esac
    done
    exit 0 ;;
esac
cmd="$1"
case "$cmd" in
  *"$OLDPKG/.SaverReceiver"*EXPORT*)
    log "EXPORT-OLD"
    [ "$EXPORT" = ok ] && echo "Broadcast completed: result=0, data=\"{\"city\": \"Rabat\", \"idle\": \"10\"}\"" || echo "Broadcast completed: result=0, data=\"error: unknown action\"" ;;
  *"$NEWPKG/$OLDPKG.SaverReceiver"*GET*)
    if [ "$RX" = 1 ] && grep -qx "$NEWPKG" "$T/installed"; then echo "Broadcast completed: result=0, data=\"version: 4.0"; echo "city: Rabat"; echo "migration: settings 4, tasks 3/3, photos 2/2\""; else log "GET-NOANSWER"; echo "Error: Broadcast not delivered"; fi ;;
  *"$NEWPKG/$OLDPKG.SaverReceiver"*IMPORT*)
    log "IMPORT ${cmd##*SaverReceiver }"
    [ "$IMPORT" = ok ] && echo "Broadcast completed: result=0, data=\"applied: 2 changes\"" || echo "Broadcast completed: result=0, data=\"error: not a ChouiTV settings file\"" ;;
  "settings get secure screensaver_components") cat "$T/comp" ;;
  "settings get secure screensaver_enabled") echo "$ENABLED" ;;
  "pm list packages "*) p="${cmd#pm list packages }"; grep -qx "$p" "$T/installed" && echo "package:$p" ;;
  "dumpsys package $NEWPKG"*) echo "    versionName=4.0" ;;
  *query-services*) [ "$DREAM_REG" = 1 ] && echo "$NEWPKG/$OLDPKG.ScreenDream" ;;
  *resolve-activity*) echo "$NEWPKG/.MainActivity" ;;
  "pm uninstall "*) p="${cmd#pm uninstall }"; log "UNINSTALL $p"; grep -vx "$p" "$T/installed" > "$T/i2"; mv "$T/i2" "$T/installed"; echo Success ;;
  "settings put "*) log "$cmd"; case "$cmd" in "settings put secure screensaver_components "*) echo "${cmd##* }" | tr -d "'" > "$T/comp" ;; esac ;;
  *) log "other: $cmd" ;;
esac
exit 0
'''


def run(*args, old=True, export="ok", rx=1, imp="ok", comp=OLD + "/.ScreenDream", enabled="1", dream_reg=1, answer="y", home_installed=True):
    t = Path(tempfile.mkdtemp())
    tree, bin_, apks = t / "tree", t / "bin", t / "apks"
    (tree / "optimize").mkdir(parents=True); bin_.mkdir(); apks.mkdir()
    for f in ("migrate-to-v4.sh", "install.sh", "saver.sh"):
        shutil.copy(ROOT / "optimize" / f, tree / "optimize")
    (apks / "chouitv-home.apk").write_bytes(b"x")
    (bin_ / "py").write_text(STUB_PY); (bin_ / "py").chmod(0o755)
    (bin_ / "sleep").write_text("#!/bin/sh\nexit 0\n"); (bin_ / "sleep").chmod(0o755)
    pk = ([OLD] if old else []) + ([NEW] if home_installed else [])
    (t / "installed").write_text("".join(p + "\n" for p in pk))
    (t / "comp").write_text(comp + "\n")
    env = dict(os.environ, PATH=f"{bin_}:{os.environ['PATH']}", PY=str(bin_ / "py"), T=str(t), APK_DIR=str(apks), URTV_RUN_DIR=str(t / "run"),
               OLDPKG=OLD, NEWPKG=NEW, EXPORT=export, RX=str(rx), IMPORT=imp, ENABLED=enabled, DREAM_REG=str(dream_reg))
    r = subprocess.run(["bash", str(tree / "optimize" / "migrate-to-v4.sh"), *args], env=env, capture_output=True, text=True,
                       input=answer + "\n", cwd=tree)
    log = (t / "log").read_text().splitlines() if (t / "log").exists() else []
    installed = (t / "installed").read_text().split()
    final_comp = (t / "comp").read_text().strip()
    saved = (t / "run" / "settings-before-v4.json").exists()
    shutil.rmtree(t)
    return r.returncode, r.stdout + r.stderr, log, installed, final_comp, saved


failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {detail}"))
    failed += not ok


def idx(log, prefix):
    return next((i for i, l in enumerate(log) if l.startswith(prefix)), -1)


# the whole route, --yes
rc, out, log, inst, comp, saved = run("--yes")
i_exp, i_ins, i_imp, i_set, i_un = idx(log, "EXPORT-OLD"), idx(log, "INSTALL"), idx(log, "IMPORT"), idx(log, "settings put secure screensaver_components"), idx(log, "UNINSTALL")
check("route: export, install, import, screensaver, uninstall - in that order", rc == 0 and 0 <= i_exp < i_ins < i_imp < i_set < i_un, f"{rc} {log} {out}")
check("route: the import carries the file and applies it", "--es json_b64 " in log[i_imp] and "--es apply yes" in log[i_imp] and "-a ma.chouitv.screensaver.IMPORT" in log[i_imp], log[i_imp])
check("route: the old settings are saved to a local file first", saved)
check("route: the screensaver and its default point at the new dream",
      f"settings put secure screensaver_components {DREAM}" in log and f"settings put secure screensaver_default_component {DREAM}" in log and comp == DREAM, f"{log} {comp}")
check("route: only the old screensaver app is removed, the home app stays", log[i_un] == "UNINSTALL " + OLD and inst == [NEW] and sum(l.startswith("UNINSTALL") for l in log) == 1, f"{log} {inst}")
check("route: the 4.0 install does not touch Home or the screensaver by itself (no set-home-activity, no stock disabling)",
      not any("set-home-activity" in l or "disable-user" in l for l in log), str(log))
check("route: says it is done", "Done: the TV runs ChouiTV 4.0" in out, out)

# without --yes: asked first
rc, out, log, inst, comp, saved = run(answer="n")
check("no --yes, answer n: everything is carried over but the old app stays", rc == 0 and OLD in inst and idx(log, "UNINSTALL") == -1 and "Left installed" in out, f"{rc} {inst} {out}")
rc, out, log, inst, comp, saved = run(answer="y")
check("no --yes, answer y: the old app goes", rc == 0 and inst == [NEW], f"{rc} {inst} {out}")

# a settings file instead of the export
tmp = Path(tempfile.mkdtemp()); f = tmp / "s.json"; f.write_text('{"city": "Salé"}')
rc, out, log, inst, comp, saved = run(str(f), "--yes", export="none")
check("a settings file: the old export is not needed", rc == 0 and idx(log, "EXPORT-OLD") == -1 and idx(log, "IMPORT") > 0 and inst == [NEW], f"{rc} {log} {out}")
bad = tmp / "bad.json"; bad.write_text("not json")
rc, out, log, inst, comp, saved = run(str(bad), "--yes")
check("a file that is not JSON: refused before anything changes", rc != 0 and not log and OLD in inst, f"{rc} {log} {out}")
shutil.rmtree(tmp)

# nothing to move
rc, out, log, inst, comp, saved = run("--yes", old=False)
check("no old screensaver app: nothing to do, nothing sent", rc == 0 and not log and "nothing to move" in out, f"{rc} {log} {out}")

# failures leave the TV as it was
rc, out, log, inst, comp, saved = run("--yes", export="none")
check("the old app cannot export: stops before installing anything", rc != 0 and idx(log, "INSTALL") == -1 and OLD in inst and "!!" in out, f"{rc} {log} {out}")
rc, out, log, inst, comp, saved = run("--yes", rx=0)
check("the new receiver never answers: no import, old app kept, screensaver untouched",
      rc != 0 and idx(log, "IMPORT") == -1 and OLD in inst and comp == OLD + "/.ScreenDream" and idx(log, "UNINSTALL") == -1, f"{rc} {log} {comp} {out}")
rc, out, log, inst, comp, saved = run("--yes", imp="err")
check("the import is refused: old app kept, screensaver untouched", rc != 0 and OLD in inst and comp == OLD + "/.ScreenDream" and idx(log, "UNINSTALL") == -1, f"{rc} {inst} {comp} {out}")
rc, out, log, inst, comp, saved = run("--yes", dream_reg=0)
check("the dream is not registered: the old app is kept and the TV's screensaver goes back to what it was",
      rc != 0 and OLD in inst and comp == OLD + "/.ScreenDream" and idx(log, "UNINSTALL") == -1, f"{rc} {inst} {comp} {out}")

# the screensaver of the TV
rc, out, log, inst, comp, saved = run("--yes", comp="com.google.android.backdrop/.Dream")
check("the TV uses another screensaver: left as it is, the old app still goes", rc == 0 and comp == "com.google.android.backdrop/.Dream" and "left as it is" in out and inst == [NEW], f"{rc} {comp} {out}")
rc, out, log, inst, comp, saved = run("--yes", comp="null", enabled="0")
check("no screensaver set and the switch off: the new dream is set, the switch stays off", comp == DREAM and "settings put secure screensaver_enabled 0" in log, f"{comp} {log}")

sys.exit(1 if failed else 0)

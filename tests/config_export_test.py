#!/usr/bin/env python3
"""3.185 bridge: ConfigProvider answers a read-only /export (one row, column "json") with the very JSON that SaverReceiver EXPORT gives,
by the same call (SettingsBackup.export), and keeps /tasks and /own/N. The source says what it must, as a plain JVM cannot run a provider.
Run: python3 tests/config_export_test.py"""
import re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "android/app/src/main/java/ma/chouitv/screensaver"
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + detail))
    failed += not ok

text = lambda p: p.read_text(encoding="utf-8")
cp = text(APP / "ConfigProvider.java")
saver = text(APP / "SaverReceiver.java")
manifest = text(ROOT / "android/app/src/main/AndroidManifest.xml")

check("ConfigProvider: /export is recognised as the single path segment export", 'private static boolean isExport' in cp and '"export".equals(uri.getPathSegments().get(0))' in cp)
body = cp[cp.index("if (isExport(uri))"):cp.index("if (isTasks(uri))")]
check("/export: one row, one column named json", 'new String[] {"json"}' in body and body.count("addRow") == 1)
check("/export: the text is SettingsBackup.export(...).toString(2), as EXPORT gives", "SettingsBackup.export(getContext()).toString(2)" in body)
check("SaverReceiver EXPORT still uses SettingsBackup.export(c).toString(2) (same code, not forked)", "SettingsBackup.export(c).toString(2)" in saver)
check("/export is answered before the default fragment row", cp.index("isExport(uri)") < cp.index("home_panels"))
check("/export is read-only: insert/update/delete still only handle tasks and prayeradj", "isExport" not in cp[cp.index("public Uri insert"):])
check("/tasks and /own/N are kept", 'isTasks(uri)' in cp and 'seg.size() == 2 && "own".equals(seg.get(0))' in cp)
check("the provider is still behind the signature READ_CONFIG permission", re.search(r'android:readPermission="ma\.chouitv\.screensaver\.READ_CONFIG"', manifest) is not None)
sys.exit(1 if failed else 0)

# TODO

Updated at the end of the session of 2026-11-02. Branch: `v4`. Published: ChouiTV 4.1 (v4.1), ChouiTV Installer 1.6.2 (v1.6.2).

## Done

- [x] ChouiTV 4.1 published: listening screen with two tabs (القرآن الكريم / الإذاعات), favourite reciters, named lists of surahs (rename), adhan moved to Settings (tab الصلاة), QR of the Installer with its explanation popup, Hijri date adjustment (-2..+2), no home refresh after the Quran.
- [x] QR popup fixed on TV1 (opaque background, one-line Play Protect note «إن ظهرت رسالة أمان من غوغل فاختر «التثبيت بدون فحص»: هذا طبيعي»).
- [x] Listening: ▼ from the tab bar lands on the chips row (`MainActivity.dispatchKeyEvent`), focused chip is solid gold with a cream ring (`QuranPane.paintChips`), «متابعة الاستماع» card removed.
- [x] README «what is new in 4.1» (ar/fr/en) and a Play Protect note in the three READMEs of ChouiTV and of the Installer.
- [x] Installer 1.6.1 published (back button, bottom action, skip up-to-date apps) then 1.6.2 (cleanup plan keeps `mitv.service` and `com.xiaomi.mitv.tvmanager`).
- [x] `optimize/lists/keep.txt` protects `mitv.service` only (the Mi TV Manager is not needed for the picture menu: TV1 has it off and the menu opens); `optimize/lists/cleanup.json` regenerated.
- [x] Download counts read from GitHub (assets `download_count`): approximate, not a count of TVs.

## In progress

Nothing half-done in the code. One commit is local only:

- `f061a3f` (keep.txt + cleanup.json) is **not pushed** to `main`; the Installer 1.6.2 already ships the same plan. Push it with the next ChouiTV release (4.2).

Open, not verified:

- TV2 (192.168.1.51): picture settings work again after re-enabling `mitv.service` and `com.xiaomi.mitv.tvmanager`. Netflix (`com.netflix.ninja`) and Prime Video (`com.amazon.amazonvideo.livingroom`) were disabled again and the TV rebooted; their state after the reboot is **unchecked** because ADB could not reconnect (port 5555 closed; the user re-enabled debugging afterwards, address/port not yet re-tested).
- TV1 (192.168.1.50:5555) still runs a debug build (versionCode 219), not the published 4.1 (220).
- The QR was never scanned with a real phone.

Blocking errors: none (builds and tests green; `tests/check.py` needs the `fontTools` module, missing on this machine, unrelated; pre-existing failures: city_match, greeting, pill_text, team_ids).

## Next session: first step

1. Reconnect to TV2 (`adb connect 192.168.1.51:5555`, or the new address/port shown in its developer options) and run `pm list packages -d | grep -i "netflix\|amazonvideo\|mitv.service\|tvmanager"` then check whether Netflix / Prime came back after the reboot.
2. Then: put TV1 on the published 4.1 (`adb install -r -d` of the release APK, or let it self-update) and check the update path from 4.0.

## Next

- [ ] 4.2: short summary of the news shown before an update (idea: a `notes` field in the signed `latest.json`, 3 bullets, shown in `UpdateActivity` before OK). Mockup first, code only after «Oui, code». 4.0 TVs do not know the field: it appears from 4.1 to 4.2.
- [ ] Push f061a3f with 4.2.
- [ ] Optional: publish the Installer on the Play Store (removes the Play Protect warning; account 25 USD, identity check, closed test for a new personal account).
- [ ] Check the first family TVs moving to 4.0/4.1; migration test on TV2 still to do.
- [ ] Installer README `settings-*.png` cropped text; change the Installer theme at the end of the project; remove the Installer TV report in 1.1.
- [ ] Clean up agent worktrees `/home/elmehdi/wt-*` and TV1 test tasks `old_task_*`.
- [ ] Optional distinct label for the new screensaver entry «(الجديدة)».

Rule kept: nothing is pushed or published without an explicit «Oui, publie».

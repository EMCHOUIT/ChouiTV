# TODO

Updated at the end of the sessions of 2026-11-02 (two closures). Branch: `v4`. Published: ChouiTV 4.1 (v4.1), ChouiTV Installer 1.6.2 (v1.6.2).

## Done

- [x] ChouiTV 4.1 published: listening screen with two tabs (القرآن الكريم / الإذاعات), favourite reciters, named lists of surahs (rename), adhan moved to Settings (tab الصلاة), QR of the Installer with its explanation popup, Hijri date adjustment (-2..+2), no home refresh after the Quran.
- [x] QR popup fixed on TV1 (opaque background, one-line Play Protect note «إن ظهرت رسالة أمان من غوغل فاختر «التثبيت بدون فحص»: هذا طبيعي»).
- [x] Listening: ▼ from the tab bar lands on the chips row (`MainActivity.dispatchKeyEvent`), focused chip is solid gold with a cream ring (`QuranPane.paintChips`), «متابعة الاستماع» card removed.
- [x] README «what is new in 4.1» (ar/fr/en) and a Play Protect note in the three READMEs of ChouiTV and of the Installer.
- [x] Installer 1.6.1 published (back button, bottom action, skip up-to-date apps) then 1.6.2 (cleanup plan keeps `mitv.service` and `com.xiaomi.mitv.tvmanager`).
- [x] `optimize/lists/keep.txt` protects `mitv.service` only (the Mi TV Manager is not needed for the picture menu: TV1 has it off and the menu opens); `optimize/lists/cleanup.json` regenerated.
- [x] Download counts read from GitHub (assets `download_count`): approximate, not a count of TVs.

- [x] Netflix/Prime on Xiaomi MiTV-MSSP3: tested disable-user, uninstall --user 0 and appops: the ROM resets all three at every boot (both TVs); `am kill` keeps them stopped. TV1 and TV2 left with both disabled and stopped (resets at next reboot).
- [x] Audit of Tellyscope, CleanYourTV and our cleanup engine with three agents; reports in /home/elmehdi/audit/ (report-*.md) and plan in /home/elmehdi/audit/PLAN-installer-1.7.md (lots A-J, releases 1.7 safety, 1.8 remote + rename, 1.9 speed/models, 2.0 pairing).
- [x] Decision: Installer renamed «ChouiTV Companion» in 1.8 (memory note installer-rename-companion).
- [x] Token savings: ~/.claude/settings.json now has model sonnet, effort medium, subagent model sonnet, cost warnings off (backup settings.json.bak-tokens); effective in new sessions.

## In progress

Nothing half-done in the code. One commit is local only:

- `f061a3f`, `0cfcccb` (keep.txt: only mitv.service; cleanup.json) and the docs commits are **not pushed** to `main`; the Installer 1.6.2 already ships the same plan. Push it with the next ChouiTV release (4.2).

Open, not verified:

- TV2 (192.168.1.51): picture settings work again after re-enabling `mitv.service` and `com.xiaomi.mitv.tvmanager`. Netflix (`com.netflix.ninja`) and Prime Video (`com.amazon.amazonvideo.livingroom`) were disabled again and the TV rebooted; their state after the reboot is **unchecked** because ADB could not reconnect (port 5555 closed; the user re-enabled debugging afterwards, address/port not yet re-tested).
- TV1 (192.168.1.50:5555) still runs a debug build (versionCode 219), not the published 4.1 (220).
- The QR was never scanned with a real phone.

Blocking errors: none (builds and tests green; `tests/check.py` needs the `fontTools` module, missing on this machine, unrelated; pre-existing failures: city_match, greeting, pill_text, team_ids).

## Next session: first step

1. Start Installer 1.7 (safety): first the mockup of the restore history and of the «the TV turns it back on» flag (lots D and E of PLAN-installer-1.7.md), tests first on FakeTv, then lots A, B, C. Nothing is coded yet.
2. Also: put TV1 on the published 4.1 (`adb install -r -d` of the release APK, or let it self-update) and check the update path from 4.0.

## Next

- [ ] Installer 1.7 then 1.8 (Companion: remote with Arabic text, to be spiked first with clipboard + KEYCODE_PASTE on TV1/TV2), see the plan file.
- [ ] ChouiTV 4.2: option to stop Netflix/Prime after boot (KILL_BACKGROUND_PROCESSES) and the update summary shown before updating.
- [ ] Check the Companion name on the Play Store.

- [ ] 4.2: short summary of the news shown before an update (idea: a `notes` field in the signed `latest.json`, 3 bullets, shown in `UpdateActivity` before OK). Mockup first, code only after «Oui, code». 4.0 TVs do not know the field: it appears from 4.1 to 4.2.
- [ ] Push f061a3f with 4.2.
- [ ] Optional: publish the Installer on the Play Store (removes the Play Protect warning; account 25 USD, identity check, closed test for a new personal account).
- [ ] Check the first family TVs moving to 4.0/4.1; migration test on TV2 still to do.
- [ ] Installer README `settings-*.png` cropped text; change the Installer theme at the end of the project; remove the Installer TV report in 1.1.
- [ ] Clean up agent worktrees `/home/elmehdi/wt-*` and TV1 test tasks `old_task_*`.
- [ ] Optional distinct label for the new screensaver entry «(الجديدة)».

Rule kept: nothing is pushed or published without an explicit «Oui, publie».

## Companion 1.8 (branch v18 in /home/elmehdi/wt-installer161, built, tested on the phone with TV1, NOT pushed, NOT published)
- Done: Remote space (pad, keys, "Ouvrir sur la tele": Accueil, Ecran de veille, Listening, Channels), text sending through the TV clipboard + Paste key (helper/Clip.java -> assets/helper/clip.jar, pushed with Adb.pushShell because the TV refuses sync:), "not installed" -> offer to install, "cannot open" -> toast + home, new name ChouiTV Companion (label only). 140 JVM tests. APK in /home/elmehdi/relinst18.
- Tested on the phone (USB, Windows adb at C:\platform-tools\adb.exe, serial 5cf83c33) with TV1: Channels opens, Screensaver starts (DreamActivity), Listening opens with the dev ChouiTV (tilawah.MainActivity exported, commit c2da9e4 on v4 = needed for ChouiTV 4.2), text typed on the phone appears in a real TV text field (settings > city search).
- Not tested: Arabic typed from the phone (same code path, proven with the helper alone), the pad keys on a real navigation, Channels missing -> install offer, README/images, QR popup text that still says "ChouiTV Installer".
- TV1 runs a DEBUG ChouiTV "4.2-dev" (code 221) installed with -r -d: reinstall the published APK to go back.
- PowerShell script files with accents are read as ANSI: use ASCII substrings or a BOM.
- 2026-11-03 remote redesign (v18 commit 0081ff5, user validated «Ça convient»): style B in 3D (remote body, sockets, layered keys, four-wedge ring with gold OK dish, round keys with printed words, volume bar), 6 shortcuts (Coran et radio, Chaînes, Applications, Écran de veille, Écrire sur la télé, Réglages de la télé), power with confirm, text pop-up (Envoyer / Effacer), Applications pop-up with 6 custom slots (real names and icons from helper AppInfo, kept on the phone per TV IP), last TV card on the hub + silent reconnect (5 tries) + optional open-at-launch. Mockups: scratchpad remote-v2..v5 (session files, not in the repo).
- Still to do before publishing 1.8: TalkBack for the ring (one block today), RTL pass on the phone, power key for real, Arabic text typed through the pop-up, the "Reconnexion" state seen on screen, README/images, then «Oui, publie».

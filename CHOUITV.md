# ChouiTV

تلفزيون عائلي في المغرب: واجهة عربية وشاشة توقف لتلفزيونات Android TV، تم تطويرها من طرف **EMChouit**.

An Arabic home screen and screensaver for Android TV, made for a Moroccan family TV by **EMChouit**. Where parts of it come from, and under which licences, is in `THIRD_PARTY_NOTICES.md`.

## What was added

- **Moroccan touch (top left of the screensaver):** weather of the family's cities, Amazigh and fellahi calendar, countdown to Moroccan occasions, the Atlas Lions (next match and last result), and the original markets. Settings: «لمسة مغربية».
- **Cards, no pictures** (settings tab «المحتوى»): news (Hespress, Hesport, Sky News Arabia RSS), «حكمة من التاريخ» (sayings of historical figures from Arabic Wikiquote; prepared entries shown only offline). Each has an on/off switch, refresh, and a place (above/below the verse card, bands above/below the middle row).
- **Guided tasbih** (bottom band): 12 formulas with their merit and source, a string of beads that light up one by one and a counter; settings in the tab «الصلاة».
- **«الاستماع»** (Quran reciters, Moroccan and Quran radio stations in an alphabetical card grid, adhan), a sleep timer with a visible selection, and a transparent oscilloscope.
- **Settings in five tabs** (الأساسيات · الشاشة · المحتوى · الصلاة · الرئيسية والنظام) with a fixed «▶ معاينة» button, left/right between tabs, and windows for every choice.
- Moroccan cities by the 12 regions, Moroccan teams (national team and Botola clubs), darija greetings, proverbs and family words in the wisdom card, verse/dua card from public APIs every 2 minutes.
- **Settings export/import** (settings tab «الرئيسية والنظام» ← «نسخ الإعدادات», and `optimize/saver.sh export` / `import`): everything the person set in one JSON file, with a preview and an undo.
- **Time zone and prayer calculation** settings (the calculation follows the zone: Morocco uses the Awqaf ministry's), a prayer's own options in «الاستماع», any key stops a ringing adhan.
- **Own names (3.171):** the apps are `ma.chouitv.screensaver` and `ma.chouitv.home` (earlier builds used other names). They are other apps for Android: `optimize/migrate-to-chouitv.sh` moves a TV from the old ones (settings carried over, old apps removed).
- **Self-update:** both apps check the releases of `EMCHOUIT/ChouiTV` and ask for one OK press per APK.

## Build

JDK 17, Android SDK (build-tools, platform 34), Gradle 8.9, Python 3.12.

```bash
python src/build.py            # builds dist/tv.html from src/template.html (commit first: the page's build stamp is the last commit time of src/)
GITHUB_RUN_NUMBER=165 gradle -p android :app:assembleRelease :launcher:assembleRelease
```

- Both APKs (`ma.chouitv.screensaver`, `ma.chouitv.home`) **must be signed with the same key** (a read permission between them is signature-level). The key is not in this repository.
- **Fonts:** the interface uses the **Thmanyah** font (`assets/fonts/Thmanyah-*.woff2` for the page, `android/launcher/src/main/assets/fonts/Thmanyah-*.otf` for the apps); the Quran text uses the unmodified KFGQPC Uthmanic Hafs (see `assets/fonts/UthmanicHafs-NOTICE.txt`).
- `GITHUB_RUN_NUMBER` becomes the version code: it must be higher than the installed one.
- The APKs are published as releases of `EMCHOUIT/ChouiTV`.

## Credits and licence

Code and additions by EMChouit under the MIT licence (`LICENSE`); other projects, data sources and licences: `THIRD_PARTY_NOTICES.md`. Quranic audio from mp3quran.net and qurango.net, verses from alquran.cloud, duas from ummahapi.com, sayings and articles from Wikimedia projects, news RSS from their publishers. Check each source's terms before reusing.

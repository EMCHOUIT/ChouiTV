# ChouiTV (famille, Android TV marocaine)

Dépôt : `/home/elmehdi/urtv-patch` (WSL Ubuntu), branche de travail `v4`, `main` = ce qui est publié.
Ouvrir la session **dans ce dossier** (Windows : `\\wsl.localhost\Ubuntu\home\elmehdi\urtv-patch`), sinon TODO/CLAUDE/git ne sont pas vus.

## Deux copies du dépôt
- **Référence (build, tests, adb, signature) :**  dans WSL. Tout ce qui compile ou touche une TV se fait ici.
- **Copie de lecture/édition :**  (clone,  = le dépôt WSL, ). Pas de clés ni d'APK dedans.
- Ne pas éditer les deux en parallèle. Synchroniser : depuis la copie G: , ou pousser une branche vers  puis la fusionner côté WSL. Si On branch v4
nothing to commit, working tree clean y montre des fichiers « modifiés » sans , faire  (cache du disque synchronisé).

## Au démarrage d'une session (rapide, sans tout relire)
1. Lire `TODO.md` (état et première étape) puis le dernier paragraphe de `PROGRESS.md`.
2. `git status -sb` et `git log --oneline -5`.
3. Vérif rapide : `python3 tests/keep_test.py && python3 tests/community_test.py` (secondes). Build complet seulement si le code Java change.
4. Ne rien relire d'autre tant que la tâche ne l'exige pas.

## Projets liés
- ChouiTV (ce dépôt) : app unique `ma.chouitv.home` (module `android/launcher`), scripts `optimize/`, listes `optimize/lists/`.
- Installer téléphone (devient « ChouiTV Companion » en 1.8) : `/home/elmehdi/chouitv-installer`, copies de travail `/home/elmehdi/wt-installer161` (branche `v162`).
- Audits et plan Installer 1.7 à 2.0 : `/home/elmehdi/audit/` (`PLAN-installer-1.7.md`).

## Build et tests (WSL, hors ligne)
```bash
cd /home/elmehdi/urtv-patch/android && export ANDROID_HOME=/home/elmehdi/android
G=$(ls -d ~/.gradle/wrapper/dists/gradle-8.9-bin/*/gradle-8.9/bin/gradle)
GITHUB_RUN_NUMBER=<n> $G -p . :launcher:assembleDebug --offline -q
```
- Release : `ANDROID_KEYSTORE=~/chouitv-signing/chouitv.p12`, `ANDROID_KEYSTORE_PASSWORD` (fichier `password.txt`), `CHOUITV_VERSION`, puis `optimize/check-signer.sh`, `optimize/sign-manifest.sh`. Code de version = `GITHUB_RUN_NUMBER` (4.1 = 220, toujours croissant).
- Tests : `tests/*.py` ; échecs connus avant ce travail : city_match, greeting, pill_text, team_ids ; `tests/check.py` exige `fontTools`.
- TV de test : TV1 `192.168.1.50:5555`, TV2 `192.168.1.51:5555` (même modèle Xiaomi MiTV-MSSP3, Android 11). Les IP changent ; adb : `/home/elmehdi/android/platform-tools/adb`.

## Règles de travail (décidées par l'utilisateur)
- Cycle : maquette → « Oui, code » → code + tests d'abord → essai sur TV → **demander avant de pousser ou publier** (publier seulement sur « Oui, publie »).
- Une seule clé de signature APK, une clé de manifeste ; ne jamais les perdre ni les changer.
- Pas de ligne d'attribution dans les messages de commit ni les PR. Auteur git : EMChouit.
- Identité visuelle ChouiTV : reprendre les fonctions des autres projets, jamais leur look.
- Fuseau des TV : Maroc à GMT (UTC+0), ville `Africa/Abidjan` : c'est voulu.
- Garder `ChouiTV-Installer.apk` comme alias stable du lien (le QR de ChouiTV 4.1 pointe dessus).
- Économie de tokens : pas d'agents sans demande, lectures ciblées (offset/limit, grep), captures réduites, `/compact` entre deux lots.

## Pièges connus
- `pm disable-user`, `uninstall --user 0` et `appops` sont remis à zéro à chaque démarrage pour Netflix et Prime Video sur MiTV-MSSP3 ; seul `am kill` après démarrage tient.
- Ne jamais désactiver `mitv.service` (protégé dans `optimize/lists/keep.txt`).
- Un test `adb install` d'un APK de debug de code supérieur impose `-r -d` pour revenir à la version publiée.

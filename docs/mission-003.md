# Mission 003 — Stabilisation Android et GitHub Actions

Date : 15 octobre 2026. Dépôt : `bubth720/JARVIS-INTERFACE`.
Branche de travail : `fix/interface-ci-003`.
Base : `development/mission-002`, commit
`d1c0931c4507e9af8f1aa60951947c23f4394b56`.
Commit du correctif CI : `cc9e873167bf7b232af66128436e8301b315d273`.

## Diagnostic

Le [run initial 38582536703](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/38582536703)
a échoué. Sa page de job confirme `setup-android@v3: failure`, puis l’installation
SDK explicite, les tâches Gradle et l’upload d’APK `skipped`.

Le constat fourni par l’utilisateur situe l’erreur dans l’installation de `tools`.
L’inspection du fichier `action.yml` officiel confirme le défaut
`packages: tools platform-tools`. Le code de l’action découpe cette valeur sur les
espaces et installe chaque paquet. Le paquet historique `tools` n’est pas nécessaire
au projet. L’installation des command-line tools est gérée séparément par l’action.

Les journaux bruts n’ont pas pu être téléchargés : l’API `api.github.com` est refusée
par le proxy cloud et les pages publiques demandent une connexion pour afficher les
logs détaillés. Les statuts de chaque étape et les résumés publics restent accessibles.
La cause précise indiquée par l’utilisateur est cohérente avec les paramètres officiels.

Sources inspectées : [paramètres](https://github.com/android-actions/setup-android/blob/9fc6c4e9069bf8d3d10b2204b1fb8f6ef7065407/action.yml)
et [implémentation](https://github.com/android-actions/setup-android/blob/9fc6c4e9069bf8d3d10b2204b1fb8f6ef7065407/src/main.ts).

## Changements

Seuls le workflow et la documentation changent.

- Ubuntu 24.04 explicite, plutôt que `ubuntu-latest` dont l’image peut migrer.
- Checkout et setup-java v5 ; actions figées par SHA, dont setup-android v3 et
  upload-artifact v4. JDK Temurin 17 conservé.
- Command-line tools `11076708` explicites, correspondant à la version utilisée
  pour la compilation locale initiale.
- Remplacement complet du paramètre `packages` par
  `platform-tools platforms;android-35 build-tools;35.0.0`. Aucun paquet `tools`.
- Utilisation du script existant `scripts/check.sh`, qui conserve le statut Gradle.
- Vérification de deux APK non vides et de résultats JVM présents, avec au moins
  un test exécuté et aucun échec, erreur ou test ignoré.
- Artefacts `jarvis-interface-apks` pour les deux APK, et
  `android-validation-reports` pour XML/HTML des tests et Lint, y compris après échec.
- Délai maximal de 30 minutes et permissions GitHub limitées à `contents: read`.

Kotlin 2.1.20, Compose BOM 2025.04.01, AGP 8.9.2, Gradle 8.13, API minimum 26 et
SDK compilation/cible 35 sont conservés. Les sources, tests, ressources, manifeste
et dépendances de l’application sont identiques à la référence.
Les patches JDK, platform-tools et l’image runner peuvent évoluer ; les versions
fixées ne constituent pas une garantie d’APK identiques bit à bit.

## Résultats locaux

Commande exécutée dans le dépôt, avec JDK 17 et SDK 35 :

```sh
./scripts/check.sh --no-daemon --rerun-tasks
```

Résultat : `BUILD SUCCESSFUL`, 76 tâches exécutées, en 33 secondes.

| Validation | Résultat |
|---|---|
| `assembleDebug` | Réussi |
| `testDebugUnitTest` | 3 tests, 0 échec, 0 erreur, 0 ignoré |
| `lintDebug` | Réussi, `No issues found.` |
| `assembleDebugAndroidTest` | Réussi |
| APK application | Présent, 9 490 640 octets |
| APK instrumenté | Présent, 1 082 957 octets |
| Signature APK application | Vérifiée par `apksigner verify` |

Sorties : `app/build/outputs/apk/debug/app-debug.apk` et
`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
SHA-256 locaux, pour cette exécution uniquement :

```text
58ffb0c607d80b76ed82085477c7b80d486da11beff98e1f48f21d949e1b1135  app-debug.apk
6ab7dc0ee6530d11dd90c228b70c3fe608205429aabcb31737aba8e9a57fd644  app-debug-androidTest.apk
```

Le manifeste APK a été inspecté : aucune permission INTERNET, aucun service.
La permission interne AndroidX de protection des récepteurs reste présente.
Le diff avec la référence confirme l’absence de changement applicatif ; aucun
connecteur, client réseau ni dépendance obligatoire à CORE, VOICE ou SERVICES
n’a été introduit. Aucun autre dépôt JARVIS n’a été modifié.

## Résultats GitHub Actions

[Run du correctif 38781057579](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/38781057579),
commit `cc9e873167bf7b232af66128436e8301b315d273`.

Résultat public : **Success**. Les étapes suivantes sont toutes `success` sur
[le job 116581055781](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/38781057579/job/116581055781) :

- installation des paquets SDK supportés ;
- exécution des quatre tâches via `scripts/check.sh` ;
- contrôle des deux APK et de la suite JVM non vide, sans échec/erreur/test ignoré ;
- upload des APK et des rapports.

Les deux artefacts sont visibles dans la page du run. Le décompte local est de
3 tests ; les XML distants sont fournis dans `android-validation-reports`, sans
prétendre les avoir téléchargés dans cet environnement à accès API restreint.
Le dernier commit de cette branche documente ces résultats ; il ne modifie ni le
workflow testé ni le code applicatif. Son éventuel run supplémentaire est vérifiable
sur l’onglet Actions de la branche.

L’exécution de `connectedDebugAndroidTest`, l’installation physique et les essais
sur POCO X8 Pro/Redmi Pad SE ne font pas partie des validations effectuées ici.
Les tests instrumentés sont compilés, pas exécutés. La procédure appareils reste
dans le README. Les avertissements de dépréciation Gradle en vue de Gradle 9
ne sont pas des erreurs Lint ; la version Gradle du projet reste inchangée.

## Situation de main et proposition de branches

Inspection distante avec `git ls-remote --symref origin HEAD refs/heads/main ...` :
`main` est absente ; HEAD pointe vers `development/mission-002` au commit de référence.
La nouvelle branche est `fix/interface-ci-003`. Aucune fusion, création de `main`,
modification de branche par défaut ou suppression de branche n’a été effectuée.

Proposition à valider explicitement :

1. Après revue de cette mission, créer `main` à partir du commit validé, puis la
   définir comme branche stable avec contrôle obligatoire `Android / build`.
2. Utiliser des branches courtes `fix/...` et `feature/...`, intégrées par PR vers
   `main` après validation, sans branche d’intégration supplémentaire au départ.
3. Conserver `development/mission-002` comme référence historique tant qu’aucune
   décision de classement ou suppression n’a été prise.

Ces opérations ne sont pas appliquées par la mission 003.

## Prochaine action

Faire valider le bilan et l’organisation des branches. Toute intégration, création
de `main`, animation 2.5D ou nouveau dashboard attend une instruction ultérieure.

# Mission 006 — validation multiappareils et stabilisation ergonomique

**Statut : préparation et validation cloud ; validation physique en attente des
résultats utilisateur.** Aucun succès POCO X8 Pro / Redmi Pad SE n’est revendiqué.

## Référence et périmètre

- Dépôt unique : `bubth720/JARVIS-INTERFACE`.
- Source : `feature/interface-storage-005`, `037e2cbe2e100eb06136e9899b815a4e8351fd72`.
- Branche dédiée : `test/interface-device-006`. Le commit du présent rapport est
  le commit de livraison ; SHA et run exacts sont fournis dans le bilan final.
- Aucun merge, changement de branche par défaut ou de `main`. Les branches
  précédentes et les autres dépôts JARVIS ne sont pas modifiés.

## Appareils réellement accessibles

Au début de la mission, `adb devices -l` ne présentait aucun appareil. Aucun POCO
ou Redmi n’est connecté à Codex et aucun accès distant à ces appareils n’est configuré.
Un émulateur a été démarré pour les vérifications cloud : Android 8.0/API 26,
x86_64, AVD `jarvis004`, 720×1280 / 320 dpi, 2 CPU virtuels, 2 Gio, rendu logiciel,
sans KVM. Mode avion activé ; aucune app CORE, VOICE ou SERVICES installée.

Les fenêtres Compose 360×320, 360×360 et 360×600 dp et le texte ×2 sont des
**conditions synthétiques**, pas des modèles physiques POCO/Redmi. Les actions
accessibles sont testées via la sémantique Compose ; TalkBack réel, le toucher
physique, les polices Android récentes non linéaires et les adaptations MIUI/HyperOS
restent à vérifier sur les appareils cibles.

## Défaut démontré et correctif limité

Reproduction automatisée sur la base mission 005 :

1. Fenêtre Compose 360×360 dp, `fontScale = 2`.
2. Paramètres puis retour, retrouver le statut enregistré, activer Modifier.
3. Essayer d’atteindre la poignée Taille d’Avatar, y compris par défilement de grille.
4. Échec : **The component is not displayed**, assertion `resize-AVATAR`.

Résultat avant correction : **4 tests ergonomiques exécutés, 3 réussis, 1 échoué,
0 erreur, 0 ignoré**. [Preuve avant](validation/mission-006/ergonomics-before.xml).
L’en-tête non borné consommait l’espace disponible ; le défilement de la grille
ne pouvait pas rendre la poignée accessible.

Seul changement de production : `ui/JarvisApp.kt`. L’en-tête est borné et défilable,
avec une réserve de 168 dp (trois cellules minimales) pour la grille quand la fenêtre
le permet. Dans une fenêtre normale, son contenu conserve sa hauteur naturelle.
En fenêtre courte, boutons, messages et statut restent atteignables en faisant
**défiler l’en-tête**, séparément de la grille. Aucun réordonnancement de panneaux,
changement du reducer, du contrôleur, de DataStore, du codec, des gestes ou du format.

Après correction, les **4 mêmes tests ergonomiques réussissent** :
[preuve ciblée après](validation/mission-006/ergonomics-after.xml).
Les cibles de déplacement contrôlées mesurent au moins 48×48 dp ; les tests
vérifient les actions accessibles, minimums/verrouillage, absence de déplacement
lors du défilement de contenu et accès aux contrôles en fenêtre courte/texte ×2.
Cela ne mesure pas la précision réelle d’un doigt ni la qualité vocale de TalkBack.

## Livrables utilisables sur Android

- [Guide d’installation et de test sans ordinateur](device-validation/guide-android.md).
- [Grille pour les deux appareils](device-validation/grille-appareils.csv), résultats
  initialisés à NON TESTÉ / NON MESURÉ.
- [Modèle de retour utilisateur par appareil](device-validation/retour-utilisateur.md).
- Artefact CI **jarvis-interface-device-kit** : `app-debug.apk`, guide, CSV, modèle
  de retour, `SOURCE.txt` (branche/commit/état du checkout), `SHA256SUMS.txt`.
- Les artefacts existants **jarvis-interface-apks** (application + instrumentation)
  et **android-validation-reports** (JUnit JVM / Lint) sont conservés.

Le [workflow de la branche](https://github.com/bubth720/JARVIS-INTERFACE/actions?query=branch%3Atest%2Finterface-device-006)
permet de retrouver le run du commit livré et les téléchargements. L’utilisateur
peut télécharger/extraire le ZIP depuis Android, puis ouvrir uniquement l’APK de
l’application. GitHub peut demander une connexion et le mode bureau du navigateur.

Le kit n’est pas une release de production : signature debug et rétention GitHub.
Une mise à jour depuis un ancien APK CI peut échouer si les clés debug diffèrent.
**Ne pas désinstaller ni effacer les données pour contourner cet échec** ; le
signaler pour préparer une mise à jour compatible. Aucun changement automatique de
clé de signature, secret ou mécanisme de publication n’est ajouté. Installer avec
la même signature conserve les fichiers ; le code de stockage et son chemin sont
inchangés. Effacer les données/désinstaller les supprimerait.

## Validation exécutée et commandes

```sh
./scripts/check.sh connectedDebugAndroidTest --no-daemon
python3 scripts/package_device_kit.py
```

La compilation de préparation initiale a réussi avec des tâches majoritairement
incrémentales ; elle n’est pas comptée comme une nouvelle exécution des tests.
Les résultats finaux réellement exécutés, après correction, sont consignés ci-dessous.

## Mesures physiques et grille de couverture

| Domaine | POCO X8 Pro | Redmi Pad SE | Preuve cloud disponible |
| --- | --- | --- | --- |
| Installation/lancement/mode avion | NON TESTÉ | NON TESTÉ | Instrumentation API 26, sans partenaires JARVIS |
| Panneaux/édition/gestes/verrous/refus | NON TESTÉ | NON TESTÉ | Tests existants + ergonomie synthétique |
| Sauvegarde/recréation/reset | NON TESTÉ | NON TESTÉ | JVM DataStore réel + instrumentation |
| Arrêt forcé du processus | NON TESTÉ | NON TESTÉ | Scénario ADB, résultat détaillé ci-dessous |
| Profils/portrait/paysage | NON TESTÉ | NON TESTÉ | Fenêtres/profils simulés, pas matériel cible |
| Texte maximal/TalkBack réel | NON TESTÉ | NON TESTÉ | Texte ×2 et sémantique seulement |
| Arrière-plan/reprise | NON TESTÉ | NON TESTÉ | Nouveau scénario Lifecycle CREATED → RESUMED |
| Démarrage (temps) | NON MESURÉ | NON MESURÉ | Aucun temps physique disponible |
| Fluidité/FPS | NON MESURÉ | NON MESURÉ | Pas de benchmark représentatif en rendu logiciel |
| RAM/CPU | NON MESURÉ | NON MESURÉ | Aucune valeur physique disponible |
| Thermique | NON MESURÉ | NON MESURÉ | Non représentatif sur émulateur |
| Activité en arrière-plan | NON MESURÉ | NON MESURÉ | Reprise fonctionnelle testée, pas consommation physique |

Le guide détaille les 14 scénarios obligatoires (texte/TalkBack séparés), la mesure
manuelle approximative de démarrage et l’outillage ADB optionnel. Il demande les
conditions (version OS, texte, orientation, fréquence, batterie, chargeur, durée,
température ambiante) et les valeurs brutes. Aucune durée de build/test n’est
présentée comme un temps de démarrage de l’application.

## Fichiers et responsabilités

- `app/src/main/.../ui/JarvisApp.kt` : seul correctif de production, en-tête borné.
- `app/src/androidTest/.../ErgonomicsTest.kt` : quatre scénarios utiles, sans
  assimilation à des tests physiques ou à une vraie session TalkBack.
- `app/src/androidTest/.../StorageInteractionTest.kt` : scénario supplémentaire
  de mise en arrière-plan/reprise avec conservation des fichiers et verrous.
- `scripts/package_device_kit.py` : empaquetage sans dépendance Python externe,
  vérification des entrées, provenance avec marqueur de modifications non commitées,
  empreintes SHA-256. Aucun accès aux clés privées.
- `.github/workflows/android.yml` : ajoute préparation/upload du kit ; préserve les
  quatre tâches Android, contrôles d’APK/JVM et les deux uploads existants.
- `docs/device-validation/` : guide, grille CSV et modèle de retour.
- `docs/validation/mission-006/` : preuves réelles avant/après et résultats finaux.
- `README.md`, `docs/decisions.md`, ce rapport : utilisation, décision et limites.

Aucune dépendance Gradle, permission, service, intégration externe, animation ou
moteur d’avatar ajouté. API minimum 26, Material 3, MVVM et stockage v1 préservés.

## Risques résiduels et action suivante

- Les **deux validations physiques restent bloquées par l’absence des appareils** :
  l’utilisateur doit installer le kit et retourner les grilles remplies.
- En fenêtre courte, l’en-tête nécessite un défilement pour revoir certains boutons
  ou le statut de sauvegarde. Les fenêtres extrêmes, polices maximales réelles et
  TalkBack/gestes OEM restent à vérifier. Ne pas extrapoler l’assertion 48 dp à tous
  les réglages possibles ou à la précision réelle du toucher.
- Les APK debug CI ne garantissent pas une signature stable entre runs ; un conflit
  de signature bloque la mise à jour, pas une preuve de perte de données de DataStore.
- Une écriture non encore confirmée peut être perdue après mort du processus, comme
  documenté en mission 005. Attendre Disposition enregistrée avant les essais d’arrêt.
- Performances et thermique cibles restent non mesurées ; aucun gain de fluidité ou
  consommation n’est revendiqué. Pas de refonte préventive avant défaut démontré.

Prochaine action : attendre les résultats **POCO X8 Pro** et **Redmi Pad SE**, traiter
les seuls défauts reproductibles dans cette mission et demander validation. Aucune
mission suivante, fusion ou modification de `main` n’est engagée.

## Résultats finaux observés dans le cloud

`./scripts/check.sh connectedDebugAndroidTest --no-daemon` : **BUILD SUCCESSFUL**,
2 min 54 s, 77 tâches (16 exécutées, 61 incrémentales).

- `assembleDebug` : réussi ; APK application présent, **9 869 536 octets**.
- `testDebugUnitTest` : **59 réussis, 0 échec, 0 erreur, 0 ignoré**, réellement
  exécutés après correction (XML horodatés durant cette mission).
- `lintDebug` : **No issues found.**
- `assembleDebugAndroidTest` : réussi ; APK instrumenté présent, **1 105 775 octets**.
- `connectedDebugAndroidTest` : **20 réussis, 0 échec, 0 erreur, 0 ignoré** sur
  l’émulateur API 26 : les 15 précédents, 4 ergonomiques et 1 arrière-plan/reprise.
- Empaquetage local : six fichiers attendus, un seul APK application ; cinq
  empreintes vérifiées par `sha256sum -c SHA256SUMS.txt`, toutes OK. Avant commit,
  `SOURCE.txt` signale correctement les modifications non commitées ; le kit du
  workflow propre indique le commit exact du run.

Preuves versionnées : [compilation/JVM/Lint/APK](validation/mission-006/build-final.json),
[JUnit Android final](validation/mission-006/instrumented-final.xml),
[échec reproduit avant correction](validation/mission-006/ergonomics-before.xml),
[quatre tests ciblés après correction](validation/mission-006/ergonomics-after.xml).
Les APK locaux et CI peuvent avoir des empreintes différentes (clé debug du runner) ;
ne pas utiliser l’empreinte locale pour prétendre vérifier l’APK CI. Chaque kit
contient les empreintes de ses propres fichiers.

Les rapports complets locaux sont dans `app/build/test-results/testDebugUnitTest/`,
`app/build/reports/lint-results-debug.*`, `app/build/outputs/androidTest-results/connected/debug/`
et `app/build/reports/androidTests/connected/debug/`. Les rapports JVM/Lint et APK
sont conservés par les artefacts Actions selon la rétention GitHub. Les preuves
Android ci-dessus sont versionnées car la CI compile l’instrumentation mais ne
l’exécute pas sur le runner standard.

La CI du commit final doit confirmer les quatre tâches, la présence des APK,
les tests JVM et l’upload des trois artefacts. Le lien de run précis et l’état
observé sont indiqués dans le bilan de livraison. Le statut/les étapes publics
sont vérifiables ; aucun accès aux journaux bruts nécessitant une authentification
n’est prétendu lorsqu’il n’est pas disponible.

### Restauration dans un nouveau processus (mission 006)

Le scénario existant a été réexécuté avec le code corrigé :

```sh
python3 scripts/verify_storage_restart.py --serial emulator-5554 --allow-layout-reset --output app/build/reports/mission-006-storage-restart
```

**10 scénarios réussis**, aucune assertion en échec : première installation,
déplacement/taille/verrou compact, profil large indépendant (écran simulé
1600×1200 / 240 dpi), refus sans écriture, disparition réelle du PID puis nouveau
processus, retour à l’accueil depuis les paramètres, reset durable, corruption et
version inconnue protégées puis archivées, autres données inchangées.
[Preuve ADB et PID](validation/mission-006/storage-restart.json).

Ce run n’utilise pas `--reboot` : aucun nouveau test de redémarrage complet de
l’émulateur n’est revendiqué en mission 006. Le test de mission 005 reste historique.
L’arrêt forcé vérifié ici est distinct d’une recréation d’activité ; il s’agit
d’un émulateur, pas des appareils physiques cibles.

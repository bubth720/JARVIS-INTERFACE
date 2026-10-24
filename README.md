# JARVIS INTERFACE

Application Android native autonome — fondations AVA-03, version 0.1.0.
**Livraison AVA-03 partielle : moteur V2 disponible, assets Athéna premium manquants.**
La validation physique multiappareils de mission 006 reste en attente.

## AVA-04 — revue artistique privée

Le pipeline de production et la galerie Compose sont développés sur
`feat/ava-04-delivery`. Douze portraits semi-réalistes ont été créés et rendus dans
un espace privé pour examen. **ART_REVIEW_PENDING / RIG_PENDING** : ils ne sont
ni publiés ni intégrés à l’APK par défaut. Les médias ne sont pas des placeholders,
mais restent des propositions pleine pose, sans prétendre des calques articulés.

[Rapport AVA-04](docs/avatar/v3/DELIVERY.md) · [Spécification artistique](docs/avatar/v3/ART-SPEC.md).
La galerie de revue est accessible depuis la preview V2 pour un pack importé
volontairement. Aucun consentement de diffusion ou d’activation n’en est déduit.

## UI-02 — identité et consentements

**Identité et personnalité** ouvre l’éditeur avec un état NON CONNECTÉ sûr.
Un laboratoire RAM volontaire permet de vérifier profils, contextes hérités,
diff/approbation, restauration et consentements mémoire. Aucun profil ou souvenir
CORE n’est lu, persisté ou activé par défaut. [Rapport UI-02](docs/ui-02/DELIVERY.md).

## Fonctionnement

Application autonome : accueil sombre, paramètres, état local et quatre panneaux :
**Avatar**, **État système**, **Applications JARVIS**, **Commandes**. L’avatar dispose
d’un prototype vectoriel 2.5D local ; les accès externes restent provisoires. Aucun
connecteur, service permanent, client réseau ou permission INTERNET n’est ajouté. Le texte
« opérationnel » décrit uniquement l’interface locale.

### Athéna V2 — AVA-03

Depuis Avatar → **Démo 2.5D** → **Athéna V2 · packs et comparaison** : importer
volontairement un ZIP V2 local, comparer au rendu V1, parcourir ses états disponibles
et confirmer son utilisation **pour la session**. Le rendu V1 reste sélectionné par
défaut ; DataStore et les profils de dashboard ne sont pas modifiés.

**ART_ASSETS_MISSING** : la planche privée fournie n'est pas un rig. Aucun visage
ni découpage de cette référence n'est publié. Les cinq PNG synthétiques déjà publics
constituent uniquement une démo technique avec IDLE/NEUTRAL/FACE ; les états sans
ressource sont refusés. Ce n'est pas Athéna premium finale et UI-03 n'est pas lancée.

[Guide APK/import](docs/avatar/v2/GUIDE.md) · [Architecture et validation](docs/avatar/v2/DELIVERY.md) ·
[Assets réels, empreintes et manquants](docs/avatar/v2/ASSET-MAP.md) ·
[Revue artistique](docs/avatar/v2/ART-REVIEW.md).

```sh
python3 scripts/check_avatar_assets.py
python3 scripts/check_avatar_v2_assets.py
python3 scripts/check_ui01_scope.py
./scripts/check.sh --no-daemon
python3 scripts/package_device_kit.py --avatar-v2
```

Le workflow conserve les artefacts précédents et ajoute
`jarvis-interface-ava03-partial-kit` (APK, documentation, vrai ZIP technique,
provenance et empreintes). La CI exécute les tests JVM/Lint et compile les tests
Android ; les exécutions sur émulateur sont rapportées séparément.

### Dashboard tactile — mission 004

- Appuyer sur **Modifier** pour afficher grille, poignées et verrouillage.
- Glisser **Déplacer** pour proposer une position, ou **Taille** pour changer les
  dimensions depuis le coin inférieur droit logique. L’aperçu s’aligne sur les cellules.
- Le cadre primaire signale une proposition valide, le cadre rouge une proposition
  invalide. Relâcher valide ou refuse l’opération entière, sans déplacer les voisins.
- Chevauchements, sorties de grille et dimensions inférieures à **3×3 cellules**
  sont refusés ; le panneau reste à sa dernière disposition valide.
- **○** verrouille un panneau ; **●** le déverrouille. Les poignées sont désactivées
  tant que le panneau est verrouillé.
- **Terminer**, Retour Android ou l’ouverture des paramètres quittent l’édition.
  En mode normal, les poignées et leurs détecteurs de glissement sont absents.
- Les poignées proposent aussi des actions TalkBack par cellule pour déplacer
  et redimensionner ; les refus sont annoncés par une zone de statut accessible.

La grille compacte utilise **6 colonnes × 12 lignes** ; à partir de 700 dp de
largeur disponible, la grille large utilise **12 × 10**. Chaque profil garde sa
disposition enregistrée indépendamment dans le stockage privé local. Les cellules
n’utilisent pas de pixels persistés. La hauteur d’une cellule est au moins 56 dp ; la grille défile si la
fenêtre est trop courte. Les panneaux ont leur propre contenu défilable.

### Sauvegarde locale — mission 005

Les déplacements, tailles et verrouillages **validés** sont enregistrés automatiquement
avec Jetpack DataStore, hors du fil principal. Aucun enregistrement pendant l’aperçu
tactile, ni pour un placement refusé, un changement de navigation ou le mode édition.
Le statut **Disposition enregistrée** confirme la fin de l’écriture ; attendre ce
statut avant un arrêt forcé. Une écriture encore en cours peut être perdue si Android
tue le processus. Les erreurs sont affichées avec une action Réessayer.

Au démarrage, les deux profils sont restaurés et validés avant affichage. Sans
sauvegarde, les valeurs initiales sont utilisées. Un fichier corrompu ou incompatible
est conservé et l’édition désactivée pour éviter de l’écraser. Dans **Paramètres →
Restaurer la disposition par défaut**, une confirmation réinitialise les deux profils
et leurs verrouillages, puis enregistre le résultat. Un fichier protégé est archivé
localement avant remplacement ; aucune autre donnée n’est effacée.

Format JSON v1 : `files/datastore/dashboard.json`, privé à l’application. Aucun état
transitoire n’y figure. Le mode édition et les paramètres ne sont pas restaurés après
mort du processus. Désinstaller/effacer les données supprime le stockage local ; la
sauvegarde Android distante reste désactivée. Les panneaux ne s’échangent pas
automatiquement et le glissement ne déclenche pas d’auto-défilement.

## Compilation

Prérequis : JDK 17 ou 21, Android SDK Platform 35 et Build Tools 35.0.0.
Configurer `ANDROID_HOME`, ou un `local.properties` non versionné :

```properties
sdk.dir=/chemin/vers/android-sdk
```

```sh
./gradlew --version
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
```

Ou utiliser `./scripts/check.sh`.

Windows : utiliser `gradlew.bat`. Le Wrapper Gradle 8.13 est versionné et sa
distribution vérifiée par SHA-256. Les dépendances et plugins sont explicitement
versionnés dans `gradle/libs.versions.toml`, avec BOM Compose fixe. Les premiers
téléchargements nécessitent Internet ; l’application installée n’en a pas besoin.
La reproductibilité concerne les versions et commandes, pas des APK bit-à-bit identiques.

APK : `app/build/outputs/apk/debug/app-debug.apk` (signature de développement).
Ne pas utiliser cette signature pour une publication définitive.

## Tests sur POCO X8 Pro et Redmi Pad SE

1. Activer les options développeur et le débogage USB ; autoriser l’ordinateur.
2. Vérifier la connexion avec `adb devices`.
3. Installer : `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. Activer le mode avion et ne démarrer aucune autre application JARVIS.
5. Ouvrir JARVIS INTERFACE : vérifier l’en-tête, les panneaux, l’état local.
6. Ouvrir les paramètres et revenir par le bouton puis par Retour Android.
7. Tester portrait/paysage, texte agrandi, TalkBack, arrière-plan/reprise et relancement.
8. Sur un appareil de test sans autre application JARVIS, exécuter :

```sh
./gradlew connectedDebugAndroidTest
```

Les tests instrumentés vérifient lancement, navigation, recréation d’activité,
présence des quatre panneaux, entrée/sortie d’édition, déplacement et agrandissement
par geste, verrouillage et absence de déplacement en mode normal. Exécuter les
scénarios tactiles automatisés en portrait avec la taille de police par défaut.
Le mode avion et l’absence d’autres apps sont des préconditions manuelles ; la suite
ne modifie pas les réglages du téléphone. Pour plusieurs appareils, utiliser
`ANDROID_SERIAL` afin de choisir la cible. Android minimum : API 26.

## Organisation

- `MainActivity.kt` : point d’entrée, edge-to-edge, assemblage Compose.
- `domain/HomeState.kt` : état immuable, dispositions par profil, actions et réduction pure.
- `domain/Dashboard.kt` : modèles de grille/panneau, contraintes et validation atomique.
- `ui/DashboardGrid.kt` : rendu adaptatif, grille, aperçu, gestes et actions accessibles.
- `domain/LayoutRepository.kt` : contrat de stockage indépendant d’Android.
- `storage/LayoutCodec.kt` : format v1, contrôle d’intégrité et conservation des extensions.
- `storage/DataStoreLayoutRepository.kt` : stockage atomique et archivage avant reset protégé.
- `JarvisApplication.kt` : instance unique du stockage et contrôleur pendant le processus.
- `ui/DashboardController.kt` : file d’actions/écritures ordonnée, restauration et statut durable.
- `ui/HomeViewModel.kt` : exposition StateFlow et délégation des actions.
- `ui/JarvisApp.kt` : rendu de l’état et émission des actions tactiles.
- `ui/JarvisTheme.kt` : palette Material 3 provisoire.
- `avatar/AvatarContract.kt` : frontière de session/médias, MP4/2D/hybride futurs.
- `avatar/AvatarAnimationController.kt` : poses, commandes et horloge sans Android.
- `avatar/AvatarArtwork.kt` : calques originaux remplaçables et secours statique.
- `avatar/AvatarDemo.kt` : hôte visible, lifecycle, Canvas et prévisualisation.
- `src/test` : règles, codec, DataStore réel et contrôleur, dont séquence de 3 000 placements.
- `src/androidTest` : démarrage, gestes, confirmation du reset et recréation d’activité.

Un seul module Gradle `app` : les frontières de packages permettent une extraction
ultérieure. Aucun chargement de widgets externes n’est implémenté.
Voir [les décisions](docs/decisions.md) et [le rapport](docs/mission-002.md).

## Validation continue — mission 003

Le workflow Android utilise Ubuntu 24.04, JDK Temurin 17 et des actions figées par
SHA. `setup-android` utilise les command-line tools `11076708` et installe
explicitement `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`.
Le paramètre `packages` remplace le défaut obsolète `tools platform-tools`.

La CI exécute `./scripts/check.sh --no-daemon`, vérifie les deux APK et des tests
JVM réellement exécutés sans échec ni test ignoré. Les artefacts sont :

- `jarvis-interface-apks` : APK application et APK de tests instrumentés ;
- `android-validation-reports` : résultats XML/HTML JVM et rapports Lint.

Les tests instrumentés sont compilés, pas exécutés sur le runner. Les versions
applicatives et le minimum API 26 restent inchangés. Les patches JDK et l’image
hébergée peuvent évoluer ; cette configuration ne promet pas des APK identiques
bit à bit. Voir le [rapport de stabilisation](docs/mission-003.md).

## Validation manuelle du dashboard

Sur POCO X8 Pro et Redmi Pad SE, hors ligne et sans autre application JARVIS :

1. Sur une installation de test, restaurer les dispositions par défaut puis vérifier
   les quatre panneaux ; faire défiler la grille si nécessaire.
2. En mode normal, balayer les contenus : aucun panneau ne doit changer de position.
3. Activer Modifier, déplacer Avatar d’une ligne vers le bas dans l’espace libre.
4. Essayer un chevauchement et une sortie de grille : refus, sans déplacement des voisins.
5. Depuis la disposition initiale, agrandir Avatar d’une ligne : accepté ; une ligne
   supplémentaire doit rencontrer le panneau Applications et être refusée.
6. Réduire un panneau à 3×3, puis tenter plus petit : refus.
7. Verrouiller un panneau : aucune modification possible ; déverrouiller et réessayer.
8. Quitter l’édition, ouvrir/fermer les paramètres et tourner l’appareil : vérifier les
   dispositions séparées et le retour au profil précédent sans perte durant la session.
9. Vérifier paysage, fenêtre partagée, texte agrandi, TalkBack et reprise d’arrière-plan.
10. Attendre **Disposition enregistrée**, puis `adb shell am force-stop com.jarvis.interfaceapp`.
    Relancer l’application : les positions, tailles et verrous doivent être identiques.
    Le mode édition doit être désactivé. Ce test est distinct d’une rotation d’écran.
11. Redémarrer l’appareil et vérifier les deux profils sans connexion réseau.
12. Annuler la réinitialisation : rien ne change. Confirmer : les deux profils et
    verrous reviennent aux valeurs initiales, également après arrêt forcé et relance.

## Suite envisagée

Après validation de la mission 005 : confirmer l’ergonomie et la restauration sur
POCO X8 Pro / Redmi Pad SE, puis définir la mission 006. Aucun connecteur externe ni
animation n’est engagé. Voir le [rapport de mission 005](docs/mission-005.md).

### Vérification de mort du processus sur émulateur jetable

Ce scénario réinitialise les dispositions de l’application de test, change la taille
de l’émulateur et peut le redémarrer. Ne pas utiliser sur un environnement contenant
des dispositions à conserver. Il refuse les numéros de série des téléphones.

```sh
python3 scripts/verify_storage_restart.py --serial emulator-5554 --allow-layout-reset --reboot
```

Il installe l’APK debug, vérifie les deux profils, tailles/verrous, les vrais arrêts
`am force-stop` (PID supprimé puis différent), le redémarrage (boot ID différent),
les fichiers invalides protégés et la réinitialisation. Les preuves sont écrites dans
`app/build/reports/storage-restart/`. Les tests Android ordinaires réinitialisent eux
aussi les dispositions via les paramètres métier ; utiliser un appareil de test.

## Mission 006 — validation multiappareils

La validation physique **POCO X8 Pro / Redmi Pad SE reste en attente** des résultats
utilisateur. Aucun test émulateur n’est assimilé à un test physique.

- [Installation Android sans ordinateur et scénarios](docs/device-validation/guide-android.md)
- [Grille pour les deux appareils](docs/device-validation/grille-appareils.csv)
- [Retour à remplir par appareil](docs/device-validation/retour-utilisateur.md)
- [Rapport, correctif reproduit et résultats](docs/mission-006.md)

Dans le workflow de la branche `test/interface-device-006`, télécharger l’artefact
**jarvis-interface-device-kit** : APK application, guide, fiches, commit et empreintes.
Le kit peut aussi être généré localement après compilation :

```sh
python3 scripts/package_device_kit.py
```

En fenêtre courte/texte agrandi, l’en-tête défile indépendamment pour garder la grille
atteignable. Faire défiler sa zone pour retrouver les boutons et le statut de sauvegarde.
Ne pas désinstaller une ancienne version pour contourner un conflit de signature :
cela supprimerait les dispositions ; voir le guide avant toute mise à jour.

## AVATAR-001 — prototype animé local

Le panneau Avatar affiche un personnage féminin adulte provisoire original, composé
avec des formes indépendantes (pas une illustration aplatie). Respiration IDLE,
paupières BLINK et inclinaison HEAD_TILT sont combinables. **Démo 2.5D** ouvre les
commandes manuelles, l’arrêt progressif et la réinitialisation de l’avatar seul.
Les états LISTENING/THINKING/SPEAKING sont des contrats sans animation de parole.

Le calcul s’arrête lorsque le dessin est entièrement hors écran, l’activité en
arrière-plan, le rendu en échec ou l’hôte détruit. Aucun état avatar n’est écrit dans
DataStore ; la sauvegarde V1 des dispositions reste inchangée. La recréation rebâtit
un avatar en IDLE. Les animations reprennent sans rattrapage du temps caché.

- [Architecture, choix du moteur et validation](docs/avatar/architecture-et-validation.md)
- [Installation et essais des animations](docs/avatar/guide-test.md)
- [Liste des assets nécessaires au personnage définitif](docs/avatar/assets-definitifs.md)

Télécharger **jarvis-interface-avatar-kit** dans le workflow de la branche
`feature/interface-avatar-001` (APK, guide, rapport et provenance). Emballage local :

```sh
python3 scripts/package_device_kit.py --avatar
```

Aucun SDK externe ni nouvelle dépendance Gradle. Le personnage artistique définitif,
les images de référence, la voix, les visèmes et le lip-sync ne sont pas développés.
Aucun résultat physique ni gain de performances n’est revendiqué.

## Livraisons AVA-04, UI-02 et UI-03

- [AVA-04 : ressources premium en revue privée et galerie Android](docs/avatar/v3/DELIVERY.md).
  Les propositions artistiques ne sont ni publiées ni activées définitivement ;
  elles restent des portraits interchangeables, sans rig articulé définitif.
- [UI-02 : éditeur de personnalité et consentements](docs/ui-02/DELIVERY.md).
  Éditeur et laboratoire RAM disponibles ; propriétaire CORE non raccordé.
- [UI-03 : proposition de client et diagnostic](docs/ui-03/DELIVERY.md).
  Client local testé, véritable CORE **non connecté** ; contrat de contenu et
  authentification inter-APK à approuver avant toute connexion sensible.

AVA-05 reste en attente : portraits de parole ≠ visèmes ou couches articulées.
Les assets approuvés (yeux/paupières/bouche/buste séparés) et les futurs signaux
VOICE manquent pour livrer une synchronisation labiale réelle. Aucun lip-sync
ou moteur vocal n'est présenté comme terminé. Aucune fusion ni installation
sur appareil personnel n'est effectuée.

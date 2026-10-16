# JARVIS INTERFACE

Application Android native autonome — mission 002, version 0.1.0.

## Fonctionnement

Application autonome : accueil sombre, paramètres, état local et quatre panneaux :
**Avatar**, **État système**, **Applications JARVIS**, **Commandes**. Les trois
intégrations sont des emplacements provisoires ; aucun moteur d’avatar, connecteur,
service permanent, client réseau ou permission INTERNET n’est ajouté. Le texte
« opérationnel » décrit uniquement l’interface locale.

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
disposition en mémoire dans le ViewModel. Les cellules n’utilisent pas de pixels
persistés. La hauteur d’une cellule est au moins 56 dp ; la grille défile si la
fenêtre est trop courte. Les panneaux ont leur propre contenu défilable.

Les dispositions et verrouillages survivent aux recréations d’activité pendant
la session. Ils reviennent aux valeurs initiales après mort du processus : la
persistance définitive est hors du périmètre de cette mission. Les panneaux ne
s’échangent pas automatiquement et le glissement ne déclenche pas d’auto-défilement.

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
- `ui/HomeViewModel.kt` : exposition StateFlow et traitement des actions.
- `ui/JarvisApp.kt` : rendu de l’état et émission des actions tactiles.
- `ui/JarvisTheme.kt` : palette Material 3 provisoire.
- `avatar/AvatarContract.kt` : frontière préliminaire MP4/2D/hybride, sans moteur.
- `src/test` : règles de placement et réduction d’état, dont séquence déterministe de 3 000 propositions.
- `src/androidTest` : tests de démarrage Android.

Un seul module Gradle `app` : les frontières de packages permettent une extraction
ultérieure. Aucun stockage durable ni chargement de widgets externes n’est implémenté.
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

1. Vérifier les quatre panneaux ; faire défiler la grille si nécessaire.
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
10. Arrêter le processus puis relancer : retour aux dispositions initiales attendu.

## Suite envisagée

Après validation de la mission 004 : définir la sauvegarde locale versionnée des
dispositions et sa restauration. Aucun connecteur externe ni animation n’est engagé.
Voir le [rapport de mission 004](docs/mission-004.md).

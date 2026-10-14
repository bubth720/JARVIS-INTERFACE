# JARVIS INTERFACE

Application Android native autonome — mission 002, version 0.1.0.

## Fonctionnement

Accueil sombre, en-tête, zone de dashboard, emplacement rectangulaire d’avatar,
paramètres provisoires et état local. Aucun moteur IA, lecteur vidéo, connecteur,
service permanent ou permission INTERNET. AndroidX ajoute une permission interne
de protection des récepteurs, sans autorisation demandée à l’utilisateur. Les autres applications JARVIS ne sont
ni recherchées ni démarrées. Le texte « opérationnel » décrit uniquement cette interface.

L’écran utilise une colonne sur téléphone et deux panneaux côte à côte à partir
de 700 dp de largeur disponible. Le contenu reste défilable avec texte agrandi.

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

Les tests instrumentés vérifient lancement, navigation et recréation d’activité.
Le mode avion et l’absence d’autres apps sont des préconditions manuelles ; la suite
ne modifie pas les réglages du téléphone. Pour plusieurs appareils, utiliser
`ANDROID_SERIAL` afin de choisir la cible. Android minimum : API 26.

## Organisation

- `MainActivity.kt` : point d’entrée, edge-to-edge, assemblage Compose.
- `domain/HomeState.kt` : état immutable, actions et réduction pure.
- `ui/HomeViewModel.kt` : exposition StateFlow et traitement des actions.
- `ui/JarvisApp.kt` : rendu de l’état et émission des actions tactiles.
- `ui/JarvisTheme.kt` : palette Material 3 provisoire.
- `avatar/AvatarContract.kt` : frontière préliminaire MP4/2D/hybride, sans moteur.
- `src/test` : tests JVM de l’état et de la navigation.
- `src/androidTest` : tests de démarrage Android.

Un seul module Gradle `app` : les frontières de packages permettent une extraction
ultérieure. Aucun stockage ni widget extensible n’est implémenté à ce stade.
Voir [les décisions](docs/decisions.md) et [le rapport](docs/mission-002.md).

## Suite envisagée

Mission 003 : panneaux déplaçables/redimensionnables, contraintes, dispositions
et sauvegarde locale, après validation de la mission 002.

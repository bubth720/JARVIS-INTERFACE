# Rapport — mission 002

Date : 14 octobre 2026. Branche : `development/mission-002`.

## Livré

Projet Android à module unique, Kotlin/Compose/Material 3, MVVM et StateFlow.
Accueil sombre adaptatif, en-tête, zones dashboard et avatar, paramètres et état local.
Contrat avatar préliminaire MP4/2D/hybride sans moteur. Wrapper, catalogue de versions,
script de vérification, workflow GitHub Actions et instructions appareils dans le README.
Aucun dépôt externe modifié. Aucun widget tactile, stockage, connecteur ou moteur IA développé.

## Vérifications réellement effectuées

Dans `/workspace/JARVIS-INTERFACE`, avec JDK Temurin 17.0.16+8, SDK 35 et Gradle 8.13 :

```sh
./scripts/check.sh --no-daemon
./gradlew lintDebug --rerun-tasks --no-daemon
```

- `assembleDebug` : réussi ; APK de développement signé produit.
- `testDebugUnitTest` : 3 tests exécutés, 0 échec, 0 erreur, 0 ignoré.
- `assembleDebugAndroidTest` : réussi ; compilation et packaging des 3 tests Android.
- `lintDebug` : réussi. Vérification finale forcée après correction du manifeste :
  « No issues found », aucun avertissement ni erreur Lint.
- Signatures des APK application et tests : vérifiées par `apksigner verify`.
- Manifeste final inspecté : aucun service et aucune permission INTERNET.
  AndroidX ajoute une permission interne de niveau signature protégeant ses récepteurs
  et un fournisseur d’initialisation ; aucune permission utilisateur n’est demandée.
- Versions APK vérifiées : package `com.jarvis.interfaceapp`, 0.1.0, minimum API 26,
  cible API 35.

APK : `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256 : `3e9d07c3a487dfd5fb1c20d5b079150eb5d927d492ac7748a6c414c2a0579dc7`.
Les journaux locaux sont conservés dans `app/build/reports/mission-002-build.log`
et `mission-002-lint.log`, les résultats JVM dans `app/build/test-results`.
Les sorties de compilation ne sont pas versionnées.

## Corrections pendant la validation

L’image cloud ne fournissait initialement qu’un JRE. Un JDK complet et le SDK Android
ont été installés hors du dépôt. Les archives Gradle/JDK et outils Android ont été
vérifiées avec les checksums officiels ; aucune vérification TLS n’a été désactivée.
Les caches Java/Android ont été redirigés vers des chemins accessibles et le proxy
Java configuré uniquement pour les commandes cloud.

Deux chaînes Kotlin mal échappées ont été corrigées. Un attribut de thème exigeant
API 27 a été retiré pour conserver le minimum API 26. Une icône provisoire et une
politique explicite d’exclusion des sauvegardes/transferts ont été ajoutées.
Gradle signale des usages dépréciés dans la chaîne de build en vue de Gradle 9 ;
le projet reste volontairement figé sur Gradle 8.13.

## Non exécuté et limites

- `connectedDebugAndroidTest` : non exécuté. Aucun appareil physique connecté et
  aucun émulateur configuré ; pas de `/dev/kvm` ni de périphérique USB exposé.
- Installation réelle, rendu visuel, mode avion et performances sur POCO X8 Pro
  et Redmi Pad SE : non validés. Procédure fournie dans le README.
- Workflow GitHub Actions : ajouté ; son exécution distante n’est pas attestée
  par les résultats locaux.
- Aucun APK de production ni signature de publication.

L’absence de dépendance à CORE/SERVICES est vérifiée dans le code et le manifeste.
Son comportement sur appareil sera confirmé par les tests instrumentés et manuels.

## Étape suivante

Après validation de la mission 002 : mission 003, édition des panneaux et
persistance locale. Cette mission n’a pas commencé.

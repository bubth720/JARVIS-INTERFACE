# AVA-01 — Ressources avatar 2.5D locales

Module ANIMATION, dépôt `bubth720/JARVIS-INTERFACE`, branche `feat/ava-01-ui-avatar`.
Base technique vérifiée : `139bfdc4aac5d2fc9b841c5b804c3b89bfc57ec6`.
Départ conservant les fiches : `50f5f61708d5ce6b141dec98fe0c73d89eaf00e3`.
La comparaison des snapshots montre que la préparation ajoute uniquement les cinq fiches de campagne, sans modifier les sources de cette base.

## Fonction livrée

L'avatar et le dashboard historiques restent le comportement initial. Ouvrir le panneau avatar → **Démo 2.5D** → **Ressources et calques locaux**. La nouvelle prévisualisation charge sept textures PNG synthétiques, dont cinq calques visibles simultanément, deux variantes de cheveux, pivots et commandes d'animation existantes. Aucun portrait personnel ni ressource artistique finale n'est fourni.

**Importer un ZIP local** ouvre le sélecteur Android SAF. Exemple reproductible : [avatar-demo.zip](fixtures/avatar-demo.zip). L'archive contient `manifest.json` et les PNG à la racine. Elle est lue en RAM, sans extraction, copie persistante ni conservation d'autorisation URI. Annuler le sélecteur ou fermer la prévisualisation laisse le dashboard et DataStore inchangés. Aucun moteur, microphone, réseau ou IPC n'est ajouté.

Le manifeste local `version: 1` décrit ID, fichier, SHA-256, ordre, rectangle, pivot, profondeur, groupe/variante et masque rectangulaire facultatif de chaque calque. Les coordonnées de rendu sont normalisées en 240×320 ; le calque optionnel `eyes` suit le canal paupières, les autres suivent respiration/inclinaison selon leur profondeur. Ce format est propre aux ressources graphiques et ne modifie aucun contrat UNIFIED v1. Les empreintes détectent la corruption ; elles ne prouvent pas l'identité de l'auteur d'une archive.

Bornes : manifeste64KiB, texture compressée2MiB, ensemble8MiB,16calques, côté2048px, total2M pixels décodés (~8MiB RGBA). Rejet avant décodage sur empreinte ou signature PNG incorrecte ; vérification des dimensions avant allocation ; noms plats et ZIP sans extraction. Une erreur produit un repli vectoriel statique, jamais une nouvelle tentative par image à chaque frame. Pas de cache global ; références de textures relâchées à la fermeture du host. Le GC Android reste responsable de la récupération physique, aucune preuve absolue de mémoire libérée n'est annoncée.

## Surfaces de code

- `avatar/assets/AvatarAssetManifest.kt` : format strict, clés dupliquées/inconnues refusées, bornes et transformations par profondeur.
- `AvatarAssetLoader.kt` : sources locales/ZIP, lecture bornée, empreintes et décodage Android.
- `LayeredAvatarArtwork.kt` : renderer, variantes, pivots et masques.
- `AvatarPreviewPanel.kt` : prévisualisation et import explicite.
- `avatar/AvatarDemo.kt` : entrée optionnelle et suspension du host couvert, réutilisation du même lifecycle/contrôleur/clock.
- Assets PNG publics synthétiques, fixture ZIP, tests JVM et instrumentés.

Aucun changement de signature, package, permission, DataStore, modèle, moteur vocal, admission ou JNI.

## Validation

Commandes exécutées dans le cloud avec JDK17, SDK35 et wrapper Gradle8.13 épinglé :

- `./gradlew :app:compileDebugKotlin --no-daemon` : réussi.
- `./scripts/check.sh --no-daemon --console=plain --max-workers=1` : réussi, 84 tests JVM (76 historiques +8 nouveaux), 0 échec/erreur/saut ; assembleDebug, Lint et assembleDebugAndroidTest réussis. Lint : zéro issue.
- `python3 scripts/check_avatar_assets.py` : sept PNG, SHA-256, CRC, transparence RGBA, copie fixture manifeste et aller-retour ZIP vérifiés ; la CI exécute désormais ce contrôle.
- `git diff --check` : réussi.
- Inspection APK `aapt` : package historique inchangé, minSdk26/target35, aucune permission Internet, micro ou stockage global. La seule permission est celle du receiver AndroidX non exporté déjà généré.

Trois nouveaux tests instrumentés (vrai décodage PNG, pixels/retour neutre, corruption) compilés **mais non exécutés** ; aucun émulateur/image système/KVM local. Aucun appareil personnel utilisé. Les huit tests JVM valident manifeste hostile, ZIP bomb, traversée de chemins, limites et transformations/lifecycle, sans revendiquer une mesure graphique Android physique.

## Limites et suite

Pas de personnage final, lip-sync, modulation VOICE ni raccordement CORE. Les masques V1 sont des rectangles, pas des textures alpha de masque séparées. Les tests d'affichage Android et mesures GPU/RAM physiques restent à qualifier. Les futures ressources sont importées volontairement, sous les mêmes limites.
AVA-02 attend VOICE-02 et CORE-02 effectivement livrés ; UI-01/UI-02 attendent leurs propres dépendances. Aucun démarrage automatique de ces missions.

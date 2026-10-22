# AVA-01 — Système d'assets et avatar 2.5D multicouche remplaçable

## 1. Identification
**Module** ANIMATION ; **dépôt** `bubth720/JARVIS-INTERFACE` ; **priorité** P0. **Dépendances** : Aucune.
**Base technique vérifiée** : `feature/interface-avatar-001` à `139bfdc4aac5d2fc9b841c5b804c3b89bfc57ec6`.
**Branche préparation** `plan/intensive-2026-10-22`, **branche code proposée** `feat/ava-01-ui-avatar`. **État** : instructions uniquement, aucun travail Codex lancé.

## 2. État existant et non-régression
L'application Compose actuelle fournit dashboard draggable, verrous et layouts persistants via DataStore V1, avatar 2.5D AVATAR-001 à trois animations (IDLE/BLINK/HEAD_TILT), tests JVM et instrumentés sur émulateur, fallback statique. Pas de personnage artistique final, de lip-sync ni d'IPC CORE/VOICE réel. Préserver la disposition et les identifiants des panneaux.

## 3. Objectif concret
Faire passer l'avatar vectoriel de démonstration à un renderer multicouche acceptant de vrais assets locaux sans remplacer les animations existantes.

## 4. Capacités à développer
1. Créer LayeredAvatarAsset et manifest versionné (calques, pivots, masques, ordre de rendu, bornes, proportions).
2. Construire AvatarAssetLoader depuis ressources locales packagées ou import explicite SAF, sans téléchargement.
3. Développer contrôles d'intégrité/format/taille textures, cache borné et repli vectoriel statique si ressource absente.
4. Adapter AvatarArtwork/AvatarDemo à un rig de calques indépendants, conserver IDLE/BLINK/HEAD_TILT et horloge foreground.
5. Ajouter mécanisme de variantes tenue, cheveux, expressions de base sans imposer un moteur 3D ou Cubism.
6. Créer écran de prévisualisation assets, calques/pivots et erreur de profil, sans stocker des textures lourdes en mémoire cachée.

## 5. Architecture et classes
1. AvatarAssetManifest, LayerDescriptor, Pivot, ResourceLimit
2. AvatarAssetLoader + AssetValidation
3. LayeredAvatarArtwork + PoseTransform
4. AvatarPreviewPanel + SafeStaticFallback
Séparer modèle de rendu, état des interactions, source des événements et ressources graphiques. Aucune logique LLM ou réseau dans une composable.

## 6. Fichiers concernés
1. Existants : app/src/main/java/com/jarvis/interfaceapp/avatar/{AvatarArtwork,AvatarDemo,AvatarAnimationController}.kt
2. À créer : app/src/main/java/com/jarvis/interfaceapp/avatar/assets/{AvatarAssetManifest,AvatarAssetLoader,LayeredAvatarArtwork}.kt
3. À créer : app/src/test/.../AvatarAssetLoaderTest.kt et res/raw/fixtures d'assets synthétiques
Utiliser les chemins réels du module app ; vérifier autres sourceSets avant modifications. Les cibles nouvelles sont proposées, pas déjà développées.

## 7. Étapes de programmation
1. Reprendre le prototype 2.5D AVATAR-001 et géométrie actuelle.
2. Créer schema manifeste avec pivots et validation strictes, sans assets personnels implicites.
3. Coder chargement paresseux et renderer multi-plans, préserver fallback et clock.
4. Intégrer assets synthétiques pour vérifier rigidité et transparence, maintenir les commandes existantes.
5. Exécuter tests pixels/pose/rotation et vérifier qu'aucun animation tourne hors écran.

## 8. Technologies et dépendances
Jetpack Compose Canvas + Android res/SAF, pas de dépendance native nouvelle.
Aucune dépendance réseau et pas de moteur 3D tiers lourd sans examen de licence, ABI et GPU. Cibler sobriété, écrans étroits/largeurs variables et accessibilité.

## 9. Connexions entre modules
UI-01 hôte visuel, VOICE-02 fournit futurs marqueurs temporels, ID-01 état de profil purement stylistique.
Raccordement par ports opt-in uniquement ; ne pas dupliquer mémoire, moteur vocal ou autorité CORE dans INTERFACE.

## 10. Compatibilité et sûreté
Pas d'effacement DataStore, renommage de package, modification de signature, migration à perte ou déclenchement d'une activité privée. Ressources graphiques fermées au lifecycle. Absence d'assets definitifs = fallback visible, pas fiction de rendu 2.5D final.

## 11. Livrables code obligatoires
UI et contrôleurs compilables et exécutables, previews ou écrans interactifs réellement pilotés, tests de commandes/pixels/stockage minimaux, rapport bref de changements et captures de démo clairement attribuées à émulateur/fixtures.

## 12. Critères d'acceptation
1. Au moins cinq calques indépendants bougent sous une pose de test et reviennent au neutre.
2. Le manifeste invalide déclenche repli statique sans rendre inutilisable le dashboard.
3. Toutes animations et dispositions AVATAR-001 demeurent disponibles.
Tester l'expérience visible ; un simple contrat, SVG statique ou mock d'événement non intégré ne suffit pas.

## 13. Dépendances et organisation
Aucune. UI-01, UI-02 et AVA-01 peuvent faire des branches séparées à condition de ne pas modifier simultanément `DashboardGrid` ni `JarvisApp`. AVA-02 suit AVA-01.

## 14. Contrôle de l'exécution
Ce document est un mandat de préparation, **pas une autorisation de lancer Codex**. Aucune fusion dans main, installation personnelle, changement de permissions ou protocole inter-app sensible sans nouvel accord.

## 15. Livraison et compte rendu
Module, mission, branche, base et commit, composants fonctionnels, fichiers, tests exécutés, résultat observable, blocages et prochaine action.

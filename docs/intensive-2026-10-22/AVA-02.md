# AVA-02 — Expressions faciales, visèmes et synchronisation parole

## 1. Identification
**Module** ANIMATION ; **dépôt** `bubth720/JARVIS-INTERFACE` ; **priorité** P1. **Dépendances** : AVA-01,VOICE-02,CORE-02.
**Base technique vérifiée** : `feature/interface-avatar-001` à `139bfdc4aac5d2fc9b841c5b804c3b89bfc57ec6`.
**Branche préparation** `plan/intensive-2026-10-22`, **branche code proposée** `feat/ava-02-ui-avatar`. **État** : instructions uniquement, aucun travail Codex lancé.

## 2. État existant et non-régression
L'application Compose actuelle fournit dashboard draggable, verrous et layouts persistants via DataStore V1, avatar 2.5D AVATAR-001 à trois animations (IDLE/BLINK/HEAD_TILT), tests JVM et instrumentés sur émulateur, fallback statique. Pas de personnage artistique final, de lip-sync ni d'IPC CORE/VOICE réel. Préserver la disposition et les identifiants des panneaux.

## 3. Objectif concret
Créer un contrôleur d'expressions et de parole animé par de vrais événements temporels publics sans micro ni audio privé et sans prétendre à une synchronisation phonème impossible.

## 4. Capacités à développer
1. Définir FaceExpression neutral/focus/joy/concern avec intensités bornées et transitions temporisées.
2. Créer VisemeTimeline composée de poses bouche A/E/I/O/U/REST et horodatages fournis par VOICE, avec interpolation et fallback par enveloppe de timing.
3. Coder SpeechAvatarController liant SPEAKING/PAUSED/STOPPED et timeline à LayeredAvatarArtwork, arrêt synchronisé et ressource libérée.
4. Préserver clignements automatiques, respiration et inclinaison tête en parallèle, sans conflit de contrôleurs.
5. Ajouter prévisualisation accessible d'expressions par commandes manuelles et profils de frame budget 30 FPS visé.
6. Gérer désynchronisation, perte d'événements, arrière-plan et déchargement par retour immédiat au neutre.

## 5. Architecture et classes
1. FaceExpressionState + ExpressionTransitionEngine
2. VisemeTimeline + MouthPoseResolver
3. SpeechAvatarController + CoreEventAdapter
4. AvatarFrameBudgeter et TestClock
Séparer modèle de rendu, état des interactions, source des événements et ressources graphiques. Aucune logique LLM ou réseau dans une composable.

## 6. Fichiers concernés
1. Existants : app/src/main/java/com/jarvis/interfaceapp/avatar/{AvatarAnimationController,AvatarArtwork,AvatarDemo}.kt
2. À créer : app/src/main/java/com/jarvis/interfaceapp/avatar/expression/{FaceExpressionState,VisemeTimeline,SpeechAvatarController}.kt
3. À créer : tests de frames et synchronisation temporelle
Utiliser les chemins réels du module app ; vérifier autres sourceSets avant modifications. Les cibles nouvelles sont proposées, pas déjà développées.

## 7. Étapes de programmation
1. Relire unités temps/cues VOICE-02 et événements CORE-02 ; définir mapping stable.
2. Coder état visage + visèmes sans coupler UI au runtime voix.
3. Intégrer timelines dans artwork multicouche, garder animation IDLE indépendante.
4. Créer lecteur de fixture publique pour preview avec durée réelle.
5. Tester perte d'événement, pause longue, hors écran et fallback statique.

## 8. Technologies et dépendances
Kotlin/Compose Canvas, frame clock/lifecycle existants ; aucune écoute microphone, aucune analyse de voix privée.
Aucune dépendance réseau et pas de moteur 3D tiers lourd sans examen de licence, ABI et GPU. Cibler sobriété, écrans étroits/largeurs variables et accessibilité.

## 9. Connexions entre modules
VOICE-02 envoie temps de segments, CORE-02 états de session ; ne pas intégrer moteur audio directement dans l'avatar.
Raccordement par ports opt-in uniquement ; ne pas dupliquer mémoire, moteur vocal ou autorité CORE dans INTERFACE.

## 10. Compatibilité et sûreté
Pas d'effacement DataStore, renommage de package, modification de signature, migration à perte ou déclenchement d'une activité privée. Ressources graphiques fermées au lifecycle. Absence d'assets definitifs = fallback visible, pas fiction de rendu 2.5D final.

## 11. Livrables code obligatoires
UI et contrôleurs compilables et exécutables, previews ou écrans interactifs réellement pilotés, tests de commandes/pixels/stockage minimaux, rapport bref de changements et captures de démo clairement attribuées à émulateur/fixtures.

## 12. Critères d'acceptation
1. Une séquence timeline de visèmes produit des poses bouche distinctes mesurables puis neutre.
2. Le rendu ne continue pas à calculer en arrière-plan ou après libération.
3. Absence de cues ne se traduit pas par un faux lip-sync présenté comme exact.
Tester l'expérience visible ; un simple contrat, SVG statique ou mock d'événement non intégré ne suffit pas.

## 13. Dépendances et organisation
AVA-01,VOICE-02,CORE-02. UI-01, UI-02 et AVA-01 peuvent faire des branches séparées à condition de ne pas modifier simultanément `DashboardGrid` ni `JarvisApp`. AVA-02 suit AVA-01.

## 14. Contrôle de l'exécution
Ce document est un mandat de préparation, **pas une autorisation de lancer Codex**. Aucune fusion dans main, installation personnelle, changement de permissions ou protocole inter-app sensible sans nouvel accord.

## 15. Livraison et compte rendu
Module, mission, branche, base et commit, composants fonctionnels, fichiers, tests exécutés, résultat observable, blocages et prochaine action.

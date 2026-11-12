# M55-D — cockpit cinématique : sphère 3D tactile, avatar 2.5D, modes adaptatifs

Laboratoire Android autonome (`fr.jarvis.m55d.lab`, min29/target35, testOnly, **aucune permission**).
V1-A, V1-B, M50, le cockpit 008 et l'application historique restent figés (`tools/check_scope.py`).

- **Sphère 3D** (Compose Canvas, sans bibliothèque) : points de Fibonacci, rotation au doigt, inertie
  (demi-vie 0,45 s), impulsion au toucher, couleurs et respiration selon l'état, description accessible.
- **Avatar 2.5D** : buste vectoriel par couches (expression par état, clignement, bouche liée au niveau
  audio). Emplacement `atlas` pour un pack PNG importé par le propriétaire en stockage privé ; **aucune
  image personnelle n'est copiée, embarquée ni publiée**.
- **Contrat visuel v1** (`CockpitContract.kt`) : `CockpitEvent(sessionId, state, source, audioLevel,
  latencyMs, errorCode, atMillis)` ; la sphère ne connaît ni llama.cpp ni CORE. Absence d'événement
  pendant 10 s → état neutre ; une ERREUR reste visible ; événements périmés refusés.
- **Modes** CINÉMATIQUE / ÉQUILIBRÉ / ÉCONOMIE + **Auto** : régulateur à hystérésis sur le p95 du temps
  de trame et l'état thermique Android (dégradation après 2 fenêtres lentes, remontée après 4 bonnes,
  3 s minimum entre changements ; thermique SÉVÈRE → ÉCONOMIE immédiat, même en manuel).
- **Commandes conservées dans tous les modes** : micro (désactivé ici, indiqué), saisie, envoi, arrêt,
  indicateur HORS LIGNE.
- La seule source d'événements de ce labo est **DÉMO**, affichée comme telle : elle anime les états
  et ne produit **aucune réponse**. Le raccordement CORE/VOICE est l'objet de M55-E.

Mesures : FPS, p50/p95 du temps de trame, % de trames > 25 ms, sur émulateur (pas un téléphone).

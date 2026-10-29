# UI-M50-01 — Coquille Android JARVIS et conversation CORE sans faux READY

Dépôt: bubth720/JARVIS-INTERFACE
Branche de distribution: mission/m50-integrated-beta-20261029
Base figée de travail: 9e60e4571fe21d112b2f5e425391b8bcdcc3299e

**MISSION, PAS IMPLÉMENTATION.** Cible M50 = application unique avec coeur conversation, documents/CSV locaux et composants indépendants, seulement après qualifications de confiance. Cette mission est une tranche spécifique, distincte des PR lot3/lot4 existantes.

## Commande pour Codex
Ouvrir AGENTS.md si présent; vérifier les SHA, les PR existantes, provenance/CI et les lignes exactes du code. Créer une NOUVELLE branche `codex/m50-*` depuis cette branche de distribution, puis une PR brouillon vers elle. Ne pas modifier ni merger main, ni toucher aux données personnelles, ni relever de permission/SDK/ABI, ni activer connexion sensible sans validation distincte.

## Fichiers prioritaires à inspecter (confirmer leur présence)
- `docs/fusion-02/TRANSPORT-REVIEW.md`
- `app/src/main/java/com/jarvis/interfaceapp/ui/chat/core/CoreConversationClientAdapter.kt`
- `app/src/main/java/com/jarvis/interfaceapp/avatar/v2/AvatarExpressionStateMachine.kt`
- `app/src/main/java/com/jarvis/interfaceapp/workspace/WorkspaceStore.kt`
- `app/src/androidTest/`

## Étapes détaillées

### 1. A — Compatibilité et identité

Réutiliser les 217 cas JVM,18 Python et 48 exécutions Android publics de PR#12; garder tests privés NOT_RUN, pas PASS. Examiner minSdk26, UI chat ACK/chunks/Stop/UNKNOWN, origine NONE et replay.

**Preuves d'acceptation de la phase :** diff ciblé et réversible, test nominal + contre-exemple, SHA40, commande exacte, sortie CI liée au HEAD, risques de régression et état PASS/BLOCKED/NOT_RUN.

### 2. B — Écran intégrateur en lecture seule

Créer dans une variante démo ou pilote opt-in une façade locale unique pour chat, sources/citations, capacités et aperçu avatar. Tout statut producteur non authentifié affiche NON_CONNECTÉ, jamais READY. UX claire sur mode avion, recherche indisponible, annulation et absence du LLM.

**Preuves d'acceptation de la phase :** diff ciblé et réversible, test nominal + contre-exemple, SHA40, commande exacte, sortie CI liée au HEAD, risques de régression et état PASS/BLOCKED/NOT_RUN.

### 3. C — Transport de laboratoire

Adapter la même interface de client à un simulateur local et à un futur CORE authentifié, interface en défaut OFF. Tester UID/certificat falsifié, epoch périmé, trou de séquence et redémarrage; aucune permission exportée/Binder créé avant décision utilisateur.

**Preuves d'acceptation de la phase :** diff ciblé et réversible, test nominal + contre-exemple, SHA40, commande exacte, sortie CI liée au HEAD, risques de régression et état PASS/BLOCKED/NOT_RUN.

### 4. D — Qualité Android

Campagnes instrumentées API26/29/35 et écrans rotation, retour, processus relancé, gros texte, capture de tests synthétiques, avatar économique. Comparer APK testOnly à l'application existante et vérifier absence de fuite de document/voix personnelle.

**Preuves d'acceptation de la phase :** diff ciblé et réversible, test nominal + contre-exemple, SHA40, commande exacte, sortie CI liée au HEAD, risques de régression et état PASS/BLOCKED/NOT_RUN.

### 5. E — Préparation packaging

Décrire une seule icône/entrée JARVIS sans dupliquer le processus CORE, les voix, le tokenizer ou NEXUS; fournir rapport de consommation mémoire, retour arrière, diff, CI exacte, PR brouillon. L'APK unifiée de production reste non autorisée.

**Preuves d'acceptation de la phase :** diff ciblé et réversible, test nominal + contre-exemple, SHA40, commande exacte, sortie CI liée au HEAD, risques de régression et état PASS/BLOCKED/NOT_RUN.

## Règles de stop et critères finals
- D'abord prototypes OFF/testOnly. Le consentement utilisateur à l'architecture ou à des principes n'autorise PAS permissions, authentification de production, migration, import natif, export SAF ou installation physique.
- Préserver contrat historique, bases, voix personnelles, minSdk, permissions et anciennes variantes; voir le dossier central M50.
- Aucune réussite hôte ne qualifie Android réel; aucun résultat d'émulateur ne qualifie un appareil physique.
- Ne jamais masquer des échecs, affaiblir des tests, extrapoler READY, prétendre à un outil natif libéré ou créer de citation synthétique présentée comme réelle.
- Rendre : module, mission, branche, HEAD SHA + tree, tests succès/échecs/skips/non exécutés, CI et PR, blocages, dépendances, rollback, prochaine action.
- Ne jamais fusionner main sans confirmation explicite; un échec bloquant = rapport et STOP.

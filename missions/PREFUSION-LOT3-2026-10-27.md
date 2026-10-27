# UI-PF-02 — Tests instrumentés Android et consommateur CORE sûr

**État : MISSION CODEX À EXÉCUTER (aucune implémentation revendiquée par ce document).**
Dépôt : bubth720/JARVIS-INTERFACE
Branche source : mission/prefusion-lot3-20261027
SHA de base du lot vérifié : 4bc1993d4b6525b248f2b12e95a18a279a3d9fdc

## Règles d'exécution obligatoires
1. Lire AGENTS.md s'il existe, les rapports du lot précédent, vérifier HEAD et CI.
2. Créer une nouvelle branche codex/prefusion-lot3-* issue de la branche de mission. Ne pas développer dans main.
3. Tranches courtes avec tests négatifs, commits, CI exacte, PR brouillon vers la branche mission.
4. Hors ligne au runtime, compatibilité Android et données personnelles préservées. Aucun merge, migration, extension de permissions, import architectural ou activation sensible sans décision explicite.
5. Tout effet d'écriture, nouveau backend SQLite natif ou transport UID/certificat non approuvé reste OFF et limité à tests synthétiques.

## Fichiers à examiner
- `app/src/main/java/com/jarvis/interfaceapp/ui/chat/core/CoreConversationClientAdapter.kt`
- `docs/fusion-02/TRANSPORT-REVIEW.md`
- `docs/prefusion/LOT2.md`
- `app/src/androidTest/`

## Étapes

### Phase 1

Requalifier la correction lot2 READY provenant de NONE→UNAVAILABLE et eventId rejoué→UNKNOWN ; ne jamais réintroduire un READY du laboratoire comme vrai CORE.

### Phase 2

Développer des scénarios instrumentation sur émulateurs API26/35 : lancement Activity, conversation OFF, connexion absente, transitions READY fallacieuses, événements en retard, replay, redémarrage/rotation, arrêt/délai, affichage des erreurs, taille de texte, permissions et avatar sans parler ni charger un modèle.

### Phase 3

Tester une source simulée de capacités signées/invalides dans un banc, sans la traiter comme une preuve réelle d’UID ou de certificat. Le véritable transport vers CORE reste OFF. Ne jamais modifier l’émission des droits, stocker URI personnelles ni activer un service exporté.

### Phase 4

Ajouter une CI séparée avec exécution réelle des androidTests API26 et API35, non seulement leur compilation. Si la CI ne fournit pas d’émulateur, marquer NOT_RUN, préserver les tests existants. Vérifier 217 JVM + builds/lint, attention jank/batterie.

### Phase 5

Fournir capture synthétique non personnelle et matrice SDK/ABI/transport/rollback, PR brouillon vers cette branche. Aucune prétention de lip sync précis sans visèmes.

## Critères de réception
- Aucune régression, ancienne fonctionnalité, contrat gelé ou preuve historique supprimés.
- Tests au SHA de la PR et à la granularité JVM / Robolectric / Android émulateur / appareil physique, sans inventer les non exécutés.
- Distinguer fiche d'architecture, prototype et connexion de production; le statut d'intégration reste BLOCKED si manque autorité, backend ou appareil.
- Rapport : module, mission, branche, commit, tests avec totaux/échecs/sauts, blocages, fichier d'impact, rollback, prochaine action.
- PR brouillon uniquement. STOP et demande de décision pour toute opération sensible.

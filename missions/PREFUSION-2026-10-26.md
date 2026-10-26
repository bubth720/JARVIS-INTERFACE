# MISSION CODEX UI-PF-01 — Interface unique autonome, capacités fiables et avatar

Dépôt : bubth720/JARVIS-INTERFACE
Chemin : missions/PREFUSION-2026-10-26.md
Branche de distribution : mission/prefusion-gates-20261026
Base Git vérifiée lors de la préparation : 3c6921e2e40dd8e7c0756e07e854ce0bc1d9c5ff

## Règles obligatoires
Codex lit d'abord AGENTS.md si présent, la CI au SHA exact, les PR ouvertes et le code réel. Créer sa propre branche de développement à partir du HEAD de cette branche mission; préserver contrats, historique, données et tests. 100 % local hors ligne au runtime, sobriété Android, minSdk stable. Aucun merge main, changement architecture majeur, installation utilisateur, activation sensible, suppression de preuve ni prise de permission implicite. Chaque phase est un commit testable et réversible.

## Phases

### 1. Inventaire

Relire docs/fusion-02/TRANSPORT-REVIEW.md, REPORT.md, app/src/main/java/com/jarvis/interfaceapp/ui/chat/core/CoreConversationClientAdapter.kt, avatar/v2/AvatarExpressionStateMachine.kt. Fixer la référence et préserver tableau de bord, sauvegardes, personnalisation, avatar 2.5D.

Critère : code/diff ciblé, tests positifs et négatifs réellement exécutés, rapport SHA et rollback. Si besoin d'accord => livrer la proposition et s'arrêter.

### 2. Transport OFF par défaut

Tester session/request/ACK/chunks/stop borné/UNKNOWN et absence de données en cas de faux client. Préparer adaptateur vers CORE seulement après UID, certificat et permission d'application approuvés. Aucun statut CONNECTED affiché sans preuve; pas de Binder public inattendu.

Critère : code/diff ciblé, tests positifs et négatifs réellement exécutés, rapport SHA et rollback. Si besoin d'accord => livrer la proposition et s'arrêter.

### 3. Citations et personnalité

Afficher URI/hash/page/offset de façon inerte et avec permission explicitement vérifiée; ne jamais ouvrir un document privé au seul clic d'une référence non validée. Définir édition personnalité Athéna transactionnelle versionnée, aperçu avant/après, export et rollback local, aucun stockage de secret dans traces.

Critère : code/diff ciblé, tests positifs et négatifs réellement exécutés, rapport SHA et rollback. Si besoin d'accord => livrer la proposition et s'arrêter.

### 4. Avatar et perf

Préserver design 2.5D anime brun/bleu nuit; ajouter événements visuels IDLE/THINKING/SPEAKING/UNKNOWN, transitions bornées et modes économie batterie, pas de lip-sync prétendument précis sans synchronisation PCM réelle.

Critère : code/diff ciblé, tests positifs et négatifs réellement exécutés, rapport SHA et rollback. Si besoin d'accord => livrer la proposition et s'arrêter.

### 5. Validation

Exécuter tests JVM/Compose, instrumentés sur API26/35, mode avion, rotation et interruption et grand texte; relever mémoire/jank/batterie. Contrôler les contrats consommateurs avant PR et ne pas activer connexion sensible.

Critère : code/diff ciblé, tests positifs et négatifs réellement exécutés, rapport SHA et rollback. Si besoin d'accord => livrer la proposition et s'arrêter.

## Livrables et blocages
PR brouillon sur base correspondante, changelog technique, fichiers concernés, matrice dépendances, commandes et résultats tests (succès/échecs/skips/non exécutés), artefacts et SHA du commit, risques, retour arrière, prochaine action. Une réussite JVM ne constitue pas une preuve physique ni un GO Android. Aucun merge dans main.

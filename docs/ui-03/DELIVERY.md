# UI-03 — proposition de raccordement et banc local

**PARTIAL_CLIENT_PROPOSAL / REAL_CORE_BLOCKED.**
[Mission #4](https://github.com/bubth720/JARVIS-INTERFACE/issues/4) lue intégralement.
Branche `feat/ui-03-delivery`, base UI-02
`4842ef1f47ac2a1efc29e77408dbdb3ec42768bb`, qui conserve AVA-04 et les missions antérieures.
Le commit livré et le workflow exact sont indiqués dans le bilan final.

## Blocage vérifié avant programmation

Sources lues directement dans leurs objets Git, sans modification de dépôt externe :

- CORE-02 `e86107b8213b0aeaf9d22025cf6bef91a8e544a5` :
  `events/CoreEventBus.kt` dit explicitement metadata/no conversation text ;
  `events/ModuleConnector.kt` ne définit ni transport, permission ni service et
  refuse les connecteurs non autorisés. `events/CoreSessionEvents.kt` appartient
  au SessionCoordinator. L'adaptateur `app/src/mvp2/.../CoreConversationAdapter.kt`
  est interne au processus CORE, sans API externe.
- CORE-03 `aea5ac859ac551d54fe16187c1883a487dbee9d6` : sa livraison est un laboratoire
  JVM `integration-lab/`, avec véritables bibliothèques NEXUS/SERVICES mais **aucun
  raccordement Android**, aucun transport Binder et aucune inférence native qualifiée.
  Le rapport CORE-03 explicite ces limites ; l'APK reste inchangé par cette mission.
- UNIFIED UNI-01 `bd283dad3a57effa276f7f4441901839d2834d5c` et UNI-02
  `329f304115c9bc5b6dd6ccc2d68d19bf455b5e6c` : `contracts/v1/SEMANTICS.md` conserve
  cinq schémas proposés sans transport, payloads privés référencés, métadonnées sans
  prompt/URI, contrôle UID/certificat/grants par le futur transport, arrêt logique
  distinct de libération native. UNI-02 conserve ces schémas et ses gates.

Les branches publiées CORE et UNIFIED ont été inventoriées. Aucun texte n'est
fabriqué à partir d'un `ResponseCompleted`, d'un index de chunk ou d'une disponibilité.
CORE applicationId `com.stark.jarvislocal` a plusieurs suffixes de variante ;
aucun nom de variante ou certificat ne peut être présumé autorisé. CORE minSdk24,
INTERFACE minSdk26 restent indépendants ; aucun JDBC/SERVICES n'est ajouté à l'APK.

## Code concret livré

- `ui/chat/core/CoreConversationTransport.kt` : DTO de **proposition locale**, ACK
  et clé de contenu distincts du nonce de soumission et des baux JNI, limites UTF-8,
  UUID/générations/séquences, port indisponible par défaut. Les seules origines
  disponibles sont NONE et LABORATORY : aucun port CORE réel n'est fourni.
- `CoreConversationClientAdapter.kt` : flux corrélés, ACK avant contenu ou tampon
  pré-ACK borné, remise en ordre bornée, déduplication, refus de session/génération
  étrangère, contrôle de volume, terminal et réponse tardive, annulation logique,
  timeout ACK et UNKNOWN bloquant. Les métadonnées UI-01 ne créent jamais de texte.
  Les ressources/arrêt natifs ne sont pas attestés par ces tests.
- `CoreConnectionDiagnosticsScreen.kt` : état CORE réel NON CONNECTÉ, banc public
  RAM volontaire séparé du fil de chat. Les réponses de test sont explicitement
  étiquetées PAS UNE RÉPONSE IA. Aucun profil/mémoire/historique n'est persisté.
- `ChatScreen.kt` : entrée de diagnostic et Retour local, sans modifier le contrôleur
  de chat UI-01. Une Surface opaque corrige le même défaut de fond réellement
  observé sur l'écran de conversation, avec une couleur de contenu adaptée.
- `CoreConversationClientTest.kt` : scénarios de protocole local ;
  `CoreConnectionDiagnosticsTest.kt` : défaut sûr et absence de mélange de fixture
  dans le vrai fil, avec conservation des octets DataStore.

L'API Kotlin proposée n'est pas un codec wire ni un amendement UNIFIED. Elle sert
à qualifier les invariants de présentation et à préparer la revue du canal de
contenu. [Proposition concrète](CONTRACT-PROPOSAL.json). Aucun service exporté,
AIDL, bindService, recherche de certificat, permission ou signature n'est activé.
Le vrai transport et son adaptateur authentifié ne sont pas déclarés livrés.

## Propriété et limites

La soumission réelle, les UUID métier, la session, les grants d'outil et la mémoire
restent sous CORE. Aucun autre moteur IA, génération locale de voix ou copie du
CoreEventBus dans l'APK. Le fixture historique CORE-02 reste uniquement dans les
tests JVM, avec vérification d'empreinte et exclusion DEX. Aucun consentement lu
dans un texte, document ou résultat de test ne devient une autorisation d'outil.

Une réouverture de banc RAM n'atteste aucune réconciliation native. Les tests de
signatures inter-APK, authentification Binder, API35, appareils physiques et service
CORE réel restent **non exécutés**, car aucun transport approuvé et qualifié n'existe.
Aucune recherche NEXUS ni exécution CSV réelle n'est revendiquée ici : CORE-03 reste
un laboratoire JVM indépendant, sans pont Android. Le tableau de bord, l'avatar,
les réglages et les autres applications restent autonomes.

## Prochaine action soumise à revue

Faire approuver le canal de contenu distinct, fournir la contrepartie CORE, les
versions/variantes/certificats et grants autorisés, puis tester l'authentification
et les réponses réelles en laboratoire sous accord spécifique. La proposition
actuelle n'applique rien de cela. Aucun merge dans main, installation personnelle,
diffusion de média privé ou connexion sensible réalisée.

## Validation réellement exécutée

Sur la version livrée : `./scripts/check.sh connectedDebugAndroidTest --no-daemon`
réussit en 6 min 28 s : `assembleDebug`, `testDebugUnitTest`, `lintDebug`,
`assembleDebugAndroidTest`, puis tests connectés sur notre émulateur API26 x86_64
logiciel (720×1280, densité320, mode avion). **167 tests JVM et 55 tests Android,
0 échec, 0 erreur, 0 ignoré**. Lint : `No issues found.` Les 18 nouveaux tests JVM
et les deux nouveaux tests Android sont compris dans ces totaux.

[Résultats et empreintes APK locaux](validation/build-tests.json),
[JUnit Android](validation/android-results.xml), captures Compose réelles
[non connecté](validation/captures/disconnected.png) et
[banc volontaire](validation/captures/protocol-laboratory.png).
La capture du banc montre un texte public de test, jamais une réponse CORE.
Les captures ont été réalisées après le passage de la suite complète.
Les empreintes locales ne désignent pas des APK signés par la CI.

Les fichiers métier/DataStore, format JSON V1, thème, manifeste, dépendances,
contrôleurs du dashboard et moteur d'avatar ne sont pas modifiés par UI-03.
Les gardes de périmètre conservent ces invariants et excluent le fixture CORE du DEX.
Risque résiduel : futur transport et thread Android devront être qualifiés ensemble,
notamment reprise, authentification et arrêt natif ; le banc RAM ne les démontre pas.
Aucune performance physique mesurée.

# INTERFACE — FUSION-02

Module : INTERFACE + AVATAR. Mission [INTERFACE #6](https://github.com/bubth720/JARVIS-INTERFACE/issues/6),
[programme UNIFIED #5](https://github.com/bubth720/JARVIS-UNIFIED/issues/5).
Branche : `feat/fusion-02-interface` ; parent explicite
`1d0a89f3d92cba64122db08cc7b326239f85c08d` (`fix/athena-premium-default`).
Verdict : **PARTIAL — façade locale et laboratoire ; vrai chat et multimodule Android BLOCKED**.
Le HEAD publié et le lien de CI sont consignés dans le bilan de livraison ;
`git rev-parse HEAD` donne le SHA exact du checkout. Le rapport ne constitue aucun GO Android multimodule.

## Audit réellement effectué

Issues UNIFIED #5, INTERFACE #6 et ancienne UI-03 #4 lues intégralement. Aucun AGENTS.md
dans ce dépôt. Huit dépôts inventoriés par `git ls-remote`, en lecture seule,
avec références initiales puis actualisées après reprise : aucun changement distant
entre ces deux observations. CORE est seulement cloné dans un répertoire temporaire
pour lire sa conception à SHA épinglé ; aucune modification de dépôt frère.

Ancestry vérifiée : UI-03 `0869b3959d3636deab3c938e6cb98b7e555f19af` est ancêtre
d'INTERFACE-FINAL `db459ba388a3fea992d80320c3989be0edda8500`, parent direct du correctif
Athéna `1d0a89f3d92cba64122db08cc7b326239f85c08d`. Aucun cherry-pick nécessaire.
La branche par défaut est toujours `development/mission-002`, main absente.
Les détails de branches, SHA, empreintes et dépendances figurent dans
[PROVENANCE.json](PROVENANCE.json) et `evidence/remote-refs*.json`.

CI historiques inspectées via les pages run/job :

| Produit | Run | Observation exacte |
| --- | --- | --- |
| INTERFACE Athéna | 40214158554 | SUCCESS, référence de départ |
| CORE première tentative | 40215415459 | FAILURE, setup-android et upload de rapport |
| CORE relance | 40215604539 | SUCCESS ; ne prouve pas un transport INTERFACE |
| NEXUS port | 40115049468 | FAILURE, setup-android |
| SERVICES port | 40115305244 | FAILURE, étape régressions/API24-library ; cause détaillée des logs non établie ici |
| LLM FUSION-01 | 40214822563 | SUCCESS ; pas une preuve JNI/native |
| UNIFIED initial | 40214722427 | UNKNOWN dans le lecteur de pages : aucune étape exploitable ; aucun succès présumé |

Les échecs des autres propriétaires ne sont pas corrigés dans ce dépôt. Aucun port
NEXUS/SERVICES ni nouveau catalogue, Guard, SQLite, mémoire ou moteur LLM n'est embarqué.

## Fonctions livrées

### I1 — présentation des capacités

Studio → **Fusion** est une vraie page navigable. `CapabilityCards` présente CORE,
NEXUS_RAG, SERVICES_CSV, SERVICES_EXPORT, LLM, VOICE et AVATAR. Le défaut de production
est **NON_CONNECTÉ / UNKNOWN pour les sept**. Une application installée, un DTO,
un texte ou une fixture ne peut pas afficher une capacité prête.

L'évaluateur local `/2` reçoit un rapport et un vérificateur d'hôte injecté,
contrôle epoch UUID, génération, séquence anti-rejeu, version, fraîcheur et doublons.
Les capacités non rapportées restent UNKNOWN. Aucun vérificateur Android authentifié
n'est fourni : les tests positifs utilisent un vérificateur de test et n'attestent
ni UID ni signature. READY ne peut donc pas être activé depuis l'interface livrée.

### I2 — laboratoire document/outils

Ouverture volontaire, RAM seulement, bannière **LAB UNIQUEMENT**. Source synthétique,
URI fictive non ouvrable, version/page/offset/hash vérifiables sur la fixture locale ;
la sélection SAF est simulée et explicitement nommée comme telle. Calcul décimal
local `0.10 + 0.20 = 0.30`, sans prétendre appeler SERVICES ou NEXUS.

Aperçu puis confirmation exacte : opération, génération, octets, digest, nouvelle
destination fictive et expiration 30 secondes. Token consommable une seule fois,
refus de modification/périmption/révocation/double confirmation. Annuler ne commet
rien. ACK perdu -> UNKNOWN sans retry ; révocation après engagement ne promet pas
un rollback. Les reçus COMMITTED sont **simulés et libellés LAB, zéro fichier écrit**.
Aucun ContentResolver, fichier externe, permission, consentement CORE ou journal
métier concurrent n'est créé. Ce modèle UI n'est pas une fence de sécurité SERVICES.

La rotation/recréation conserve le brouillon par ViewModel ; une mort du processus
ferme le laboratoire sans rejouer de commande. Aucun nouveau stockage ni migration.
Le corps de confirmation est défilable en fenêtre courte, avec statuts liveRegion
et libellé accessible de l'option ACK perdu.

### I3 — client séparé et correction démontrée

Réemploi du client UI-03, aucun second contrôleur de conversation. Deux tests ont
démontré un arrêt bloqué indéfiniment, avant/après ACK. L'arrêt est maintenant borné
à cinq secondes, puis UNKNOWN bloque un nouvel envoi. L'ACK d'arrêt ne signifie
jamais modèle déchargé. Tests initiaux en échec conservés, pas neutralisés.

Le budget local optionnel `Fusion02Review` applique les limites proposées par CORE
(question UTF-8 1024 octets, chunk 4096), sans changer le banc historique v1 par
défaut. Le dépassement refuse ou bloque, jamais tronqué. Aucun codec/transport
commun n'est inventé. [Revue et conditions d'activation](TRANSPORT-REVIEW.md).

### I4 — Athéna préservée

Les ressources, moteurs V1/V2, hôte premium, import privé AtomicFile et fallback
explicite sont inchangés. Le build privé garde le portrait premium principal ; le
build public continue d'exiger son import. Aucun portrait ou référence utilisateur
n'est ajouté au Git ni aux artefacts publics. AVATAR dans la façade désigne une
future capacité hôte, distincte du portrait local déjà visible.

[Demande du pack articulé](ATHENA-ARTICULATED-PACK.md) : calques/pivots/occlusions,
paupières et visèmes réellement séparés, provenance, mapping et budgets. Les
portraits existants ne deviennent pas un rig ni un lip-sync par cette mission.

## Fichiers / responsabilités

- `fusion/CapabilityPresentation.kt` : rapports de présentation, refus par défaut.
- `fusion/DocumentToolLaboratory.kt` : état RAM et décisions synthétiques uniques.
- `fusion/FusionScreen.kt` : composants Compose, citation et confirmation.
- `workspace/WorkspaceScreen.kt` : nouvel onglet, autres pages conservées.
- `ui/chat/core/CoreConversationClientAdapter.kt` : délai stop et budget optionnel.
- Tests `fusion/` JVM/Android et `CoreConversationClientTest.kt` : nouvelles preuves.
- `scripts/check_fusion02.py`, deux gardes existants : gel renforcé au parent premium,
  sauf trois fichiers explicitement modifiés ; manifeste, DataStore, art, dépendances gelés.
- Workflow : contrôle FUSION et artefact de preuves synthétiques publiques ; README,
  présent dossier : provenance, réception, risques et PR proposée.

## Validation et incidents

La compilation initiale publique, tests JVM et Lint ont réussi. Le total final
attendu est vérifié dans les reçus réellement produits ; les résultats définitifs
figurent dans [VALIDATION.json](VALIDATION.json).

- Preuve rouge de départ : deux tests stop échoués sur l'ancien client, puis corrigés.
- Première campagne Android : 41 tests exécutés, 40 réussis, un crash Compose
  `SlotWriter.moveSlotGapTo` lors de la destruction de l'ancien écran de comparaison
  V2 ; campagne interrompue, 32 scénarios non exécutés. Trace XML conservée.
  Cet écran et le moteur n'avaient pas été modifiés. Les trois tests V2 ont réussi
  lors de l'investigation ciblée ; reprise complète effectuée séparément. Le crash
  n'est pas effacé du bilan et aucune correction interne Compose n'est revendiquée.
- Investigation ciblée : huit tests, cinq réussis et trois erreurs de test Fusion
  (snapshot d'un fichier DataStore légitimement absent au premier lancement ;
  performScrollTo appliqué à des boutons de dialogue sans parent défilable).
  Corrections : vérifier l'absence inchangée du fichier et cliquer directement les
  boutons modaux ; aucune assertion métier supprimée.
- Les captures publiques de cette mission montrent seulement les écrans synthétiques
  Fusion ; aucun portrait n'est affiché sur ces écrans ni capturé dans ce dossier.
- Compilation d'un APK instrumenté n'est pas comptée comme exécution Android.
  Aucune validation physique, consommation, CPU/RAM ou transport signé revendiquée.

## Blocages, risques et prochaines actions

| Lot | Blocage / action du propriétaire |
| --- | --- |
| Vrai chat / transport | Accord distinct sur protocole/version, UID/paquet/certificat/grant, serveur CORE et instrumentation d'authentification ; aucune activation ici |
| Documents/outils réels | NEXUS FTS5/API24 et SERVICES SAF/fence/relecture non qualifiés par nos preuves ; attendre produits et autorisations |
| Rig/lip-sync | Calques et visèmes approuvés absents ; timing VOICE non qualifié |
| Appareils | POCO X8 Pro, Redmi Pad SE, lecteur TalkBack réel et mesures physiques non accessibles |
| API24/25/35 | Tests UI INTERFACE API26 seulement ; minSdk26 conservé, aucune qualification héritée d'une autre API |
| Mort du processus | Aucun vrai traitement propriétaire à réconcilier ; le laboratoire ferme et ne rejoue rien, pas de reçu durable métier revendiqué |

Risque résiduel : bug interne Compose observé lors de la première campagne et futur
transport encore non qualifié. La confirmation LAB ne doit jamais servir de grant
réel. Le rapport de capacité n'est qu'une projection et le futur adaptateur doit
posséder l'autorité et les watermarks. Les anciens modules, profils, historique,
personnalité, disposition et art restent préservés par tests et contrôles de portée.

Prochaine action : revue de la PR brouillon et des captures locales ; faire livrer
les gates producteurs et approuver séparément le transport avant tout vrai chat.
Aucune fusion main, activation sensible, installation personnelle, migration ou
publication d'art privé. Aucun ANDROID_MULTI_MODULE_GO.

## Publication / PR

Les commits sont destinés uniquement à la branche dédiée. La création distante
via GitHub CLI/API est bloquée : `gh api repos/bubth720/JARVIS-INTERFACE` renvoie
Forbidden et le proxy refuse CONNECT vers api.github.com avec HTTP403. La lecture
et le push Git HTTPS restent disponibles. Aucun token extrait ni route contournée.
Le [texte de PR brouillon](PR-DRAFT.md) est prêt ; la PR n'est pas déclarée créée.
Après publication, ouvrir la comparaison
[base premium → branche FUSION](https://github.com/bubth720/JARVIS-INTERFACE/compare/fix/athena-premium-default...feat/fusion-02-interface?expand=1)
et choisir **Create draft pull request**. Aucune PR non brouillon ou fusion autorisée.

## Résultats locaux définitifs

Code validé : `5534f2593108fa062a1b4b9b1beff23ebc91a1fd` ; les commits ultérieurs
portent uniquement sur la documentation et les preuves de réception.

`./scripts/check.sh connectedDebugAndroidTest -PathenaPrivateAssets=PRIVATE_ASSETS --no-daemon`
termine en **8 min 2 s** : **215 tests JVM, 73 tests Android, zéro échec/erreur/ignoré**.
Lint : `No issues found.` Les 73 comprennent les 68 scénarios historiques et les cinq
nouveaux scénarios Fusion. Les trois tests historiques V2 ont donc réussi dans la
reprise complète ; le crash initial reste une limite observée, pas un défaut corrigé
par modification de moteur. **9 tests Python du packager réussis.**

Les quatre tâches de build demandées sont PASS, APK debug et APK instrumenté présents.
Émulateur API26 logiciel, mode avion vérifié ; aucun téléphone. Contrôles de portée :
105 fichiers existants protégés, DataStore V1 inchangé, fixture CORE absente du DEX.
APK minSdk26 / target35 vérifié au binaire, pas de permission INTERNET. Les bibliothèques
Kotlin/Compose/Material/DataStore et le wrapper restent exactement ceux du parent.

| Lot | Verdict de cette livraison |
| --- | --- |
| I0 | PASS : ancestry, branche, provenance et gel |
| I1 | PASS façade locale ; aucune preuve distante de production |
| I2 | PASS laboratoire explicite ; SAF/outils réels BLOCKED |
| I3 | PASS client passif/tests/arrêt borné ; transport signé BLOCKED |
| I4 | PASS préservation privée ; rig/lip-sync BLOCKED |
| I5 | PASS suites émulateur et JVM ; validation physique/TalkBack réel NOT_RUN |

Les éventuelles réexécutions de capture ne sont pas additionnées au nombre de tests
uniques de la suite. `VALIDATION.json` distingue campagne finale et incidents initiaux.

### Captures et accessibilité de laboratoire

[Galerie synthétique](evidence/captures/index.html),
[non connecté](evidence/captures/disconnected.png),
[ACK perdu](evidence/captures/unknown.png),
[portrait après rotation](evidence/captures/portrait.png).
Ces captures viennent de l'application réelle, aucun art privé n'y est visible.
Campagne de capture : 5/5 réussis, 79,692 s, sans addition aux 73 tests uniques.
Préparation initiale refusée car l'APK instrumenté avait été retiré par le nettoyage
Gradle ; réinstallation sur notre émulateur puis reprise, aucun test compté sur
cette erreur de préparation. Trace conservée.

Texte système à **130 %**, rotation portrait/paysage, confirmation/annulation et
sémantique de contrôle : un scénario supplémentaire exécuté, **1/1 réussi** en
37,33 s. Échelle restaurée ensuite. Cela ne constitue pas un essai du lecteur
TalkBack : cette validation et les appareils physiques restent NOT_RUN.
Le build public sans pack privé réussit en **56 s**, absence du ZIP privé vérifiée
après le build privé. Les APK publics et privés ne sont pas interchangeables comme
preuves de présence des portraits.

### Véritable arrêt de processus

[evidence/process-restart.json](evidence/process-restart.json) : PID4110 puis4331,
arrêt forcé vérifié, laboratoire fermé après retour dans Studio/Fusion, aucune
commande rejouée, portrait premium prêt et octets DataStore identiques. Le fichier
dashboard JSON V1 a la même empreinte ; workspace-v1.json était absent et reste
absent (aucune préférence non initiale enregistrée dans cette fixture).
Deux tentatives de sonde ont corrigé leurs préconditions : faire défiler le statut
LAB hors fenêtre, et traiter correctement l'erreur `cat` d'un fichier optionnel
absent renvoyée sur stdout par adb. Traces conservées ; aucune erreur de stockage
applicative ni migration introduite. La preuve finale reprend le laboratoire déjà
ouvert avant un nouvel arrêt forcé réel ; elle ne confond pas recréation et mort du
processus. Les logs/scénarios intermédiaires ne sont pas comptés comme tests réussis.

# UI-PF-02 — consommateur Android public, lot 3

Branche `codex/prefusion-lot3-interface`, issue de la branche mission au SHA
`c14eebdbce8b2fda024043d6abd1e84bc719f113`. Le lot 2
`4bc1993d4b6525b248f2b12e95a18a279a3d9fdc` est préservé. Aucun merge.
Le SHA de livraison est celui du commit contenant ce rapport ; la preuve publiée
ne prétend pas inclure un SHA autoréférent ni une CI encore non observée.

## Incrément livré

Deux classes **androidTest seulement**, sans modification du runtime :

- `PrefusionConsumerBoundaryTest` : onze cas sur le consommateur Kotlin existant,
  exécutables sur le Main dispatcher Android. NONE→READY refusé, doublons et rejeu,
  corrélation de session, résultats tardifs, annulation avant ACK, délai stop réel de
  cinq secondes, UNKNOWN non réinitialisable par reconnexion, limites UTF-8/chunks,
  séquences manquantes, fermeture et nouvelle session vide.
- `PrefusionOffUiTest` : deux cas Activity/Compose, écran OFF, rotation/recréation,
  diagnostic absent, aucune réponse créée, avatar non chargé. La capture PNG ne
  provient que de cette scène publique sur émulateur de test ; aucune image
  synthétisée n'est présentée comme une capture exécutée.

Le banc de capacités signe le rapport exact avec une clé RSA **éphémère de test**.
Altération, origine LAB, version inconnue, epoch, fraîcheur et rejeu sont refusés.
Le succès de cette signature de fixture ne démontre ni UID Android, ni certificat
APK, ni transport réel. Un vérificateur Android de confiance reste absent et OFF.

La CI séparée `prefusion-lot3.yml` lance les JVM/builds/Lint puis réellement les
androidTests publics sur API26 et API35. Le script refuse tout appareil non
`emulator-*`, contrôle l'API, valide les XML (aucun échec/erreur/saut), effectue un
force-stop/redémarrage et conserve résultats et capture publique. Aucun accès à
un téléphone personnel, art privé, audio, modèle ou URI utilisateur.

## Validation réellement obtenue le 28 octobre 2026

- `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
  --no-daemon --max-workers=2` : **BUILD SUCCESSFUL**. Première compilation complète
  15 min 20 s ; reprise de vérification 41 s, 76 tâches (1 exécutée, 75 à jour).
- JVM : **217 tests, 0 échec, 0 erreur, 0 saut**, 15 fichiers XML. Tests historiques
  inchangés. Les 13 nouveaux tests Android sont compilés, pas comptés parmi les JVM.
- Lint Debug : zéro issue dans le rapport XML.
- Garde lot3 : **114 fichiers existants préservés octet pour octet** (production,
  tests, dépendances, configuration et ressources historiques).
- Gardes FUSION-02, UI01, AVA04/distribution publique et syntaxe Bash : voir résultats
  consignés avec la livraison ; aucune nouvelle permission ni dépendance runtime.
- Instrumentation API26/API35 **NOT_RUN localement** : `/dev/kvm` absent.
  La CI est préparée ; ses résultats doivent être lus au SHA publié avant réception.
- Sélection publique : **22 cas par API** (13 nouveaux + 9 existants), puis deux
  vérifications OFF après redémarrage. Aucun résultat anticipé. Les classes qui
  nécessitent un pack privé restent intactes et NOT_RUN dans cette campagne publique.
- Appareil physique, performance/jank/batterie et capture : **NOT_RUN**. Aucune
  nouvelle mesure de fluidité ou preuve de lip-sync.

Les empreintes des XML JVM exécutés figurent dans `VALIDATION.json`. Les fichiers
XML et rapports complets sont produits dans `app/build/` et archivés par la CI ;
les sorties locales de construction ne constituent pas une preuve de CI distante.

## Matrice de compatibilité et responsabilité

| Élément | État | Condition avant activation |
|---|---|---|
| Android | minSdk26, compile/target35 inchangés | essais émulateurs API26/35 puis qualification autorisée |
| ABI | aucune nouvelle bibliothèque native | ne pas déduire une compatibilité ARM physique des JVM/x86_64 |
| Consommateur UI | contrat local UI03/FUSION02 conservé | négocier un codec wire commun, sans mapping silencieux |
| Producteur CORE | port lot3 expérimental séparé | authentifier source réelle et revoir correspondance de champs |
| Identité | RSA de fixture uniquement | UID observé, paquet/certificat épinglé et grant révocable approuvés |
| Autorité | CORE décide ; UI n'accorde aucun droit | contrôles au commit chez propriétaire de l'effet |
| Stockage/avatar/voix | préservés, non activés par ce lot | qualification spécifique ; aucune preuve JNI issue de ces tests |
| Intégration | **BLOCKED / transport OFF** | autorisation distincte et preuves d'identité, backend et appareil |

Les métadonnées seules ne créent ni assistant, ni citation, ni résultat documentaire.
L'origine LAB peut alimenter des tests isolés ; elle ne transforme pas un lien NONE
en connexion CORE de production. Le travail côté CORE et cette suite consommateur
ne constituent pas un raccordement inter-APK démontré.

## Impact, retour arrière et suite

Ajouts : deux classes androidTest, workflow CI isolé, scripts garde/exécution et ce
rapport avec sa preuve JSON. Aucun fichier applicatif, manifeste, signature,
permission, dépendance, contrat gelé, actif ou donnée historique modifié.

Retour arrière : revert du commit de lot3 ; aucune migration, préférence ou base
produit à restaurer. Les tests historiques ne sont pas désactivés.

Prochaine étape : publier la branche sans force, consulter la nouvelle CI au SHA
exact, corriger tout échec réel, puis soumettre une PR **brouillon** vers
`mission/prefusion-lot3-20261027`. Le transport réel et toute activation sensible
restent soumis à une décision distincte. Aucune fusion dans main.

## Publication et premier résultat distant

Le commit `a1d9db6cf31c41256f96508147606029286ca343` a été publié sans force et
son SHA distant vérifié. Workflow Android historique
[40664650732](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/40664650732)
**SUCCESS**, 4 min 45 s. La nouvelle campagne
[40664650866](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/40664650866)
a signalé **FAILURE API26** à l'étape d'exécution émulateur/tests (5 min 57 s),
annotation `/usr/bin/sh failed with exit code 1`. Aucun total de tests Android
n'est déduit de cette annotation. Les logs détaillés et XML d'artefact ne sont
pas accessibles anonymement dans cet environnement ; la page demande une connexion.

Une amélioration de diagnostic est ajoutée : résumé public borné des XML de tests
synthétiques dans le résumé du job et annotations. Six tests Python de ce lecteur
passent, y compris XML absent/corrompu et échec conservé. Aucun test Android n'est
désactivé, aucun échec n'est converti en succès. La cause de l'échec reste non
établie tant qu'une sortie reproductible n'est pas accessible. Le workflow API35
était encore en cours lors de cette observation.

La commande de création de PR brouillon a échoué :
`Post "https://api.github.com/graphql": Forbidden`. Aucun contournement ni nouvelle
tentative API ; branche et corps de PR préservés. Ouvrir la comparaison vers
`mission/prefusion-lot3-20261027`, puis **Create draft pull request**, sans fusion.

Dernière observation à 09:46 UTC : la campagne au commit
`0cbde77dc36f5537af0442c077b4f7d4acfd9eaa`, run
[40665765400](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/40665765400),
est **FAILURE** sur API26 et API35, 7 min 25 s au total, même annotation shell.
Le workflow historique Android au même SHA est **SUCCESS** (4 min 30 s,
[40665765393](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/40665765393)).
Le résumé Markdown de job n'est pas rendu sur la page anonyme accessible.
Les comptes XML sont donc aussi exposés comme annotation de diagnostic bornée,
y compris lorsque les XML sont absents. Aucun résultat de test n'est corrigé
par hypothèse ; les deux échecs restent des preuves historiques conservées.

Le diagnostic de runner conserve désormais un jalon borné et le code de sortie
initial dans `stage.json` via un trap EXIT : validate_emulator / instrument /
check_xml / restart / capture / complete. Le résumé n'accepte que ces valeurs et
un entier de sortie ; fichier absent ou invalide signifie
`NOT_STARTED_OR_NO_REPORT`, jamais succès. Aucun environnement, logcat ou contenu
privé n'est publié. Les **neuf tests Python** du lecteur passent ; un essai shell
avec adb de fixture non-émulateur confirme refus avant instrumentation, sortie 2
et journal `validate_emulator/2`. Ce test de runner n'est pas un essai Android.

## Correction du laboratoire après les preuves de redémarrage

La campagne [40667162403](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/40667162403)
au commit `cc5d860f91c0c84c3513419cf3c80ffa0354bfd4` publie, **sur API26 et API35**,
`reports=1, tests=22, failures=0, errors=0, skipped=0`, puis
`stage=restart, exit_code=1`. Les 22 cas Android réussissent donc sur chaque API;
la campagne entière échoue ensuite et n'est pas déclarée réussie.

Le runner présumait que les APK installées par `connectedDebugAndroidTest`
restaient installées pour l'expérience séparée de redémarrage. Cette hypothèse
n'est pas garantie par AGP : inspection statique du JAR **AGP8.9.2**, SHA256
`6c313d24d07ac393cfc7db5d63c5029e7d20b278781c42b2d22adf9bcc7dba30`, avec
`javap -c -p` : `SimpleTestRunnable` appelle `uninstall` pour test et application;
`UtpConfigFactory$createApkInstallerPlugin$2` configure `setUninstallAfterTest`.
Ce constat statique ne reconstitue pas les logs protégés de l'ancien run.

Correction limitée au script de laboratoire : après la vérification des XML,
réinstaller explicitement les deux APK publiques déjà compilées, vérifier les
paquets et l'enregistrement de l'instrumentation, puis force-stop/redémarrer et
relancer les deux tests OFF existants. Aucun test ou assertion Android modifié.
Chaque commande ADB après sélection utilise `-s` avec le numéro d'émulateur validé;
`ANDROID_SERIAL` est également fixé à ce numéro pour Gradle. Le script refuse un
numéro physique ou une API différente avant installation. Aucun appareil réel,
aucune désinstallation ou donnée personnelle impliqué.

Validation locale du correctif : **13 tests Python réussis, zéro échec/erreur/saut**
(9 diagnostics +4 régressions du runner). Le banc hôte simule volontairement le
nettoyage des APK par Gradle, vérifie leur réinstallation sur l'émulateur choisi,
le refus d'un appareil physique/d'une mauvaise API et la conservation d'une erreur
d'installation (sortie17, aucune ouverture d'application). Ces tests de shell
ne sont **pas** des essais Android ni des captures. La garde des114 fichiers
historiques, la syntaxe Bash et `git diff --check` réussissent. La nouvelle phase
`prepare_restart` est bornée dans le lecteur des diagnostics.

Les217 JVM et les builds/Lint du code applicatif inchangé sont déjà vérifiés aux
commits précédents; une nouvelle CI au SHA du correctif doit confirmer le parcours
complet22+2 et la capture. Aucun succès de cette nouvelle campagne n'est anticipé.

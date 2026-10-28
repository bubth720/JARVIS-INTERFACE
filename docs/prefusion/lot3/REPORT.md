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

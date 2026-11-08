# M51-R1 — livraison du diagnostic, qualification BLOCKED

Module INTERFACE, dépôt `bubth720/JARVIS-INTERFACE`, branche `codex/m51-r1-android-stability`, [PR20 brouillon](https://github.com/bubth720/JARVIS-INTERFACE/pull/20), basée sur la branche historique M51. **Le dossier est livré ; la mission n'est pas acceptée.** Aucun laboratoire essayé n'a permis l'admission des tests applicatifs. Aucun correctif applicatif n'est déclaré démontré et aucune APK prête à installer n'est livrée.

## Sources exactes

| Étape | Commit / tree | CI |
|---|---|---|
| Entrée PR18 | `9c8960a7c9d3f572dff41fe18a5b002c9f66ac8a` / `39427d75257a841208e6e75e2f1755d1cb564d85` | [42456346025](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42456346025) |
| D1 Linux/TCG | `1fe0b9adc8554b0e144a9efe7f1075daeba6a192` / `1ae32cb16efacea19d2f215a369d320c98bbac85` | [42513995917](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42513995917) |
| Q1 Intel/HVF réellement compilé et essayé | `d0e9219b205fbf89fd7dd346ab26aacd03a8f62d` / `ee08be7a69789b0578af1d9c7ec7e63621d2b4cf` | [42515139639](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42515139639) |

Rapports d'entrée relus : UNIFIED21, réception `a7625a31513be5c56fa36ed946fbdcd3c23c2a15`, FINAL-ACCEPTANCE, comptes et journaux bruts RAW-9C8960A. La réception documentaire finale a son propre commit à la tête de PR20 : il ne remplace pas le SHA exécuté Q1. La liste des fichiers et empreintes du diff exécuté est [SOURCE-FILES.json](SOURCE-FILES.json).

## Résultats Android séparés

Une tentative de boot par API et campagne ; au total deux boots par API, sans retry. Même AVD 2 cœurs / 2048 Mio, délai de boot configuré 300 s, assertions 15/20 s et budget instrumentation 900 s inchangés. Les runners Intel standards de ce dépôt public sont gratuits. Aucun droit KVM, groupe, udev ou protection modifié.

| Campagne | Résultat observé | Installation JARVIS / tests applicatifs |
|---|---|---|
| D1/API29 | BLOCKED_ENVIRONMENT : budget mural du préflight 120 s dépassé, 8 relevés | Aucune / NOT_RUN |
| D1/API35 | FAIL_DIAGNOSTIC : assertion Wi-Fi 0 immédiate échouée | Aucune / NOT_RUN |
| Q1/API29 | BLOCKED_ENVIRONMENT : crash SystemUI avant JARVIS | Aucune / NOT_RUN |
| Q1/API35 | BLOCKED_ENVIRONMENT : boot non terminé | Aucun script applicatif lancé / NOT_RUN |

D1/API29 : géométrie 1080×1920/420 vérifiée, launcher visible ; le dépassement du protocole ne démontre pas un ANR. D1/API35 :1200×1920 / 240 vérifié ; l'assertion immédiate ne conservait pas la valeur Wi-Fi réellement lue. Délai de stabilisation ou autre état : UNKNOWN. Ce défaut d'observation du banc reste documenté, sans le présenter comme défaut applicatif.

Q1/API29 : à 19:36:31.075, PID 2104, thread SysUiBg, `java.util.ConcurrentModificationException`, `HashMap$HashIterator.nextNode` puis `TunerServiceImpl.clearAllFromUser` / `upgradeTuner`. Événement am_crash et buffer fatal concordants, [copie brute](evidence/Q1-api29-crash-buffer.txt). Les paquets JARVIS étaient encore absents. À19:37:23.546, crash supplémentaire sdksetup : `Timezone: Unknown/Unknown is not an Olson ID`. Le runner signale aussi un `/etc/localtime` incompatible. Aucun lien causal entre le fuseau et le crash SystemUI n'est démontré. Le screenshot sauvegardé montre ensuite le launcher ; il n'annule pas le crash enregistré. Arrêt de la campagne concernée, aucune nouvelle installation.

Q1/API35 : HVF réellement détecté disponible sur l'hôte, mais `Timeout waiting for emulator to boot.` dans le job128837590875. L'archive 500 octets ne contient que le probe hôte ; elle ne constitue pas un rapport Android. Cause précise du blocage de boot UNKNOWN. La disponibilité de l'accélérateur ne prouve pas la stabilité d'Android.

Le helper commun conserve dans la preuve Q1 `environment/diagnostic.json` le libellé interne R1-D1 : son SHA et le rapport parent `qualification.json` identifient Q1. Le champ android_booted=false du probe décrit l'observation avant lancement de l'émulateur. Les preuves brutes ne sont pas réécrites.

## Causes initiales et audit

| Incident d'entrée9c8960a | Cause actuelle / preuve manquante |
|---|---|
| SnapshotStateObserver, ancien thread260 et courant main | UNKNOWN ; identité et pile du second thread, reproduction sur Android stable |
| Fixture DocumentsUI non sélectionnée en 20 s | UNKNOWN ; observation réelle MediaStore, octets et fenêtre du sélecteur |
| Navigation, timeout 15 s | UNKNOWN ; route affichée et état de fenêtre lors d'une reproduction isolée |
| API35, sortie processus et dialogue SystemUI | Cause initiale UNKNOWN ; Q1 ne démontre aucune causalité rétroactive |

L'entrée API29 avait 18 cas prévus, 12 commencés, 9 PASS / 3 FAIL puis crash, 6 non commencés. API35 n'avait commencé aucun cas. Ces comptes restent ceux de l'entrée, pas de R1.

Les sources exactes Compose 1.8.0 ont été examinées et hashées : [SOURCE-AUDIT.md](SOURCE-AUDIT.md), [DEPENDENCY-SOURCES.json](DEPENDENCY-SOURCES.json). captureToImage utilise redraw posté et PixelCopy avec callback main ; envelopper aveuglément tout cet appel bloquant sur main risquerait un interblocage. performSemanticsAction utilise déjà runOnUiThread. Aucun patch de dessin ou upgradeCompose arbitraire.

Le test SAF conserve la sélection réelle, le droit persistant et le contrôle du fichier original. Deux observations en lecture sont ajoutées dans une copie générée ; leur retrait restitue la source originale octet pour octet. Leur influence temporelle n'est pas qualifiée. Le sampler de threads est opt-in, borné, ne dessine pas ; Q1 ne l'active pas. Aucun de ces diagnostics n'a été exercé sur Android puisque le préflight a refusé l'admission.

## Tests et CI exacts

Q1 : 17 Python PASS, zéro ignoré. JVM 6 historiques, 15 baseline et 15 candidat PASS : 15 cas uniques au total dont 9 M51, sans additionner les doublons. Compilation des deux APK et deux testAPK PASS. Lint 0 erreur / 41 avertissements conservés. Signatures et empreintes vérifiées.

CI dédiée Q1 : r1_build SUCCESS, API29 etAPI35 FAILURE ; les jobs de l'autre phase sont SKIPPED par sélection de campagne. D1 : build et probe SUCCESS, deux diagnostics FAILURE. Les CI historiques [42515139622](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42515139622) et [42515143877](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42515143877), build/preview SUCCESS, ne qualifient pas les appareils M51.

Les trois cas isolés, 18 baseline puis 24 candidat, force-stop/restart, calendrier/projets/finances/navigation/DocumentsUI, hors-ligne applicatif et fenêtres de frames restent NOT_RUN dans R1. Activer le mode avion au préflight ne valide pas l'application. Fluidité, mémoire, latences et comparaison avant/après : NOT_RUN, aucune absence remplacée par zéro. Physique, LLM et Android16K dynamique : NOT_RUN.

Les derniers ajouts sont exclusivement documentaires et copies de preuves ; leur commit `[skip ci]` évite une nouvelle campagne après l'arrêt obligatoire. Il n'a pas de qualification Android propre. Aucun échec historique n'est effacé ou relancé.

## APK compilées, non qualifiées

[Archive de build Q1](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42515139639/artifacts/13630927616), ZIP SHA256 `88081b79151620961757477d75a0ba9bd2296f3da6cd6eac4dbaece3a4419323`.

| Fichier | SHA256 |
|---|---|
| M51-baseline.apk | `94e3010b925b9da663099d45d7fd54cbdce1fb7f076018a05ee1d9b7830cf5bd` |
| M51-baseline-tests.apk | `29d15ad9a42b1be3e887dfcb552fa15cab93779e0438b0c3382f13e31f8308bb` |
| M51-candidate.apk | `52aa2376a14b480439f0de58164f3c27205a62e11a39890121e61abda355c409` |
| M51-candidate-tests.apk | `b72b6775b6279af2f08b127d3728ad79ea3b4f55a2d747d41c7aa0c00e61731e` |

Paquets fr.jarvis.v1.lab.baseline / fr.jarvis.v1.lab, testOnly=true, allowBackup=false, version 51, min 26 / target 35. Signature v2 vérifiée ; certificat debug Q1 `63518cc50eac5c052ed5bbc84cf3a4c66d6d72f8ebf4c62f3b111312e2575f15`. Il diffère de D1. Aucune permission plateforme ; receiver interne AndroidX protégé par signature. Seul helper natif AndroidX graphique, quatre ABI identiques entre variantes, aucun JNI LLM.

## Préservation, archivage et réception

Deux fichiers existants modifiés : workflow M51 et build.gradle.kts du laboratoire (ajout test-only opt-in). Tous les autres ajouts sont dans m51/r1. Les 89 fichiers UI-01C et 113 fichiers M51 hors ces deux configurations restent identiques : sources applicatives, tests maintenus dans app/, SQLite, contrats, dépendances et ressources. Les receipts exacts de CI sont copiés dans [evidence/](evidence/).

[RECEIPTS.json](RECEIPTS.json) contient les sept archives vérifiées et comptes exacts. Les copies brutes essentielles sont versionnées avec [INDEX.json](evidence/INDEX.json). Les extraits de jobs sont explicitement marqués EXCERPT, avec numéros de lignes et SHA du journal intégral. Sauvegarde intégrale des archives et captures : `/workspace/m51-r1-20261108/BACKUP`, manifeste local vérifié ; ce workspace n'est pas un hébergement durable. Télécharger les artefacts GitHub avant leur expiration du 6 février 2027. Les logs essentiels versionnés restent accessibles après expiration.

Risques résiduels : régression Android non exclue, observateur Compose non diagnostiqué, SAF non observé en exécution, API35 non admis, listener non validé sur appareil, aucun benchmark recevable. Aucune permission, limite thermique/batterie/mémoire, donnée personnelle, module externe ou JNI changé. Aucun autre dépôt source modifié ; bilan transmis à UNIFIED21 par commentaire.

Compilation/preuve/préservation satisfaites ; résolution des incidents et réception 18/24 non satisfaites. **M51-R1 reste BLOCKED, non acceptée.** Les causes environnementales prouvées pour Q1 ne suffisent pas : aucun autre laboratoire n'a encore permis une qualification fiable. [NEXT-LAB.md](NEXT-LAB.md) prépare la suite sans nouvelle tentative. [ROLLBACK.md](ROLLBACK.md) : abandon de branche et omission de l'option R1, sans restauration de données historiques. Aucune fusion ni installation personnelle.

# Rapport M51 / INTERFACE

Mission M51 lots02–06/08 ; dépôt bubth720/JARVIS-INTERFACE, branche codex/m51-v1-foundation, base0dfeef8ff09d60a68d18398ccb6b2530f3b87df4/tree57ea00745ddfb0bc3c992c18ba4b61240c85706b. Le commit/tree final exécuté, CI, APK/certificats/SHA, captures et résultats bruts sont publiés dans le complément de la PR après lecture des artefacts ; un fichier de workflow n'est pas une preuve de campagne.

Avant : cockpit UI-01C séparé, huit espaces locaux et stockage déjà implémentés ; aucun module externe raccordé. Après candidat : hôte testOnly distinct, même code métier et ressources, panneau Modules & état honnête, export volontaire technique, réduction des mouvements et traitement du pulse aligné sur frames. Variante baseline comparable disponible ; les89fichiershistoriques restent figés. Les ajouts et leurs hashes sont listés dans le diff et source-receipt.json.

Tests locaux de préparation : premier build bloqué par le sandbox/écriture ~/.android puis dépendances absentes en offline. Corriger les répertoires Android vers le workspace et provisionner les mêmes dépendances officielles a permis compilation baseline/candidat, testsJVM et testAPK ; aucune protection système modifiée. Ces premiers APK de développement ne sont pas les APK finales du commit CI. Les dernières additions FrameProbe/contrôles sont qualifiées par la campagne CI du head exact.

Critères à juger séparément :
- compilation/testOnly/applicationId/certificat/permissions/hashes : PENDING CI finale ;
- historique UI01C préservé : contrôle statique ; régressions appropriées à rejouer au head ;
- navigation/CRUD/persistance/recréation/force-stop/SAF/modeavion : PENDING Android, puis résultats exacts ;
- mesure UI avant/après : PENDING, sans gain présumé ;
- fidélité artistique, TalkBack humain/physique, stockageENOSPC et certains fournisseursSAF externes : NOT_RUN ;
- génération LLM/GATE-B/POCO/Redmi/thermique/énergie : NOT_RUN, autorité NO_GO ;
- vraie composition CORE–LLM–INTERFACE : NON RÉALISÉE ; seulINTERFACElocal tourne.

Chaque critère doit pointer à build.json/device.json/source-receipt et aux logs/captures du même SHA. Les tests baseline et candidat dupliqués ne sont pas additionnés en nombre de cas uniques. Une absence de KVM donne un blocage matériel ; une éventuelle tentative logicielle TCG bornée ne qualifie pas la vitesse physique. Les limites modernes/legacygfxinfo sont conservées. Une amélioration non attestée ne devient pas PASS.

Sécurité/risques : fichiers originaux SAF préservés ; aucune donnée privée/art vocal/modèle dans les artefacts ; aucun permis ou clé de fixture n'est admis comme autorité réelle. Aucun correctif de manifeste ou garde historique LLM rouge n'est masqué par la CI M51. Les PR UNIFIED21/LLM16 et préparatifsNEXUS/AVATAR/PERF sont des responsabilités distinctes.

Gate M51 : à réceptionner d'après la campagne exacte. PASS seulement si hôte démarré/testé, données et UI préservées, registre honnête et perf comparée sans régression critique ; sinon PARTIAL/BLOCKED/FAIL précis. M51 n'ouvre aucun gate sensible. Prochaine action prioritaire : examiner la preuve Android/benchmark, puis proposer la correction des risques d'autorité sous nouveau diff non appliqué.

## Première campagne conservée — 8 novembre 2026

Source6c2656d057482bf1159fadf89327979211435f26/treec501b722e2c3099cd986f012962b123f260d79c0, [run42454245088](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42454245088) : build PASS, JVM6 historiques et15 par variante (15 cas uniques),3Python et lintPASS. Les CI historiques push42454245075/PR42454354298 sont PASS au même SHA. Deux appareils x86_64 ont démarré en logiciel TCG (KVM présent mais non accessible, aucun droit modifié). API29 boot170994ms et API35 boot248298ms sont des temps de démarrage d'émulateur, pas de l'application.

Les deux jobs appareils ont FAIL pendant la préparation : broadcast AIRPLANE_MODE protégé refusé par Android. Aucune APK installée par le banc, aucun test applicatif ni benchmark exécuté. Les résultats correspondants restent NOT_RUN. Les archives sont conservées : API29 artefact13606589799 SHA256cac2cab9168ae853642bb16914423376eb64a06fbd41d3ea0d8ae0714c418ce8 ; API35 artefact13606958735 SHA25652eae5c05a414b93ddcdaf008f9b4c87b66b487673376ccfa0344080bdcf5840. Le build13607270918 SHA256a7999eb8c762e8f294adfbb625af4d58b7c354c91d1aad7393d228b250f22f30 reste un build compilé sans réception Android.

Remédiation légitime : appeler `cmd connectivity airplane-mode enable` et relire le statut du service, sans émettre nous-mêmes le broadcast ni ajouter de privilèges. Commande présente dans ConnectivityService AOSP Android10, blobfc67c38560fa0d94cf447456cb4a0d7042267166 lignes7025–7070, référence android-10.0.0_r47 ; le framework conserve ses contrôles. La nouvelle exécution qualifie un banc corrigé, pas un retry destiné à masquer un crash. Le log complet de toute commandeADB échouée est dorénavant conservé.

La revue du manifeste de laboratoire a aussi identifié un écart de comportement : le code UI-01C et son test de rotation exigent que configChanges conserve l'Activity. Le manifeste de laboratoire reprend maintenant exactement cette politique. Les tests de recréation distincts restent en place et détruisent/recréent effectivement l'Activity ; aucun test historique n'a été modifié. Les nouveaux tests ont un état de départ explicite et produisent une capture du refus LLM. Aucun fichier historique ni garde n'est changé.

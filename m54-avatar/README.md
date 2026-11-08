# M54 — préparation AVATAR, runtime OFF

Base INTERFACE UI-01C PR #16 : `0dfeef8ff09d60a68d18398ccb6b2530f3b87df4`, tree `57ea00745ddfb0bc3c992c18ba4b61240c85706b`. Branche `codex/m54-avatar-preparation`. Module AVATAR, lots M54.03/06 et asset pipeline §9 du cahier des charges V1 Acceleration du 7 novembre 2026.

Ce lot fournit du code et des tests isolés, **pas un avatar rendu dans Android**. `AvatarContract.kt`, ses méthodes `setVisible/resize/release`, tous les 89 fichiers historiques, les source sets Gradle, permissions, scènes et applications UI-01C sont préservés. Rien sous `m54-avatar/` n'entre dans `:app`. Le cockpit M51 conserve AVATAR OFF.

## Ce qui est exécutable en revue

`src/AvatarSceneV1.kt` propose un contrat versionné au-dessus d'AvatarSession et un moteur logique déterministe, sans thread. États repos/écoute/réflexion/parole, expressions, gestes bornés, file de 4, ECO, réduction des mouvements, masquage, pause, redimensionnement, Stop et release. Les états critiques interrompent les gestes. Une séquence logique de 60 s est testée avec une horloge injectée ; elle ne mesure pas un rendu vidéo de 60 s.

La parole n'est demandée que par un événement de lecture PCM positif ; les tokens/textes ne déplacent pas la bouche. Epoch/sequence refusent les événements anciens, doublons ou hors ordre. C'est un **contrat candidat** : aucun adaptateur VOICE réel ne l'alimente. `mouthActive` n'est pas une preuve de son audible ni de synchronisation phonémique. Les visèmes restent UNSUPPORTED. Le renderer/rig/player est NOT_IMPLEMENTED.

Le hook de ressources est une abstraction testée par fixture : close retourné ne prouve pas une libération GPU. Si le hook échoue, CLEANUP_FAILED est conservé et le contrôleur ne se réutilise pas silencieusement. Aucun moteur LLM, accès documentaire, modèle, micro, décodeur ou audio réel n'est créé.

`inventory.py` inventorie seulement les références explicitement listées dans un manifeste PRIVATE_ONLY. PNG : dimensions/chunks/CRC ; MP4 : boîtes de tête seulement. **Décodage et qualité restent NOT_RUN**. Hash SHA256 exact, fichiers réguliers sans symlink/hardlink, 32 Mio par fichier, 256 Mio de lectures cumulées y compris refus, 300 entrées maximum. Aucun original n'est modifié, copié, extrait, publié ou supprimé ; aucun audio n'est extrait.

Les droits sont des déclarations à vérifier par une personne, jamais un transfert automatique de licence. Aucune entrée ne devient un master/asset de production : tous les résultats restent candidats ou en quarantaine. Le rapport est PRIVATE_ONLY ; la CLI exige une sortie privée hors dépôt et hors entrées, exclusive 0600 sans overwrite. Les noms/paths d'entrée ne figurent pas dans le rapport ; les empreintes privées doivent néanmoins rester privées.

## Ressources artistiques et points d'arrêt

Aucun master officiel ni lot de photos/vidéos autorisé n'a été fourni à cette mission. Aucun dépôt d'assets privé n'est présumé exister ou créé. Aucun asset historique ou profil vocal B n'est inspecté/copied/published pour contourner ce manque. Les seules images lues par les tests sont de minuscules PNG anonymes créés en mémoire et des boîtes MP4 synthétiques non décodées, supprimés avec leurs répertoires temporaires.

La prochaine étape artistique exige un lot choisi, provenance/droits, tri et validation humaine d'un master face/trois-quarts/profil. Les références privées ne doivent jamais entrer dans une PR/artefact public. L'intégration de ce contrat/controller dans un renderer Android demande une revue séparée et une campagne de ressources ; elle n'est pas effectuée par cette PR.

## Tests et preuves

Banc : JDK21, Kotlin2.1.21, JVM17, JUnit4.13.2 et unittest Python ; jars épinglés par SHA256. `run_review.py` compile le **vrai AvatarSession historique** et le candidat isolé. `check_scope.py` fige les 89 fichiers et leurs modes. La CI publie le commit/tree, hashes des sources, résultats et logs textuels uniquement. Les chiffres et URLs finaux sont ajoutés au bilan PR après examen de l'archive.

Commandes de reproduction :

```sh
python3 m54-avatar/bootstrap_jars.py --cache build/m54-avatar/cache
python3 m54-avatar/run_review.py --jdk /chemin/vers/jdk21 --cache build/m54-avatar/cache
python3 m54-avatar/check_scope.py
```

Statut : PREPARED ; connected/enabled/runtime_ready/user_approved/device_qualified=false. Android/renderer/performance graphique/mémoire GPU/audio/visèmes/master artistique : NOT_RUN ou NON FOURNI. Aucun point artistique/V1 n'est acquis par ces tests JVM.

Rollback : fermer cette PR ou abandonner la branche additionnelle ; aucune donnée ou migration à annuler, aucun changement de signature APK ou application installée. Aucune fusion dans main, dépense cloud, téléchargement de modèle, permission sensible ou installation personnelle autorisée par ce lot.

Voir [contrats et risques](CONTRACTS-AND-RISKS.md), [plan d'intégration](INTEGRATION-PLAN.md) et [capacité candidate](CAPABILITY.json).

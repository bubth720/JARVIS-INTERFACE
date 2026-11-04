# M50-S1B — laboratoire Android CORE + INTERFACE

**L'acceptation exige le rapport `QUALIFICATION.json` de la CI au SHA exact.**
Ce projet Gradle autonome ne fait pas partie du graphe Gradle historique.
Application `fr.jarvis.lab.m50s1`, testOnly, min29/target35, sans permission,
modèle, bibliothèque native, stockage personnel ni transport inter-APK.

## Composition exacte

`sources.json` épingle sept fichiers : le véritable `SessionCoordinator`,
ses modèles et événements CORE, et le véritable client de conversation
INTERFACE avec ses types. Le préparateur extrait leurs blobs Git sans les
modifier dans un dossier temporaire extérieur aux checkouts.

Les versions correspondent à INTERFACE : JDK17, Gradle8.13, Kotlin2.1.20,
AGP8.9.2, coroutines1.10.2, SDK35. Les applications historiques gardent leurs
versions et sources. Les licences de projet ne sont pas établies ; cette
compilation privée autorisée ne crée pas de droit de distribution publique
ni de nouvelle licence.

Un propriétaire `Application` crée volontairement un seul coordinateur,
un bus événementiel RAM et le client INTERFACE dans le même processus.
Chaque ACK reprend les UUID/génération du véritable événement CORE UserInput.
Les réponses fixes portent `LAB/SYNTHETIC` et ne reflètent pas la question.
L'adaptateur attend le drainage effectif de CORE avant le terminal de contenu :
une erreur RESOURCE_UNCONFIRMED après une métadonnée Completed reste UNKNOWN.
Stop n'est qu'une demande logique ; aucun déchargement natif n'est prétendu.

Rotation : le propriétaire processus est conservé. Fermer efface l'affichage
RAM et interdit un nouveau banc dans ce processus. UNKNOWN reste bloquant,
même en rouvrant l'Activity. Un véritable force-stop suivi d'un lancement
crée un nouvel epoch, initialement OFF. Aucun reset productif n'est exposé.

## Construire et vérifier

Depuis la racine INTERFACE, avec le checkout CORE exact voisin :

```sh
python3 labs/m50-s1/tools/check_scope.py --interface .
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s labs/m50-s1/tests -v
python3 labs/m50-s1/tools/prepare_sources.py --core ../CORE --interface . --output /tmp/m50-sources
gradle -p labs/m50-s1 -PlabSources=/tmp/m50-sources/kotlin :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=2
```

`local_tools.py provision` prépare les outils épinglés dans
`/workspace/.m50-s1b-tools`, via le proxy/TLS hérités, sans modifier les
checkouts ou credentials. `local_tools.py gradle ...` utilise ces outils.

La CI `m50-s1b-lab.yml` construit un seul APK, inspecte son manifeste et sa
signature, puis teste **ce même digest** sur émulateurs API29 et35 en mode
avion. Les XML et captures de fixtures sont conservés séparément par API.
Le script externe vérifie UNKNOWN dans le même processus puis un véritable
force-stop/restart avec nouvel epoch et absence de restauration du texte RAM.
`QUALIFICATION.json` ne peut être produit qu'après les deux campagnes réussies.
L'artefact APK n'est publié qu'après toutes ces vérifications.

Les scénarios OWNER_ERROR et RESOURCE_UNKNOWN sont des injections de panne
synthétiques du laboratoire, sans moteur réel. Ils sont sélectionnables par
extra `LAB_SCENARIO` avant activation, et explicitement affichés à l'écran.
Les tests injectent aussi ACK perdu, replay et clés périmées ; aucune identité
Binder, permission ou preuve native n'en est déduite.

## Limites et retour arrière

LLM, NEXUS, SERVICES, VOICE et EVOLUTION restent désactivés. Les tests de
laboratoire ne qualifient pas les fonctions historiques ni un appareil physique.
Mémoire/latence de cette variante restent NOT_RUN tant que non mesurées.
M50 reste NON ATTEINT ; les huit gates nécessitent leur réception distincte.
Rollback : abandonner la branche/PR et supprimer le seul laboratoire sur
l'émulateur. Aucun main, stockage historique ou appareil personnel modifié.

## M50-PERF-01 — temps de réponse du laboratoire (ne prouve pas la vitesse du LLM)

Le scénario NORMAL emploie désormais **60 ms par chunk synthétique**, au lieu
des **500 ms par chunk** de la première APK (deux chunks fixes). Cela retire
880 ms de délai de démonstration **nominal**, sans supprimer ACK, Stop, drainage,
UNKNOWN ni tests négatifs. Ce gain est un calcul de configuration, **pas**
une mesure de temps de réponse réelle, et il n'accélère aucun modèle IA.

Le scénario opt-in `SLOW_STOP` conserve 500 ms par chunk et est utilisé
exclusivement pour les tests déterministes d'arrêt depuis le véritable écran.
Les tests Android sont isolés par AndroidX Test Orchestrator, avec un processus
neuf par test, afin qu'une session fermée/UNKNOWN ne puisse jamais être
réinitialisée depuis une Activity. Aucun paramètre ou droit de production ne
change.

Le test `LabLatencyInstrumentedTest` soumet 12 tours de laboratoire NORMAL
successifs à l'interface tactile. Il mesure en horloge monotone le délai
entre le clic « Envoyer » et le premier texte réellement affiché puis la fin
de la réponse synthétique. Il vérifie les ACK croissants pour éviter d'utiliser
une réponse précédente et impose `p95 < 3_000 ms` sur chacune des deux valeurs,
pour chaque émulateur API29 et API35. La valeur p95 est conservatrice : maximum
des 12 mesures. Elle est affichée sans aucune donnée utilisateur dans les
sorties du test. L'exécution et ses chiffres demeurent **NOT_RUN** tant que
la nouvelle CI n'a pas terminé.

L'objectif produit reste distinct : **LLM réel, TTFT p95 <3 s** uniquement
après qualification d'une vraie génération locale sur appareil autorisé,
et réponse complète dépendante de sa longueur. Les tests synthétiques ne
qualifient jamais G3, G8, la chauffe, la batterie ou l'unload JNI.

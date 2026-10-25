# Validation INTERFACE-FINAL-01

Branche `feat/interface-final-01`, base UI-03 `0869b3959d3636deab3c938e6cb98b7e555f19af`.
Commit final et workflow distant : bilan de livraison. Aucune fusion dans main.

## Résultat final local

`./scripts/check.sh connectedDebugAndroidTest --no-daemon` : **BUILD SUCCESSFUL en6m17s**,
77 tâches,9 exécutées/68 incrémentales. **190 JVM** (167 précédents +23 nouveaux),
**64 Android** (55 précédents +9 nouveaux), **0 échec/erreur/ignoré**. Les nouveaux
scénarios Android comprennent six tests Studio, deux de conversation et un de mouvement.
`assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest` et
`connectedDebugAndroidTest` réellement exécutés. Lint : **No issues found.**
Les9 tests Python du packager avatar passent aussi. Aucun nouvel asset artistique.

[Résultats et SHA des APK locaux](validation/build-tests.json),
[JUnit Android](validation/android-results.xml),
[preuves de préservation](validation/preservation.json).
Les empreintes locales ne désignent pas des APK signés par GitHub Actions.

## Défauts et investigations

Une première passe intermédiaire avait60/60 Android réussis. L'ajout d'un canvas
horizontal avait ensuite révélé une collision des sémantiques ScrollBy avec le
scroll vertical : parcours bloqué, passe interrompue et non considérée réussie.
Deux nœuds de layout distincts et l'activation horizontale seulement si nécessaire
corrigent le défaut. Le test de régression existant passe dans les64 finaux.

Une passe ultérieure avait62 réussis/2 échoués sur64 : test de relance comptant
les éléments composés visibles d'une LazyColumn, et clic du raccourci avant exposition
du panneau par le scroll externe. Les assertions vérifient maintenant le nombre de
réponses dans l'état réel et la visibilité effective du bouton après scroll du panneau.
Les résultats attendus n'ont pas été affaiblis ; les deux scénarios passent dans la
suite finale. [Relevé d'investigation](validation/investigation.json).

## Périmètre des vérifications

- JVM : codec borné/versionné, données invalides, copies des profils/verrouillages,
  historique sur opt-in, contrôle de volume, stockage réel puis nouvelle instance,
  corruption conservée, ordre des écritures, ACK durable et erreurs de restauration,
  fusion des champs visuels, sources documentaires, politiques de personnalité.
- Android : suite précédente complète, confirmation du profil local et recréation,
  copie/restauration nommée, modules indisponibles/absence de permission INTERNET,
  suppression confirmée, placement numérique invalide, réduction réelle des frames
  et reprise de l'avatar, actions copier/détails/relance en simulation.
- Hôte Android : captures réelles et véritable arrêt forcé/nouveau PID, vérification
  des octets DataStore et de l'interface restaurée. Distinct de Activity.recreate.
- Le statut CI distingue compilation de l'APK instrumenté et exécution locale sur
  émulateur ; le workflow distant ne lance pas de test connecté.
- API26 émulateur logiciel uniquement. POCO X8 Pro, Redmi Pad SE, API35, TalkBack
  prononcé, énergie/chauffe et performance physique : non exécutés.

## Commandes

```sh
./scripts/check.sh connectedDebugAndroidTest --no-daemon
python3 scripts/check_ui01_scope.py --apk
python3 tools/avatar/check_ava04_scope.py
python3 -m unittest discover -s tools/avatar -p 'test_*.py' -v
```

Les gardes autorisent seulement les fichiers d'intégration UI listés, ainsi que le
contrôle de visibilité de l'hôte AvatarDemo pour l'accessibilité. Le contrôleur et
le moteur avatar, le codec des dispositions, les contrats et le manifeste restent
figés. Aucune image privée n'est copiée dans l'APK ou les artefacts CI.

## Dernière retouche des aperçus et revalidation ciblée

Après la suite complète64, deux fichiers d'affichage sont retouchés : les gabarits
rendent maintenant visibles les effets de franchise/humour/initiative/ton, et
l'accueil du Studio affiche des descriptions utiles plutôt que des noms de routes
ou versions de contrat internes. Aucun modèle de stockage, connecteur ou contrôleur
ne change pendant cette retouche.

La version finale exécute de nouveau les quatre tâches de build/JVM/Lint/APK
instrumenté et les trois classes Android ajoutées : **191 JVM** (un nouveau test
vérifie tous les réglages de gabarit) et **9 Android**, tous réussis,0 échec/erreur/ignoré,
**BUILD SUCCESSFUL2m48s**, Lint **No issues found.** Ces9 sont des réexécutions parmi
les64 scénarios précédents ; ne pas annoncer73 tests Android distincts.
La suite complète64 n'est pas présentée comme une nouvelle exécution après cette
retouche ciblée. [Reçu final et empreintes APK](validation/post-preview-validation.json),
[JUnit final ciblé](validation/post-preview-android.xml).

## Restauration après véritable arrêt du processus

[Preuve](validation/process-restart.json) : ancien PID5656, nouveau PID6563,
`am force-stop`, absence du processus avant relance, même SHA-256 du fichier
workspace après relance. Disposition `Mission-final-01` affichée, deux profils
compact/large présents, tutoiement local modifié et réduction des animations retrouvés.
Aucun remplacement par une simple rotation ou Activity.recreate. Contrôle effectué
sur émulateur seulement ; redémarrage physique de l'appareil non exécuté.

## Captures Android réelles

Données publiques de vérification uniquement, aucune image Athéna privée :

- [Dashboard](validation/captures/dashboard.png)
- [Studio](validation/captures/studio.png)
- [Dispositions](validation/captures/layouts.png)
- [Personnalité](validation/captures/personality.png)
- [Confirmation locale](validation/captures/personality-confirmation.png)
- [Modules](validation/captures/modules.png)
- [Apparence](validation/captures/appearance.png)
- [Disposition visible après mort du processus](validation/captures/restored-after-process-stop.png)

Le premier script hôte de captures n'attendait pas toujours le rendu du nouvel onglet
et cherchait la copie restaurée hors du viewport. Il a été corrigé (attente du titre
et défilement avant lecture) puis les captures rafraîchies. Cette tentative hôte n'est
pas confondue avec un test de l'application réussi ou une validation physique.

## Mesures limitées à l'émulateur

[Conditions et mesures](validation/emulator-metrics.json),
[mémoire brute](validation/emulator-meminfo.txt),
[CPU brut](validation/emulator-cpu.txt),
[gfxinfo brut](validation/emulator-gfxinfo.txt).
Android8/API26 x86_64 logiciel, SwiftShader sans KVM,720×1280/densité320,
APK debug, mode avion1, préférences restaurées avec réduction d'animations.

Trois démarrages après arrêt forcé : `ActivityManager TotalTime` **6787,6809,6302ms**.
Il s'agit du temps ActivityManager sur émulateur, pas d'un benchmark physique froid
ou de la latence d'une réponse CORE. PSS instantanée **41181KiB** ; CPU application
instantané **0,0%** au repos. Ces relevés ne fixent pas un budget RAM/CPU pour le téléphone.
Le gfxinfo ne porte que sur deux frames de démarrage, toutes classées janky : il est
**insuffisant pour qualifier la fluidité des gestes**, et ne sert pas à annoncer60fps.
Aucune consommation énergétique ni comportement thermique mesuré.

## Revue finale du dialogue et des formats

Le dialogue de confirmation a été rendu lisible en français, en conservant ses
valeurs exactes et la décision d'approbation. Nouvelle revalidation :191 JVM et9
Android réussis, BUILD SUCCESSFUL3m45s, Lint sans anomalie. L'APK de production
n'a ensuite plus changé. Le helper optionnel de capture a été recompilé puis testé
par une invocation Android supplémentaire réussie28,818s. Cette réexécution n'est
pas un65e scénario distinct. [Résultat natif](validation/dialog-capture-result.txt).
Une tentative de capture PixelCopy du dialogue avait échoué : API26 ne supporte
pas la capture Compose de dialogues (<28). Le helper utilise la capture native du
vrai écran après synchronisation du modal ; aucune image reconstruite.

[Rotation téléphone paysage](validation/captures/phone-landscape.png) et
[fenêtre logique large](validation/captures/tablet-window.png) ont été réellement
rendues sur le même émulateur :1280×720, densité320 puis160, profil large automatique.
Ce changement de densité simule une fenêtre tablette ; il ne valide pas un Redmi Pad.
[Conditions et limite de la capture de police système](validation/windows.json).
Le contrôle hôte additionnel de police système1,5 n'a pas confirmé son seuil de hauteur
et n'est pas déclaré réussi. Les tests Android de texte2×/fenêtre courte et sémantiques
TalkBack de la suite complète passent ; TalkBack physique reste non exécuté.
Les paramètres système d'origine de l'émulateur ont été restaurés.

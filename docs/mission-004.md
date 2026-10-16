# Mission 004 — Fondations du dashboard tactile

Branche : `feature/interface-dashboard-004`.
Base : `fix/interface-ci-003`, `4fe7b19ec7d637dda28cd517ec5f87a186407d0d`.
Le commit final est indiqué dans le bilan de livraison et vérifiable avec
`git rev-parse HEAD`. Les liens de CI distinguent le code initial du dernier
commit livré.

## Périmètre implémenté

- Quatre panneaux locaux : Avatar, État système, Applications JARVIS, Commandes.
- Modèle de panneau avec identifiant, position, dimensions, minimums et verrou.
- Grille entière indépendante des pixels ; profils compact 6×12 et large 12×10.
- Édition explicite, poignées de déplacement/redimensionnement, aperçu coloré,
  alignement à la cellule et validation au relâchement.
- Refus atomique des collisions, sorties de canevas et tailles sous 3×3 ; aucun
  déplacement automatique des voisins. Verrouillage appliqué dans les règles métier.
- En mode normal : aucune poignée ni détecteur de geste d’édition ; le reducer
  refuse également les actions de placement et verrouillage.
- Retour Android, Terminer ou Paramètres quittent l’édition. Les profils et verrous
  sont conservés en mémoire ViewModel lors d’une recréation d’activité.
- Grille défilable en fenêtre courte ; contenu des panneaux défilable, contrôles
  d’édition séparés ; actions TalkBack par cellule et annonce des refus.
- Annulation du geste en cas de changement de profil ou de taille des cellules.

Pas de stockage durable, connecteur, animation, lecteur MP4, service permanent ou
permission INTERNET. L’avatar reste un emplacement compatible avec le contrat
préliminaire MP4/2D/hybride, sans moteur. CORE, VOICE et SERVICES ne sont pas requis.
Le minimum API 26, les versions Kotlin/Compose et le workflow CI sont conservés.

## Fichiers

Créés :

- `domain/Dashboard.kt` : grille, panneau, profils, validation et placement atomique.
- `ui/DashboardGrid.kt` : canevas adaptatif, panneaux, aperçu, poignées, accessibilité.
- `src/test/.../DashboardTest.kt` : règles, reducer et séquence déterministe.
- `src/androidTest/.../DashboardInteractionTest.kt` : scénarios Compose tactiles.
- `docs/mission-004.md` : présent bilan.

Modifiés : `domain/HomeState.kt` (état et actions), `ui/JarvisApp.kt` (écran et mode
édition), `README.md` (utilisation et recette), `docs/decisions.md` (décisions).
`HomeViewModel`, manifeste, contrat avatar, dépendances et workflow inchangés.
Aucun autre dépôt JARVIS n’est modifié, aucune fusion ni opération sur `main`.

## Validation

Commande : `./scripts/check.sh --no-daemon` dans le dépôt, JDK 17 et SDK 35.

La validation locale initiale confirme :

- `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest` réussis ;
- 22 tests JVM : 19 nouveaux et 3 existants, aucun échec/erreur/test ignoré ;
- Lint : `No issues found.` ;
- APK application et APK instrumenté produits.

Les 19 tests couvrent les quatre panneaux et deux profils initiaux, déplacement,
redimensionnement, minimums et limites exactes, collisions, contacts bord à bord,
verrouillage, panneau inconnu, débordements d’entiers, mode normal, paramètres,
profils indépendants et remontée des refus. Une séquence de 1 500 propositions par
profil (3 000 au total) vérifie les invariants des dispositions acceptées et refusées.

### Vérification finale incluant l’instrumentation

```sh
./scripts/check.sh connectedDebugAndroidTest --no-daemon
```

Résultat : `BUILD SUCCESSFUL in 2m 9s`, 77 tâches, dont 20 exécutées.
Les 22 tests JVM et les **10 tests instrumentés** passent : 0 échec, 0 erreur,
0 ignoré. Les tests Android ont été réellement exécutés sur un émulateur Android
8.0/API 26, x86_64, 720×1280 à 320 dpi, rendu logiciel sans KVM, mode avion activé,
sans autre application JARVIS. Les animations système ont été désactivées pour
l’instrumentation ; aucune animation applicative n’est développée.

Les 10 tests instrumentés couvrent les 3 scénarios existants (démarrage autonome,
paramètres, recréation) et 7 scénarios dashboard : quatre panneaux/mode édition,
déplacement tactile, agrandissement tactile, mode normal, verrouillage,
chevauchement refusé et conservation d’une disposition modifiée après recréation.
La préparation du mode avion a initialement été refusée au shell Android 8,
avant exécution des tests. ADB root a été utilisé sur cette image userdebug pour
appliquer le réglage ; l’application et ses permissions restent inchangées.

Le défilement des paramètres en fenêtre courte a été préservé après revue et
revalidé par la suite finale. Les résultats XML courants se trouvent dans
`app/build/test-results/testDebugUnitTest` et
`app/build/outputs/androidTest-results/connected/debug` ; le rapport HTML Android
est dans `app/build/reports/androidTests/connected/debug`.

Les captures locales des modes normal/édition compact et de l’édition large ont
été inspectées dans `app/build/reports/mission-004/`. Le format large est simulé
par un changement de taille/densité Android sur le même émulateur, pas par un
appareil physique. Le canevas compact défile verticalement ; les quatre panneaux
restent accessibles, même lorsque leur partie inférieure dépasse la fenêtre.

### GitHub Actions

Le [run applicatif initial 38826512449](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/38826512449)
au commit `e98dbd4` est **Success** : préparation SDK, quatre tâches Gradle,
vérification des APK et upload des artefacts. La dernière modification des
paramètres et le présent rapport sont validés dans un run supplémentaire lié
au commit final, référencé dans le bilan de livraison.

Le workflow reste identique à la mission 003. Il publie `jarvis-interface-apks`
(application et APK instrumenté) et `android-validation-reports` (JVM/Lint).
Il compile les tests instrumentés mais ne les exécute pas sur le runner ; leur
exécution réelle est la validation locale API 26 décrite ci-dessus.
Les journaux bruts distants nécessitent une connexion GitHub et l’API est bloquée
par le proxy cloud ; statuts et artefacts sont vérifiés sur les pages publiques.

## Limites et risques

- Dispositions non persistées après mort du processus ; absence de migration et
  restauration durable volontaire pour cette mission.
- La borne verticale est celle du canevas défilable : un panneau peut être hors de
  la portion visible, mais jamais hors du canevas logique.
- Pas d’auto-défilement pendant le glissement, échange de panneaux, annulation
  d’une modification validée ou recompactage automatique. Les espaces libres et
  les actions accessibles servent à réorganiser les panneaux progressivement.
- En fenêtre très étroite ou à forte échelle de texte, titres ellipsés et contenus
  défilants ; l’ergonomie et le conflit entre défilement et gestes demandent des
  essais sur POCO X8 Pro et Redmi Pad SE.
- Les changements de profil utilisent deux dispositions indépendantes, pas une
  conversion automatique. Le retour au profil précédent retrouve sa disposition.
- L’aperçu suit les cellules ; le panneau réel ne change qu’au relâchement valide.
  Une collision intermédiaire n’empêche pas une destination finale libre.
- Les widgets Applications et Commandes décrivent des fonctions à venir ; aucune
  disponibilité externe ou métrique système n’est simulée.
- Aucun test physique, mesure de batterie, de fluidité GPU ou de mémoire sur les
  appareils cibles n’est revendiqué. Recette manuelle détaillée dans le README.

## Prochaine action

Faire valider la mission et tester les gestes sur les deux appareils. Ensuite,
définir la persistance locale versionnée et ses règles de restauration dans une
mission séparée. Aucune autre mission n’a commencé.

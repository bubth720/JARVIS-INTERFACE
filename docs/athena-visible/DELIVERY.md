# Athéna — remplacement réel du rendu principal

Branche `fix/athena-premium-default`, base la plus avancée INTERFACE + AVATAR :
`db459ba388a3fea992d80320c3989be0edda8500` (`feat/interface-final-01`).
Aucune fusion dans main, aucun dépôt externe modifié.

## Cause exacte

`ui/DashboardGrid.kt` appelait `AvatarDemo(Modifier.fillMaxWidth())`.
L'argument par défaut `remember { DemoAvatarArtwork() }` dans `avatar/AvatarDemo.kt`
instanciait `avatar/AvatarArtwork.kt::DemoAvatarArtwork`, identifiant
`jarvis.demo.adult-vector.v1` : personnage vectoriel brun dessiné avec des formes.
Le pack public `assets/avatar-v2-demo/manifest.json` est également DEMONSTRATION ;
charger ce pack technique n'aurait pas constitué un remplacement artistique premium.
La galerie privée AVA-04 n'était pas le rendu de démarrage : c'était le défaut réel.

## Correction exécutée

Le dashboard appelle maintenant `AthenaAvatar`. Ce composant charge un véritable
pack premium V2 dans `AthenaResources`, puis passe explicitement `PremiumAvatarArtwork`
au même hôte et au même contrôleur d'animations. Le paramètre par défaut vectoriel
n'est plus sélectionné par le panneau de production. Studio possède une vraie page
**Avatar**, accessible par son onglet et sa carte, avec un portrait agrandi et les
expressions disponibles. Les libellés et sémantiques distinguent Athéna du secours.

Le défaut d'une compilation privée est le ZIP intégré au moment du build. Un import
volontaire peut ensuite remplacer ce pack ; copie bornée, validation complète V2,
rejet du grade DEMONSTRATION et écriture AtomicFile avant activation locale. Ce pack
est relu après fermeture/processus ; aucun grant URI conservé. Un seul pack décodé
est partagé par Application, rendu par des hôtes distincts sans dupliquer le contrôleur.
Chargement/décodage hors du fil principal, allocations bornées par le validateur V2.
La visibilité, le cycle de vie et la réduction d'animations restent gérés par l'hôte
existant. La sélection d'une expression est instantanée si les animations sont réduites.

La comparaison technique V2 peut encore être ouverte. Avec un portrait premium
principal, confirmer une démo ferme la comparaison en conservant Athéna. L'import
principal passe par Studio/Avatar et le dépôt validé, pas par le cache de comparaison.
Les tests directs des moteurs V1/V2 gardent leur comportement technique antérieur.

Sans ressource premium, l'application affiche une demande d'import ; elle n'affiche
pas le personnage vectoriel comme rendu normal. Le secours technique nécessite une
ouverture explicite dans Studio, ou un véritable échec de rendu (secours statique
existant). Il ne remplace pas silencieusement un pack valide.

## Ressources privées et limite artistique

Le pack AVA-04 existant, réellement exploitable et inspecté, contient12 portraits RGBA
semi-réalistes originaux : cheveux cuivrés/dorés, lunettes noires, yeux noisette,
uniforme bleu nuit, expressions et trois-quarts. SHA-256 du ZIP :
`e3fc17885ae285c4380754f1cab04c74226c8407abbd174b3625a71fa0f9c4e0`.
Ces images sont **IMAGE_REPLACEMENT**, pas un rig découpé ni un lip-sync terminé.
Les paupières/visèmes/calques articulés définitifs restent manquants. Le moteur
conserve ses commandes ; aucun clignement articulé premium absent n'est inventé.

La demande actuelle autorise cette intégration privée de prévisualisation comme
rendu principal. Elle ne vaut pas autorisation de publier les médias ni approbation
artistique définitive : les reçus AVA-04 restent inchangés. Ni la référence utilisateur,
ni les portraits, ni leurs captures premium ne sont committés ou envoyés à GitHub.

**Le livrable visuel est l'APK privé**, remis dans les artefacts locaux privés.
Le ZIP et les captures restent dans `/workspace/artifacts/athena-private/`.
Le build public/CI ne possède pas les ressources privées : il permet leur import,
mais n'est pas déclaré livraison artistique autonome sans cet import. L'absence
d'autorisation de diffusion publique des assets est le blocage de distribution,
pas un motif pour conserver le placeholder dans l'APK privé livré.

## Compilation reproductible

Préparer un répertoire hors checkout qui contient uniquement :

```text
PRIVATE_ASSETS/
  athena-private/athena-review.zip
```

```sh
./scripts/check.sh -PathenaPrivateAssets=/chemin/PRIVATE_ASSETS --no-daemon
python3 scripts/check_athena_distribution.py app/build/outputs/apk/debug/app-debug.apk --private-pack /chemin/PRIVATE_ASSETS/athena-private/athena-review.zip
```

Le répertoire doit être hors dépôt, ne contenir que ce ZIP, et la propriété est
**refusée en CI**. Aucun changement de dépendance, permission INTERNET ou service.
Le build public sans propriété est vérifié par :

```sh
./scripts/check.sh --no-daemon
python3 scripts/check_athena_distribution.py app/build/outputs/apk/debug/app-debug.apk
```

Ne pas confondre cet APK public sans images et l'APK privé de vérification visuelle.
Ne pas désinstaller une version existante pour contourner une signature différente ;
utiliser la même clé de signature pour préserver ses données. Aucune installation
sur appareil personnel réalisée par Codex.

## Validation

Les résultats réellement observés sont fournis dans le bilan final et les reçus
numériques. Les tests Android avec pack premium utilisent la propriété privée ;
les sources des tests sont publiques, les images et captures ne le sont pas.
Tests ajoutés : rendu premium de démarrage dashboard/Studio sans import manuel,
recréation, sauvegarde/import relu par un nouveau dépôt, rejet de la démo comme
primaire, absence de personnage procédural lorsque l'art est absent. La régression
V2 vérifie également que confirmer une comparaison de démo conserve le premium.

Les quatre panneaux, profils compact/large, gestes, verrouillages, sauvegarde
DataStore V1, personnalisation, personnalité locale et conversation sont conservés.
Aucune connexion CORE/VOICE/SERVICES ni performance physique n'est revendiquée.

### Résultats locaux réellement exécutés

- Build privé : `assembleDebug`, `testDebugUnitTest`, `lintDebug`,
  `assembleDebugAndroidTest` et `connectedDebugAndroidTest` terminés en 7 min 42 s.
- **191 tests JVM et 68 tests Android réussis, zéro échec, erreur ou test ignoré**.
  Lint : `No issues found.` Émulateur API 26, rendu logiciel ; aucun appareil physique.
- 9 tests Python du packager réussis. Contrôles de portée : 96 fichiers protégés,
  DataStore V1 inchangé, aucune référence CORE de test dans le DEX de production.
- Build public, sans propriété privée : quatre tâches requises réussies en 45 s.
  Vérification du ZIP absent dans cet APK, y compris après un build privé précédent.
- Capture instrumentée dédiée : **1 test supplémentaire réussi**, 22,347 s ;
  dashboard, Studio/Avatar et portrait réellement capturés dans l'application.
  Pack présent dès le démarrage ; aucun import manuel effectué pour ces captures.
- APK privé SHA-256 :
  `a5e38d004174215a70623e933c130870789cfb40ec41537332c21181504ec208`.

Les reçus XML/JSON de ce dossier contiennent uniquement résultats, identifiants et
empreintes. Les images de preuve et l'APK avec art restent hors dépôt. La CI ne
lance pas l'émulateur : ses tests Android sont compilés ; les 68 exécutions citées
ci-dessus sont celles de l'émulateur local. Les valeurs CPU, RAM, fluidité et
thermiques physiques n'ont pas été mesurées. POCO X8 Pro et Redmi Pad SE non accessibles.

### Fichiers et responsabilités

- `avatar/athena/AthenaResources.kt` : lecture/import durable borné et cache premium.
- `avatar/athena/AthenaAvatar.kt` : hôte principal, import et Studio/Avatar.
- `JarvisApplication.kt` : propriétaire du dépôt de ressources.
- `ui/DashboardGrid.kt`, `workspace/WorkspaceScreen.kt` : branchement dashboard/Studio.
- `avatar/AvatarDemo.kt`, `avatar/v2/AvatarV2Preview.kt` : hôte partagé, libellés honnêtes
  et maintien du premium après comparaison technique.
- `app/build.gradle.kts` : source d'assets privée externe facultative, interdite en CI.
- Tests `avatar/athena/`, `avatar/v2/AvatarV2DashboardTest.kt`, `StorageTestSupport.kt` :
  rendu principal, absence d'art, recréation, import et non-régression.
- `scripts/check_athena_distribution.py`, workflow, deux contrôles de portée :
  séparation privée/publique et preuves CI ; README et présent rapport : livraison.

### Risques résiduels et suite

L'art présent est un ensemble de portraits premium de prévisualisation ; il reste
à faire approuver le personnage et produire un rig cohérent avec paupières,
visèmes et calques séparés. Le portrait n'est pas présenté comme modèle articulé
définitif. L'ancienne démo reste testable en secours explicite. Les imports sont
atomiques mais n'offrent pas encore de gestionnaire de plusieurs packs artistiques.
Une installation signée par une autre clé peut être refusée par Android : préserver
la clé existante et les données, ne pas désinstaller pour forcer le remplacement.
La prochaine étape est la revue visuelle privée du portrait dans l'application,
puis la préparation des calques approuvés. Aucune mission suivante ni fusion lancée.

### Arrêt réel du processus

Le contrôle complémentaire a installé l'APK public sans art sur l'émulateur :
carte d'import présente et aucun renderer procédural. Après installation de l'APK
privé par mise à jour, un véritable `am force-stop` suivi d'un démarrage a donné
un nouveau PID, le portrait premium principal sans import et un fichier DataStore
V1 strictement identique. Voir `validation/restart.json` et `public-build.json`.
Une première tentative a été bloquée par une alerte ANR de **System UI** de
l'émulateur ; après fermeture de cette alerte système, la vérification a réussi.
Cette observation est conservée comme limite de l'environnement logiciel.

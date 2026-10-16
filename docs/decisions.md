# Décisions — mission 002

- Kotlin 2.1.20, plugin Compose de même version, AGP 8.9.2, Gradle 8.13,
  Compose BOM 2025.04.01 : versions stables fixes, volontairement sans versions dynamiques.
- SDK compilation/cible 35, minimum 26 ; bytecode Java/Kotlin 17. La compatibilité
  physique POCO/Redmi reste à vérifier. Les exigences de boutique seront revues avant publication.
- Un module `app` évite une modularisation prématurée. Domaine, présentation et
  frontière avatar restent séparés par packages.
- MVVM unidirectionnel : action -> réduction d’état -> StateFlow -> Compose.
  `collectAsStateWithLifecycle` suspend la collecte hors état actif.
- ViewModel conserve l’état pendant la recréation ; après mort du processus,
  retour à l’accueil par défaut. Aucun réglage persistant n’est proposé aujourd’hui.
- Aucun service, permission Internet, accès fichiers externe ou dépendance à CORE/SERVICES.
- Le manifeste n’exporte que l’activité de lancement nécessaire. Sauvegarde Android
  désactivée en attendant une politique de stockage et de restauration explicite.
- Avatar : MP4, animation 2D ou hybride sont des options futures. Le contrat est
  préliminaire ; aucun lecteur Media3 ni moteur de rendu n’est ajouté. Les futures
  sessions devront suspendre les médias invisibles et libérer leurs ressources.
- Les interfaces de stockage et connecteurs seront définies lorsqu’un premier usage
  concret apparaîtra ; aucun faux service ni état externe inventé.
- Pas de polling ni animation permanente ; les changements UI suivent les actions.

## Mission 004 — grille tactile

- `DashboardPanel` possède un identifiant typé stable, une géométrie entière, des
  minimums et un verrou. `placePanel` valide une proposition atomiquement : aucune
  résolution implicite de collision ni modification des panneaux voisins.
- Les bornes et intersections utilisent des calculs Long pour refuser les entrées
  extrêmes sans débordement d’entiers. Les contacts bord à bord sont autorisés.
- Deux profils indépendants : compact 6×12, large 12×10 à partir de 700 dp. Des
  espaces libres initiaux permettent de réorganiser les panneaux. Minimum 3×3.
- État unidirectionnel : geste -> aperçu éphémère -> action Place -> règle métier ->
  StateFlow -> Compose. L’aperçu ne corrompt jamais la disposition validée.
- Poignées limitées au mode édition, verrouillage contrôlé aussi dans le domaine.
  Les actions de placement/verrouillage sont également refusées par le reducer en
  mode normal ou dans les paramètres. Retour Android quitte d’abord l’édition.
- Les modifications de profil ou de taille de cellule annulent un geste en cours.
  Les gestes annulés n’appliquent pas de position partielle.
- Les cellules ont une hauteur minimale de 56 dp ; le tableau défile dans une
  fenêtre courte au lieu de réduire les contenus à une taille illisible. La limite
  de placement est le canevas logique défilable, pas seulement sa portion visible.
- Pas d’auto-défilement pendant le geste, de rotation de panneau, de recouvrement
  volontaire ni de réorganisation automatique. Des actions accessibles par cellule
  complètent les gestes. Les essais physiques restent nécessaires pour l’ergonomie.
- Layouts et verrous restent en mémoire ViewModel. La persistance durable et ses
  migrations sont reportées ; aucun stockage, service ou permission n’est ajouté.
- Aucun rendu d’avatar : le contrat MP4/2D/hybride existant reste inchangé.

## Mission 005 — persistance locale

Ces décisions remplacent le comportement sans persistance des missions précédentes.

- DataStore Core 1.1.7, JSON kotlinx.serialization 1.8.1, coroutines 1.10.2 : un seul
  fichier petit et versionné, sans base relationnelle ni plugin de génération inutile.
- Domaine indépendant : `LayoutRepository`, identifiants typés et géométrie entière.
  Les minimums viennent des règles métier, jamais du fichier sauvegardé.
- Un DataStore par fichier/processus, propriété de l’Application. Un acteur traite
  les commandes et écritures FIFO ; `updateData` assure la transaction atomique.
  Les I/O utilisent Dispatchers.IO et les suspensions ne bloquent pas le thread UI.
- L’état affiché est provisoire jusqu’au retour réussi d’updateData. Géométrie et
  statut non durable sont publiés ensemble. Un échec garde les changements visibles
  avec un avertissement explicite et une possibilité de réessayer. Aucun flush de
  cycle de vie supposé fiable ; tuer le processus avant l’accusé peut perdre l’édition.
- JSON v1 contient compact/large, id/x/y/width/height/locked. Taille maximale 64 Kio,
  profondeur limitée, UTF-8 et types stricts, clés uniques, panneaux complets uniques,
  collisions et limites validées. Aucune position invalide n’atteint le rendu.
- Pas de remplacement silencieux d’un fichier illisible, futur ou avec widget inconnu.
  Afficher les valeurs initiales et protéger le fichier. Reset explicite avec archive
  des octets d’origine ; échec d’archive = reset refusé. Les champs supplémentaires
  des versions supportées sont conservés lors des sauvegardes/réinitialisations.
- Ajouter un widget exige une migration explicite versionnée : préserver les anciens
  identifiants et réglages, placer le nouveau dans une zone valide, tester la migration
  complète avant écriture. Sans migration, un document incomplet/inconnu est protégé.
  Une future migration doit produire et valider les deux profils atomiquement ; ne
  jamais réinterpréter une version supérieure comme la version courante.
- Pas d’export ni de backup distant dans cette mission. Les archives restent privées
  et sont supprimées avec les données de l’application. Aucun service, réseau,
  permission, connecteur ou moteur d’avatar ajouté. API 26 et CI conservées.

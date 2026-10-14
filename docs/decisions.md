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

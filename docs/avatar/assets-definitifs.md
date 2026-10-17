# Assets attendus pour le personnage définitif

Le prototype utilise uniquement des formes originales écrites dans le dépôt.
Aucune image de référence n’a encore été fournie ni reproduite. Le personnage
provisoire n’est pas le modèle artistique final et n’identifie aucune personne réelle.

## Direction et livraison

Personnage féminin **adulte**, original, cadré au buste : cheveux bruns mi-longs,
uniforme futuriste bleu nuit, expression naturelle, ambiance cyan discrète. Les deux
références à venir guideront le dessin, sans copier l’identité d’une personne réelle.

Fournir un fichier source éditable avec calques nommés (PSD/Krita/SVG ou équivalent),
les droits d’utilisation/modification/distribution, et des exports transparents dans
un même repère. Une illustration aplatie ne suffit pas. Ne pas fournir seulement
une vidéo MP4 pour remplacer ce prototype articulé.

## Calques et informations nécessaires

| Élément | Exports / exigences |
| --- | --- |
| Buste / uniforme | Silhouette complète derrière le cou, épaules, col, insigne cyan séparables ; marge sous les parties cachées pour la respiration. |
| Cou | Partie cachée sous le visage/col, ombre de contact séparée ; pivot de liaison avec la tête. |
| Cheveux arrière | Masse complète jusqu’aux épaules, masque de profondeur derrière le visage. |
| Visage | Peau sans yeux ni bouche incrustés ; oreilles séparables et zones cachées complétées. |
| Yeux gauche/droit | Blanc, iris/pupille, reflets séparés ; points d’ancrage et masque du globe. |
| Paupières gauche/droite | Supérieure/inférieure et cils ; masques de fermeture continue, états ouvert/mi-clos/fermé comme références de raccord. |
| Sourcils | Gauche/droit indépendants, position neutre et pivots. |
| Nez / détails du visage | Couche ou géométrie permettant un léger décalage de profondeur sans détacher les yeux du visage. |
| Bouche neutre | Expression de repos originale ; masque de raccord. Aucun jeu de visèmes ni lip-sync requis à ce stade. |
| Cheveux avant | Frange + mèches gauche/droite séparées, zones cachées complétées, ordre de recouvrement. |
| Ombres / lumière | Ombres de contact et reflets cyan séparés, modes de fusion et opacités documentés. Pas de fond opaque intégré au personnage. |
| Fond éventuel | Couche indépendante ; facultative, remplaçable par le thème local. |

Pour chaque calque : identifiant stable, dimensions, origine, pivot, ordre/profondeur,
masque, zone de recouvrement et limites de déformation. Les deux côtés doivent
rester dessinés sous les cheveux pour les inclinaisons jusqu’à ±5,5°. Prévoir une
marge d’au moins 10 % autour de la silhouette et vérifier les raccords au col/cou.

## Format et budget à valider

- PNG RGBA sRGB transparent pour les rasters, SVG/chemins pour les vecteurs si le
  backend retenu le permet. Aucun chargement réseau obligatoire.
- Artboard source conseillé : 1024×1365 ou ratio portrait proche ; exporter tous les
  calques avec leur repère commun et fournir les métadonnées d’ancrage normalisées.
  Le moteur actuel travaille en **240×320 unités logiques**, puis adapte le rendu
  aux pixels disponibles sans modifier les dispositions du dashboard.
- Livrer d’abord un jeu réduit adapté au panneau et une version de prévisualisation ;
  éviter plusieurs dizaines de textures pleine taille. Une texture RGBA 1024×1365
  représente environ 5,3 Mio décodés, indépendamment de sa compression PNG.
- Les budgets RAM/GPU/qualité définitifs seront décidés après mesures sur POCO et
  Redmi. Aucune consommation physique du modèle final n’est déduite de ces chiffres.
- Fournir un manifeste versionné des ressources et une image neutre statique de
  secours. Les assets sont emballés dans l’APK, contrôlés avant activation et libérés
  lors de la disparition de leur hôte.

La prochaine étape artistique est de valider ces calques et leurs raccords avant
un remplacement du renderer. Un SDK articulé spécialisé nécessitera une décision
séparée sur les outils, licences, ABI et budgets ; il n’est pas ajouté implicitement.

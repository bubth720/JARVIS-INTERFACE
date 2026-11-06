# UI-01B — livraison de validation

Branche `feat/ui-01-native-cockpit`, PR brouillon #16. Les résultats UI-01
précédents sont historiques ; ils ne valident pas les nouvelles fonctions.

## Fonctions locales développées

| Espace | Comportement |
| --- | --- |
| Système | Android/API, PSS processus, RAM et stockage disponibles, économie Android ; sources nommées, actualisation et export texte |
| Administratif | Tâches avec échéance ISO validée, état terminé/rouvert, références durables de documents choisis par SAF |
| Finance | Saisie manuelle EUR, dépenses/revenus en centimes exacts, budget du mois courant, totaux et exclusion/réinclusion réversible |
| Projets | Titre, description, échéance, progression 0–100 %, état terminé/rouvert |
| Écriture | Notes/œuvres locales, brouillons persistants, modification du texte enregistré |
| Création | Travaux créatifs avec description, références de fichiers et ouverture par une application Android disponible |
| Technique | Diagnostic et journal borné à 100 événements sans contenu des enregistrements ; export volontaire |
| Sécurité | Informations de stockage/permissions, accès aux paramètres Android, protection volontaire FLAG_SECURE |

Aucun enregistrement n’est prérempli dans l’application Android. CORE, LLM,
VOICE et les autres services sont Non connecté. Pas d’accès bancaire, moteur
d’écriture, éditeur multimédia, antivirus ou synchronisation simulés.

## Stockage et navigation

Base SQLite privée `ui01b-workspace.db`, schéma v1, nouvelles tables uniquement.
Lectures/écritures sur Dispatchers.IO. Les titres, textes, dates, montants,
progressions, états et URI sont conservés. Les brouillons, budgets, réglages,
route et dernier module sont privés à l’application et persistés localement.
La sauvegarde distante demeure désactivée. Pas de chiffrement applicatif
supplémentaire ; la protection du stockage dépend d’Android et de l’appareil.

Les documents sont référencés, pas copiés. Leur disponibilité hors ligne dépend
du fournisseur choisi et de ses droits persistants. L’ouverture nécessite une
application de lecture compatible. Les erreurs sont affichées.
Pas de suppression automatique, de synchronisation ni de pagination des entrées.
La qualification sur grands volumes reste ouverte.

Les changements orientation/screenSize sont gérés sans recréation de l’activité.
Compose réagit à la configuration ; la disposition paysage utilise les panneaux
côte à côte lorsque la largeur est suffisante. En fenêtre étroite, le défilement
et la lisibilité priment. La route ouverte, les réglages et saisies restent présents.
Le Retour Android ramène à l’accueil. La version UI-01 précédente reste dans
LegacyCockpitScreen ; aucun développement d’un autre module n’est modifié.

## Ambiances et énergie

Palette partagée générée depuis `design/ui01b-tokens.json` pour Kotlin et HTML.
Le module sélectionné est souligné ; sa couleur se propage au thème et aux
LEDs, hologrammes et reflets, avec interpolation douce. Le bureau garde ses objets.

Économie : animation ambiante arrêtée. Équilibré : environ 15 mises à jour/s.
Qualité : environ 30 mises à jour/s, intensité initiale supérieure. Les transitions
ponctuelles restent gérées par Compose. Les animations sont suspendues hors
RESUMED, en économie Android et en économie utilisateur. Ce plafonnement ne
constitue pas une validation thermique/énergétique.

Le mode démonstration choisit heure et météo dans les paramètres, avec un
marquage explicite. Il n’écrit jamais dans LocalWeatherStore. Les dates des
enregistrements financiers suivent toujours l’heure locale réelle. Sans démo,
dernière observation météo identifiée/horodatée ou ambiance neutre. Aucun
fournisseur météo actif et aucune météo déduite du module sélectionné.

## Prévisualisation HTML

`preview/index.html`, `style.css`, `app.js`, `design-data.js` : statique, sans
service distant obligatoire, données de démonstration dans localStorage.
Les données Android ne sont jamais importées dans cette démonstration.
Le SVG reprend le décor Canvas ; les dispositions, transitions et animations
restent des implémentations différentes. Aucune parité pixel à pixel revendiquée.

Ouvrir index.html avec ses fichiers voisins dans un navigateur compatible,
ou servir le dossier : `python3 -m http.server 8000 --directory preview`.
Pour un Android relié en USB : `adb reverse tcp:8000 tcp:8000`, puis ouvrir
http://127.0.0.1:8000 sur le téléphone. Ceci n’est pas un hébergement publié.

Les helpers de publication Sites ne sont pas montés dans cet environnement.
Aucune publication ni audience publique n’a donc été créée. La CI fournit
un ZIP HTML utilisable et des captures navigateur ; aucun faux lien hébergé.

## Références et limites de qualification

`JARVIS_reference_paysage.jpg` et `JARVIS_reference_portrait.jpg` sont absentes
des fichiers accessibles et de la branche auditée. Le dossier
`docs/design/references/` indique les originaux attendus ; aucun substitut n’est
présenté comme référence officielle. La fidélité et le comparatif avec ces images
restent BLOQUÉS par leur absence. L’illustration actuelle reste provisoire.

Les résultats effectifs sont consignés dans `ui-01b-results.md`.
Tests physiques POCO X8 Pro / Redmi Pad SE : à réaliser. Préparer portrait/paysage,
texte 200 %, TalkBack, fournisseurs SAF locaux, export/lecture externe, arrêt forcé
du processus, données nombreuses et arrière-plan/reprise. Comparer économie,
équilibré et qualité sur 15 minutes à luminosité/température initiales identiques,
enregistrer PSS/CPU/frames, batterie et température. Aucune conclusion thermique
ou d’autonomie sans ces mesures réelles. PR maintenue en brouillon, sans fusion.

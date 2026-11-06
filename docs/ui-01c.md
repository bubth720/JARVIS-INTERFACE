# UI-01C — architecture et contrat d’interaction

L’inventaire initial et ses corrections prévues sont dans
[ui-01c-interactions.md](ui-01c-interactions.md). Les preuves effectives et les
limitations sont dans [ui-01c-results.md](ui-01c-results.md) ; la présence d’un scénario ne vaut
pas réussite tant que son résultat n’est pas vérifié.

## Navigation et actions

Le logo, la maison, la grille, le dossier, le calendrier, les statistiques,
les paramètres et l’alimentation ont des actions distinctes. Supervision et
avatar restent accessibles. Une pile de 40 routes maximum remplace le retour
systématique à l’accueil ; elle est conservée localement, ainsi que le module
sélectionné. Le Retour Android et le bouton Retour dépilent la route précédente.
Les barres de navigation défilent lorsque la hauteur disponible ne permet pas
des cibles tactiles d’au moins 48 dp. Aucun panneau purement informatif ou
élément de mobilier n’a d’apparence de lien.
Les libellés latéraux reviennent à la ligne dans la largeur disponible. Les
captures ont permis de corriger leur rognage par la forme arrondie du bouton ;
les tests vérifient désormais aussi l’absence de débordement du texte. Les
modules gardent des cibles d’au moins 48 dp ; une rangée trop étroite défile
horizontalement au lieu de comprimer ses boutons. Le HTML conserve la même
règle en pixels CSS, y compris lorsque le paysage est forcé sur un téléphone.

Le menu alimentation propose Annuler, Veille, Reprendre et Fermer l’application.
La veille retire le décor animé de la composition, conserve données/navigation
et permet aussi la reprise par Retour Android. Fermer appelle uniquement
finishAndRemoveTask sur l’activité JARVIS. Aucune commande d’extinction système,
aucun service de fond ni réveil automatique n’est ajouté.

## Données locales et migration

La base `ui01b-workspace.db` passe de la version 1 à 2 par création des tables
`steps` et `history`. Les entrées et journaux existants sont conservés. Les
écritures d’entrée et de son historique/journal sont transactionnelles ; une
suppression confirmée retire uniquement l’entrée choisie et ses lignes filles.
Les fichiers originaux référencés ne sont jamais supprimés par l’application.

Les cinq types d’enregistrements peuvent être modifiés par ID, sans duplication,
ou supprimés après confirmation. La date et le montant sont validés. Terminer,
rouvrir, exclure et réinclure restent réversibles. Les notes préparent L’ATELIER
sans le connecter. Création conserve travaux, références et étapes sans moteur
multimédia caché. La progression générale est manuelle ; les étapes ont un état
indépendant et un historique. Les 200 dernières opérations par entrée sont
consultables. Le journal diagnostic global reste borné à 100 événements et
ne reprend pas les titres ou textes personnels.

Le graphique financier est calculé à partir des mouvements du mois courant
non exclus : revenus positifs, dépenses négatives, calcul BigDecimal natif.
Le tableau Statistiques reprend les progressions enregistrées. Aucun graphique
n’est prérempli ou présenté comme prévision. Les valeurs restent lisibles en
texte et en description d’accessibilité.

Le calendrier montre les échéances réelles des entrées hors Finance, par mois
et par jour ; les dates des opérations financières ne deviennent pas des
échéances. Documents liste les références choisies et ouvre un lecteur Android
compatible ou affiche une erreur. Import et export passent par les sélecteurs
Android, sans permission globale de stockage.
Le sélecteur des jours adapte son nombre de colonnes à la largeur disponible
pour garder des cibles de 48 dp (48 pixels CSS dans la démo). Il ne présente
pas d’en-têtes de jours de semaine lorsque les sept colonnes ne tiennent pas.

## Ambiance

Les couleurs utilisent toujours la palette commune. Le mobilier est inchangé.
Le panneau permet de revenir explicitement au cycle automatique ; les réglages
de démonstration restent séparés du dernier relevé météo identifié. Un badge
de démonstration apparaît sous le décor lorsque la météo est simulée.
Aucun fournisseur météo ou autre module n’est activé automatiquement.

## HTML autonome et parité honnête

`preview/jarvis-ui01c.html` contient CSS, palette et JavaScript incorporés.
Il est généré par `python3 scripts/build_ui01c_preview.py` et contrôlé avec
`--check`. Aucun serveur, module JavaScript distant, police ou requête réseau
n’est nécessaire pour l’utiliser. Le dossier preview conserve aussi les
sources séparées pour maintenance. Ouvrir le fichier autonome plutôt que le
seul index.html sans ses fichiers voisins.

La navigation, les CRUD de démonstration, le budget/graphique, les étapes,
historiques, échéances, statistiques, ambiances et veille suivent le même
contrat métier. Les données sont explicitement de démonstration et isolées
dans le stockage du navigateur ; aucune donnée privée Android n’est importée.
Les montants de cette démo sont limités aux entiers exacts JavaScript par
opération, et les totaux utilisent BigInt. Les fichiers choisis sont limités
à 1 Mio et aux formats annoncés ; leur téléchargement est réel, sans prétendre
disposer d’un lecteur Android.

| Capacité | Android | Navigateur |
| --- | --- | --- |
| Fichiers | URI durable choisie dans SAF, lecteur externe | Fichier choisi via FileReader, stockage démo et téléchargement |
| Permissions système | Liste déclarée et paramètres de cette application | Indisponibles, texte explicite, aucun bouton fictif |
| Confidentialité | FLAG_SECURE volontaire | Masque visuel de la démo uniquement ; aucune protection anti-capture revendiquée |
| Diagnostic | Build, PackageManager, ActivityManager, Debug, StatFs | Informations de fenêtre et journal de démonstration ; aucune RAM Android inventée |
| Export | Document choisi dans Android | Téléchargement texte du diagnostic navigateur |
| Fermer l’application | Termine uniquement l’activité JARVIS | Indisponible ; pas de fausse fermeture d’onglet |
| Heure automatique | Horloge locale et dernier cache météo identifié | Horloge du navigateur et météo neutre, démo clairement signalée |

Quand le navigateur refuse l’historique sur une URL locale particulière, les
boutons utilisent la pile interne. Quand il refuse le stockage local, l’interface
annonce un fonctionnement en session seule ; aucune persistance réussie n’est
simulée. La disposition automatique suit le rapport largeur/hauteur, y compris sur tablette
portrait, et les dispositions forcées restent prioritaires. Les tests vérifient
le nombre réel de colonnes des panneaux et des modules.
Le mode fichier est vérifié dans Chromium sans réseau ; les restrictions
propres aux gestionnaires de fichiers et navigateurs Android sont à distinguer.
La publication hébergée n’est pas requise pour ce livrable autonome et n’est pas
déclenchée ici.

## Vérifications

La suite native comprend navigation dans les deux orientations, CRUD, migration,
étapes/historique, couleurs effectivement dessinées, contrôles d’ambiance,
confidentialité et frontières d’intents, veille et fermeture de cette activité.
Un scénario supplémentaire vise le vrai sélecteur de documents Android.

`ProcessRestartProbe`, exclu de la suite ordinaire, est lancé deux fois en CI
avec arrêt forcé entre les deux. Il vérifie que le PID a changé et que données,
route, pile, sélection et intensité sont relues depuis le stockage. Les sorties
write/verify doivent contenir un test réussi ; un simple code retour adb ne suffit pas.

La suite navigateur exerce les quatre formats téléphone/tablette portrait/paysage,
les formulaires, annulations et suppressions, l’historique navigateur, les fichiers,
l’export et le fichier autonome sans réseau après fermeture et réouverture de page.
Les valeurs de test sont nommées TEST ou DEMO ; elles ne sont pas des données
préchargées dans l’APK.

Les performances physiques et l’intégration de tous les fournisseurs/lecteurs
Android restent à qualifier sur les appareils concernés. Ces limitations ne
doivent pas être confondues avec une réussite des scénarios automatisés. Aucun
autre module JARVIS, aucune branche main ni moteur externe n’est modifié.

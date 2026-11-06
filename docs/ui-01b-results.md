# UI-01B — preuves et limites, 6 novembre 2026

**Livraison partielle. PR #16 maintenue en brouillon.**

## Révision Android et compilation réelle

Code Android testé : `f89a8b2da86dc934446ad3715ccef95a935c3ad4`.
Le commit `44d1815` suivant change seulement HTML et documentation ;
`git diff f89a8b2 44d1815 -- app` est vide.

Compilation et rapports :
https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42291322741

- `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest` réussis.
- Six tests JVM : zéro échec/erreur/ignoré ; montants exacts en centimes,
  dates ISO réelles, plages horaires et ancien état local.
- Lint : zéro erreur, 49 avertissements ; rapport complet conservé.
- APK `com.jarvis.interfaceapp`, version `0.2.0-ui01b`, code 2,
  minimum API 26, cible API 35 ; aucune permission INTERNET ajoutée.
- Signature debug v2 vérifiée par `apksigner verify --verbose`.
- SHA-256 du fichier APK téléchargé et recalculé localement :
  `e9197579c0de7947e0c37f4b32b87b8814f74f27dc9fe3e8d889a92d9bbc4ef0`.

APK, signature, empreinte, métadonnées et rapports :
https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42291322741/artifacts/13540640399
Le ZIP contient `outputs/apk/debug/app-debug.apk` et `reports/ui-01/`.

## Neuf tests Android réussis sur chaque émulateur

Exécution :
https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42291311374

| Profil générique API 34 AOSP x86_64 | Dimensions constatées | Densité | Résultat XML |
| --- | --- | --- | --- |
| Téléphone | 1080 × 1920, paysage 1920 × 1080 | 420 dpi | 9 tests, 0 échec/erreur/ignoré |
| Tablette | 1200 × 1920, paysage 1920 × 1200 | 240 dpi | 9 tests, 0 échec/erreur/ignoré |

Le téléphone était demandé à 1080 × 2400 ; l’émulateur limite l’override à
1080 × 1920. Aucun de ces profils n’est un appareil POCO ou Redmi physique.

Couverture réellement exécutée :

- Lancement, supervision, avatar, ouverture et retour des huit modules.
- Retour des paramètres, restauration de l’écran après recréation.
- Préférence économie conservée et Retour Android.
- Cache météo identifié conservé après réouverture.
- Simulation neige sans modification du cache réel ; rotation aller/retour
  en conservant **la même instance d’activité** et l’écran Paramètres.
- Fermeture/réouverture de la base pour les cinq types d’entrées ; champs
  montants/progression/état conservés et journal sans contenu des enregistrements.
- Saisie effective dans les cinq formulaires Compose, écriture SQLite attendue,
  recréation de l’activité, puis vérification des données enregistrées.

Les fixtures sont explicitement nommées TEST FIXTURE. La base dédiée du test
de dépôt est nettoyée. Les captures sont faites après le nettoyage du runner,
réinstallation de l’APK et vérification du processus ; elles ne montrent pas
de données utilisateur fictives présentées comme réelles.

Le Wi-Fi et les données mobiles sont désactivés par le script avant les tests.
L’absence de permission INTERNET est indépendante de ces réglages radio.
Ce protocole ne constitue pas un essai physique en mode avion.

## Captures réelles inspectées et performance

Téléphone :
https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42291311374/artifacts/13539528660

Tablette :
https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42291311374/artifacts/13539778072

Les quatre PNG portrait/paysage ont été ouverts et inspectés. Sur tablette,
grille 4 × 2 en portrait, rangée de huit modules en paysage. Sur téléphone,
défilement nécessaire, particulièrement en paysage ; certains éléments sont
sous la zone visible initiale. Aucun chevauchement constaté sur les vues
inspectées au texte standard. La fidélité artistique n’est pas attestée.

Snapshots après lancement/rotation et dix secondes de collecte :

| Mesure | Téléphone | Tablette |
| --- | --- | --- |
| PSS processus | 60 945 Ko | 60 679 Ko |
| RSS processus | 167 944 Ko | 168 192 Ko |
| Swap rapporté | 0 Ko | 0 Ko |
| Frames Gfxinfo | 151 | 152 |
| Janky frames | 146 (96,69 %) | 152 (100 %) |
| Janky frames, métrique legacy | 1 (0,66 %) | 152 (100 %) |
| Journal de crash collecté | vide | vide |

**Fluidité non qualifiée : les taux de jank élevés ne permettent pas de valider
l’objectif fluide.** L’écart des métriques téléphone et le rendu logiciel de
l’émulateur demandent investigation et essais physiques, pas une affirmation de
réussite. Le plafond logiciel des animations (15/30 mises à jour/s) ne garantit
pas un temps de frame correct.

Snapshots CPU/batterie inclus. Le relevé CPU global ne fournit pas ici de mesure
fiable isolée de l’application. Aucune comparaison énergétique, autonomie ou
température réelle n’est mesurée. Aucun crash n’est observé dans ces journaux,
sans garantie sur tous les usages futurs.

## Prévisualisation HTML

Source dans `preview/`, palette commune générée et vérifiée par
`python3 scripts/generate_ui01b.py --check`. Syntaxe JavaScript vérifiée.
La CI Playwright vérifie les huit modules, une saisie persistante après
rechargement, l’affichage littéral du texte utilisateur, la simulation 22 h/neige,
les vues responsive et l’absence de débordement horizontal sur les tailles testées.

La révision HTML `44d181581b9d17febe5e85e41af59aab1bd1079a` ajoute le paysage
forcé même sur écran de téléphone. Son scénario Playwright réussit, y compris
les deux bascules sur un viewport de 412 × 915.
Exécution : https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42292301472
ZIP HTML final :
https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42292301472/artifacts/13540551851 Les captures HTML sont dans
`test-results/html-portrait.png` et `html-landscape.png` de l’artefact.
La géométrie est reprise en SVG, sans revendiquer une identité pixel à pixel
avec le Canvas natif. Aucun fichier Android privé ou contenu personnel exporté.

Pas d’URL hébergée publiée : les helpers Sites nécessaires ne sont pas disponibles
dans cet environnement. Le ZIP HTML est un livrable local, pas un faux site en ligne.

## Comparatif officiel bloqué et fonctions restant à qualifier

Les deux JPG officiels sont absents des sources accessibles. Le dossier
`docs/design/references/` consigne les fichiers attendus. Aucun original inventé,
aucun score de ressemblance ni comparatif factice n’est produit. L’illustration
reste provisoire : UI-01B n’est pas terminée graphiquement.

Restent également ouverts : essais physiques POCO X8 Pro / Redmi Pad SE ;
fluidité, CPU par processus et batterie/température ; texte agrandi/TalkBack ;
arrêt forcé et redémarrage du processus ; toutes les variantes horaires/météo ;
gestes de progression et édition longue sur grands volumes ; import SAF, lecture
par applications externes, export et FLAG_SECURE sur les appareils concernés.
Ces derniers contrôles sont développés mais ne sont pas tous couverts par les
neuf tests et ne sont pas annoncés intégralement validés.

Les premiers essais UI-01B ont échoué sur la rotation et sur la préparation de
l’accueil restauré. Ajout de `screenLayout` aux changements gérés et retour
explicite à l’accueil dans les tests ; les exécutions ci-dessus sont les reprises
réussies. Un contrôle de génération HTML manquait initialement un fichier inutilisé :
le contrat a été réduit à la palette réellement partagée.

Code complet et procédure physique dans `docs/ui-01b.md`. Aucun autre module
JARVIS modifié, aucune suppression de développement existant, aucune fusion.

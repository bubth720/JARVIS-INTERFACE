# UI-01C — résultats et périmètre de qualification

Interactivité locale livrée sur `feat/ui-01-native-cockpit`, PR [#16 en brouillon](https://github.com/bubth720/JARVIS-INTERFACE/pull/16).
Aucune fusion ni connexion CORE, LLM, VOICE ou AVATAR. Les anciens développements
et documents restent conservés. L’inventaire [avant/après correction](ui-01c-interactions.md)
associe les éléments interactifs à leurs scénarios ; [l’architecture](ui-01c.md)
décrit la migration et la parité Android / navigateur.

## Révision, compilation et APK

Révision exécutée : `9a43dad6a112f0a40323a93c0034d55aa02c6397`.
Les ajouts documentaires ultérieurs ne modifient ni l’application, ni les tests,
ni la prévisualisation. Les exécutions faisant foi sont liées ci-dessous.

- [Compilation Android, JVM, lint et navigateur](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42313231521).
- [Installation, instrumentation, redémarrage et captures Android](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42313231538).
- Commandes exécutées : `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest`, puis `connectedDebugAndroidTest` sur chaque émulateur.
- Paquet `com.jarvis.interfaceapp`, version **0.3.0-ui01c**, code **3**, minimum API **26**, cible **35**.
- Six tests JVM : zéro échec, erreur ou test ignoré. Lint : **0 erreur, 53 avertissements**.
- Signature de développement v2 vérifiée par `apksigner` et APK installé par `adb install -r`.

**APK conseillé pour cette livraison :** [archive téléphone testée](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42313231538/artifacts/13549937134),
fichier `app/build/outputs/apk/debug/app-debug.apk`. Cette archive contient
l’APK effectivement installé dans le protocole d’essai, ainsi que sa signature,
son empreinte, ses métadonnées et les preuves. Empreinte recalculée après téléchargement :

```text
f8dcd0216eab6d4fbedb6347c53be4a4d1e7fa2cd9cdacb95d3185c68014f638
```

L’[archive tablette](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42313231538/artifacts/13549403404) contient également son APK installé ;
les clés debug sont produites par les runners, ce qui peut donner des signatures
et empreintes différentes malgré les mêmes sources. Une mise à jour d’une
installation existante nécessite la même clé : ne pas désinstaller une version
contenant des données pour contourner une différence de signature sans avoir
préservé ces données. La migration SQLite v1→v2 est testée par fixture réelle,
pas par installation physique d’une ancienne APK différemment signée.

L’[archive de compilation indépendante](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42313231521/artifacts/13548969088) fournit aussi l’APK
build, les six résultats JVM et le rapport lint complet. Les liens GitHub Actions
peuvent nécessiter une connexion et sont soumis à la rétention des artefacts.

## Résultats Android réellement exécutés

| Profil générique AOSP API 34 x86_64 | Dimensions constatées portrait / paysage | Densité | Suite normale | Sonde avant / après arrêt forcé |
| --- | --- | --- | --- | --- |
| Téléphone | 1080 × 1920 / 1920 × 1080 | 420 dpi | 18 tests, 0 échec/erreur/ignoré | `OK (1 test)` / `OK (1 test)` |
| Tablette | 1200 × 1920 / 1920 × 1200 | 240 dpi | 18 tests, 0 échec/erreur/ignoré | `OK (1 test)` / `OK (1 test)` |

Le profil téléphone est demandé à 1080 × 2400 ; l’émulateur limite effectivement
l’override à 1080 × 1920. Ce ne sont ni un POCO X8 Pro ni une Redmi Pad SE physiques.
Les XML, sorties d’instrumentation, dimensions et densités sont dans les archives.

Couverture exécutée, avec fixtures identifiées TEST et sans données privées :

- Chaque icône de navigation, le logo, les huit modules, retours affiché/Android,
  pile conservée et chemins imbriqués, dans les deux orientations. Les libellés
  latéraux sont contrôlés par le résultat de mise en page du texte, pas uniquement
  par la réussite d’un clic. La rotation conserve la même activité dans le test dédié.
- Création via les cinq formulaires Compose ; édition, validation, annulation,
  suppression confirmée ou annulée, terminer/rouvrir et exclure/réinclure.
- Mouvements financiers des deux signes, budget, montants exacts et graphique
  calculé ; projets et travaux créatifs, progression, étapes et historique réel.
- Calendrier, mois/jours/filtres et accès à l’échéance ; documents ; statistiques
  et accès aux projets. Dimensions réelles des boutons de jour et des huit modules ≥48 dp aux formats testés.
- Six météos de démonstration, heure, retour au cycle automatique, cache météo
  identifié intact, trois profils, intensité, économie, éclairage. Pixels du décor
  vérifiés pour les accents Finance, Création, Sécurité et Projets.
- Sécurité : permission déclarée, intent vers paramètres de l’application, drapeau
  réel `FLAG_SECURE` activé puis désactivé. Diagnostic actualisé/exporté, contenu
  réel lu dans le fichier de test, sans le texte privé des enregistrements.
- Menu alimentation, annuler, veille/reprise et fermeture de la seule activité
  JARVIS ; le test attend effectivement sa destruction.
- Migration additive d’une vraie base v1, fermeture/réouverture SQLite, recréation
  d’activité et deux sondes séparées par `am force-stop`. La seconde sonde contrôle
  un PID différent, texte/progression enregistrés, route, pile, module et intensité.

**Fichiers et frontières externes :** un test ouvre réellement DocumentsUI AOSP,
sélectionne un fichier local créé pour l’essai, vérifie l’autorisation durable et
le contenu lisible, puis supprime la référence via l’interface et vérifie que
l’original existe toujours. Les autres lecteurs et les paramètres Android sont
contrôlés à la frontière d’intent avec ActivityMonitor ; l’export écrit réellement
dans une URI locale fournie au contrat. Ce n’est pas une validation de tous les
fournisseurs de fichiers, lecteurs tiers ou écrans système des constructeurs.

Wi-Fi et données mobiles sont désactivés avant l’essai ; l’APK ne déclare aucune
permission INTERNET. Ce protocole ne constitue pas un essai physique en mode avion.
Les journaux de crash collectés sont vides sur les deux profils, sans prétendre
couvrir tous les usages futurs.

## Prévisualisation HTML autonome

[Ouvrir/télécharger le fichier autonome dans le dépôt](../preview/jarvis-ui01c.html)
ou récupérer l’[archive HTML et captures](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42313231521/artifacts/13549996239), puis ouvrir
`preview/jarvis-ui01c.html` dans un navigateur. L’affichage GitHub du code source
ne constitue pas un hébergement de démonstration. Aucun site n’a été publié.

**Six tests Playwright Chromium réussis** : quatre formats (412 × 915, 915 × 412,
800 × 1280, 1280 × 800), un scénario CRUD/fichiers/collections et un scénario
fichier autonome `file://` hors ligne. Ce dernier ferme puis rouvre la page dans
le même contexte de stockage, relit données/réglages/navigation, et vérifie
qu’aucune requête HTTP n’est émise. Les CRUD sont aussi vérifiés après rechargement.

La suite teste les huit modules, chaque navigation et ses retours, simulations
portrait/paysage, trois profils, intensité/éclairage, cycle/heure/six météos,
accents, formulaires/annulations/suppressions, budget/graphique, étapes/historique,
calendrier/statistiques, vrai choix et téléchargement d’un TXT, rejet d’un format
non compatible, export diagnostic, confidentialité visuelle et veille/reprise.
Les libellés latéraux sont bornés à leurs boutons et les jours/modules mesurés ≥48 px CSS, même en paysage forcé. Les rangées
trop étroites défilent horizontalement pour conserver ces dimensions.

Les données restent explicitement **de démonstration**, dans ce navigateur. Ni
fermeture native, ni permissions Android, ni protection anti-capture native ne
sont simulées. Les restrictions propres aux navigateurs et gestionnaires de
fichiers Android restent à tester physiquement ; si le stockage est refusé,
un avertissement annonce la session seule. Le test hors ligne ne prétend pas
fermer tout le navigateur ou redémarrer le téléphone.

## Captures et contrôle visuel

Les quatre captures Android et les quatre captures navigateur finales ont été
ouvertes et inspectées. Les originaux complets restent dans les archives des runs.
Les fichiers suivants sont des captures réelles, sans reconstruction de capture :

| Format | Android | HTML démonstration |
| --- | --- | --- |
| Téléphone portrait | [PNG](evidence/ui-01c/phone-portrait.png) | [PNG](evidence/ui-01c/html-phone-portrait.png) |
| Téléphone paysage | [PNG](evidence/ui-01c/phone-landscape.png) | [PNG](evidence/ui-01c/html-phone-landscape.png) |
| Tablette portrait | [PNG](evidence/ui-01c/tablet-portrait.png) | [PNG](evidence/ui-01c/html-tablet-portrait.png) |
| Tablette paysage | [PNG](evidence/ui-01c/tablet-landscape.png) | [PNG](evidence/ui-01c/html-tablet-landscape.png) |

L’inspection a révélé des libellés latéraux rognés : boutons rectangulaires,
largeur adaptée et retour à la ligne corrigent ce défaut ; le test de débordement
le couvre maintenant. Les captures ont aussi révélé que le seuil de largeur HTML
conservait une disposition paysage sur tablette portrait : l’orientation du
viewport et le comptage des colonnes effectives corrigent et couvrent ce défaut.
Le téléphone nécessite du défilement, surtout en paysage.
La tablette présente une rangée de huit modules en paysage et une grille 4 × 2
en portrait. Les panneaux gardent leur disposition selon l’orientation.
L’absence de chevauchement n’est revendiquée que pour les vues inspectées et
les contrôles mesurés, au texte standard des profils testés.

Les originaux graphiques officiels ne figurent toujours pas dans le dépôt :
voir [leur état documenté](design/references/README.md). Aucun comparatif de
fidélité artistique ne peut être certifié sans ces images. Le décor Canvas/SVG
reste provisoire ; cette livraison qualifie les interactions et leur cohérence,
pas la reproduction finale des références. Le glyphe Unicode de l’alimentation
apparaît comme caractère de remplacement sur ces émulateurs ; son libellé reste
lisible et son action est testée. Une iconographie vectorielle reste à finaliser
avec la direction artistique.

## Performances et qualification physique restante

Snapshots après lancement/rotation et dix secondes de collecte Gfxinfo :

| Mesure brute | Téléphone | Tablette |
| --- | --- | --- |
| PSS processus (Ko) | 61 234 | 61 894 |
| RSS processus (Ko) | 168 976 | 170 096 |
| Swap rapporté (Ko) | 0 | 0 |
| Frames Gfxinfo | 151 | 151 |
| Janky frames | 143 (94.70%) | 148 (98.01%) |
| Janky frames, métrique legacy | 1 (0.66%) | 78 (51.66%) |
| Journal de crash collecté | vide | vide |

Les [compteurs structurés et empreintes](evidence/ui-01c/qualification.json)
et les rapports complets CPU, mémoire, frames et batterie sont conservés
dans les archives ; les relevés mémoire/frames sont aussi copiés avec les captures.
**Fluidité non qualifiée : les taux de jank mesurés ne permettent pas de valider
l’objectif fluide.** Les métriques legacy et modernes divergent sur ce rendu
émulé ; elles restent présentées telles quelles. Le relevé CPU global ne donne
pas ici une mesure fiable isolée de l’application. Aucun essai thermique,
autonomie ou consommation énergétique réelle n’est attesté.

Essais physiques à exécuter sur **POCO X8 Pro** et **Redmi Pad SE** : installer
l’APK compatible avec la signature en place sur un appareil de test, rejouer
l’inventaire dans les quatre orientations/formats, vérifier fichiers/lecteurs
et permissions constructeur, puis mesurer PSS/CPU/frames et consommation/température
sur durées comparables (économie/équilibré/qualité, actif/veille/arrière-plan).
Conserver les mesures et versions d’appareil ; ne pas extrapoler celles des
émulateurs. Tester aussi texte agrandi, TalkBack et stockage local du HTML Android.

## Fonctionnalités et limitations

Opérationnels dans le périmètre testé : navigation complète, cinq espaces CRUD
persistants, étapes/historique, finance calculée, documents et échéances,
statistiques, diagnostic local/export, préférences, sécurité disponible,
veille/fermeture Android, ambiances et prévisualisation autonome interactive.

Restent : qualification physique/énergie/fluidité, fidélité aux originaux non
présents, validation de tous les lecteurs/fournisseurs et navigateurs Android,
ainsi que les intégrations externes explicitement non connectées. La base locale
n’ajoute pas de chiffrement applicatif ; la protection du stockage dépend
actuellement d’Android et du verrouillage de l’appareil. Aucune validation finale
utilisateur, fusion, publication hébergée ou activation sensible n’est effectuée.

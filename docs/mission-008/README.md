# Mission 008 — cockpit Android V2

Prototype volontaire sur la base 006, sans changement des quatre panneaux ni du
stockage historique. L’[audit de réutilisation](AUDIT.md) précède le développement ;
les [résultats effectifs](RESULTS.md) distinguent compilation, émulateurs et matériel.

## Utilisation

1. Démarrer : le dashboard historique s’ouvre avec ses dispositions sauvegardées.
2. Paramètres → **Tester le cockpit V2**. Aucun mode n’est enregistré.
3. Saisir dans **Texte local**. Le champ vit en RAM (2 000 caractères maximum).
   **Terminé** au clavier retire le focus et ferme le clavier, sans envoi ni réponse.
4. **Voir une fiche de démonstration** ouvre volontairement un exemple. Les
   sources sont explicitement absentes. **Fermer la fiche** la referme.
5. La roue circulaire unique ouvre/ferme le menu : dashboard historique,
   paramètres ou fiche de démonstration. CORE, SERVICES et VOICE sont désactivés.
6. Retour Android ferme le menu, puis la fiche, puis revient au dashboard.
   **Retour** en haut et la destination Dashboard quittent directement V2.
7. Quitter V2 efface le brouillon. Une rotation garde la session en cours ; un
   arrêt du processus reprend l’ancien dashboard, sans restaurer le texte.

Les contrôles d’envoi et de microphone restent indisponibles, même avec du texte.
Leur état et sa raison sont exposés à l’accessibilité et visibles à l’écran.
Aucune application externe, requête, capture audio ou réponse JARVIS n’est simulée.

## Composants

| Composant | Mise en œuvre | Contrat futur / limite |
| --- | --- | --- |
| Avatar à gauche | Canvas statique, fallback vectoriel AVA-04 adapté et borné | Slot Compose remplaçable ; aucun contrôleur ou moteur d’avatar |
| Sphère centrale | Dessin 2D statique, ellipses et nœuds décoratifs | Slot Compose remplaçable ; aucune rotation, données de connaissances ou 3D |
| Fiche à droite / sous la scène | Exemple volontaire avec titre, résumé, informations et état des sources | Fermée par défaut ; illustration facultative absente ; aucun résultat SERVICES |
| Barre basse | Texte contrôlé par la session RAM ; boutons indisponibles décrits | Aucun port CORE/VOICE ni historique de conversation |
| Roue basse à droite | Un seul bouton circulaire et DropdownMenu natif | Deux destinations réellement disponibles ; fonctions futures désactivées |

`cockpitv2/CockpitSession.kt` ne dépend que de StateFlow. HomeViewModel garde
cette session hors du contrôleur des dispositions. `JarvisApp.kt` ajoute le
routage volontaire et l’accès depuis Paramètres. MainActivity, HomeState,
HomeAction, DashboardGrid, DashboardController, LayoutCodec, le repository et
le manifeste restent identiques à 006. Le mode, le brouillon, la roue et la fiche
ne passent dans aucun SavedStateHandle ni sauvegarde. Les états de défilement
standard de Compose ne portent aucune de ces données ; aucune conversation
ou préférence personnelle n’est sérialisée.

`CockpitV2Screen` reçoit deux slots `@Composable (Modifier) -> Unit`, avatar et
sphère. Un futur renderer peut remplacer leur contenu en gardant la navigation
et le stockage indépendants. L’actuel contrat AvatarSession reste conservé sans
implémentation nouvelle. Les responsabilités de cycle de vie d’un futur moteur
restent à définir/valider ; cette mission n’intègre aucun moteur.

## Adaptation et accessibilité

La composition large démarre à 840 dp disponibles en paysage : avatar à gauche,
sphère au centre et espace de fiche à droite. En fenêtre étroite, avatar et sphère
restent dans la scène et la fiche se place sous celle-ci. La fiche ne remplace
jamais la sphère. Le contenu central peut défiler si nécessaire. La fiche garde sa propre zone
défilante bornée ; en fenêtre étroite normale, l’ouverture révèle ses contrôles
sans remplacer la sphère.

En fenêtre de moins de 480 dp de haut ou avec un facteur de texte supérieur à
1,4, une page défilante garde tous les contrôles atteignables. Il n’est pas promis
que les cinq zones tiennent simultanément dans une très petite fenêtre avec
texte doublé. Les champs et actions suivent les insets système et du clavier.
Le rendu est statique ; seules les interactions normales des composants Android
et le repositionnement du défilement à l’ouverture/fermeture d’une fiche existent.
Aucune boucle d’animation permanente, capteur ou service de fond.

Les actions ont des libellés, les dessins une description, les groupes des titres
et les indisponibilités un état sémantique. Roue 56 dp, commandes 48 dp minimum.
La fiche et les boutons s’étendent verticalement pour le texte. Les tests utilisent
la sémantique Compose, du texte ×2 et les événements Android Tab/Entrée injectés
par Instrumentation. Les captures ne sont ni des reconstructions ni du HTML. Ils ne valent
pas essai de lecture vocale TalkBack sur appareil physique.

## Reproduire les validations

```sh
python3 scripts/verify_mission008_scope.py
./scripts/check.sh --no-daemon
./gradlew connectedDebugAndroidTest --no-daemon
```

Le garde-fou compare 13 fichiers protégés à la base exacte 006. La suite historique
est conservée. Les tests instrumentés réinitialisent leurs fixtures de disposition :
utiliser un émulateur ou appareil de test, pas un profil contenant des dispositions
personnelles. Le workflow `Cockpit V2 008 device evidence` installe réellement les
APK et capture le rendu natif de la racine Compose par PixelCopy après test, sur deux profils génériques API 34.
Il désactive Wi-Fi/données mobiles et ne dépend d’aucun service JARVIS.

Le workflow construit aussi la base 006 dans un worktree et vérifie une mise à
jour `adb install -r` signée avec la même clé de runner. Le script
`verify_cockpit008_upgrade.py` vérifie les octets JSON, géométries/verrous des deux
profils et extensions inconnues, puis le changement réel de PID, le retour au
dashboard et l’absence du texte éphémère après arrêt forcé. Il refuse les numéros
de série physiques et exige `--allow-test-fixture` ; il remplace les dispositions
de l’émulateur jetable uniquement.

## Essais physiques demandés

POCO X8 Pro et Redmi Pad SE ne sont pas assimilés aux profils génériques.
Installer uniquement sur un appareil de test, conserver les dispositions et
vérifier la signature : `adb install -r app-debug.apk` ou installer le fichier
APK depuis Android. Une ancienne APK signée avec une autre clé peut refuser la
mise à jour ; **ne pas désinstaller ni effacer les données pour contourner cela**.
Voir aussi le [guide de la base 006](../device-validation/guide-android.md).

- Vérifier d’abord les quatre panneaux et leurs placements/verrous sauvegardés.
- Ouvrir V2, manipuler roue/fiche/champ et les retours dans les deux orientations.
- Vérifier que les fonctions indisponibles n’ouvrent rien, sans fausse réponse.
- Ouvrir le clavier, activer texte agrandi, fenêtre partagée et TalkBack ; parcourir
  les titres, les descriptions et les états désactivés, puis Entrée/Tab au clavier.
- Quitter et rouvrir V2 : champ vide. Forcer l’arrêt, relancer : dashboard par défaut,
  placements intacts. Refaire hors ligne. Ne pas confondre rotation et mort du processus.
- Contrôler absence de chevauchement bloquant, accessibilité des zones par défilement
  et stabilité. Les résultats matériels/thermiques ne sont pas attestés par la CI.

## Proposition de mission 009 — soumise à validation

Consolider le prototype avec l’image officielle et les livrables 007, recueillir
les retours POCO/Redmi et TalkBack, puis ajuster proportions, typographie et
composition des fenêtres courtes. Définir ensuite un contrat de capacités
explicites pour les slots graphiques et les commandes, sans activer de moteur
ou service. Livrables proposés : comparatifs visuels, matrice d’accessibilité,
mesures sur les appareils et plan d’intégration validable séparément.
Aucun développement 009 n’est engagé par cette livraison.

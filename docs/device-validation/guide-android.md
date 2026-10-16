# Mission 006 — tester JARVIS INTERFACE sur Android

Ce guide concerne **POCO X8 Pro** et **Redmi Pad SE**. Aucun résultat physique
n’a encore été relevé depuis Codex. Remplir une grille par appareil ; un succès
sur émulateur ne valide pas le téléphone ou la tablette.

## 1. Installer sans ordinateur

1. Avec une connexion Internet, ouvrir dans le navigateur Android le
   [workflow de la branche mission 006](https://github.com/bubth720/JARVIS-INTERFACE/actions?query=branch%3Atest%2Finterface-device-006).
   Se connecter à GitHub si nécessaire. Choisir **Android**, terminé **Success**,
   branche `test/interface-device-006`, et noter le commit affiché. Sur mobile,
   utiliser « Version pour ordinateur » du navigateur si les artefacts sont cachés.
2. En bas du run, télécharger l’artefact **jarvis-interface-device-kit**. GitHub fournit
   une archive ZIP ; l’ouvrir avec Fichiers ou le gestionnaire de fichiers Android
   et extraire son contenu. Si l’archive ne s’ouvre pas, noter le problème ; un
   transfert USB du même APK depuis un ordinateur constitue une alternative.
3. Dans le dossier extrait, ouvrir **app-debug.apk**. Le kit contient aussi ce
   guide, les grilles, `SOURCE.txt` (commit) et `SHA256SUMS.txt` (empreintes).
   L’autre artefact `jarvis-interface-apks` contient un APK `androidTest` réservé
   aux tests automatisés : ne pas installer cet APK de tests.
4. Android peut demander « Autoriser depuis cette source » pour le navigateur ou
   gestionnaire de fichiers : autoriser cette application le temps de l’installation,
   puis désactiver cette autorisation après installation. Ne pas désactiver Play
   Protect pour contourner un blocage : relever le message exact si l’installation échoue.
5. Installer puis ouvrir **JARVIS INTERFACE**. L’application ne demande ni Internet,
   ni microphone, ni caméra, ni stockage externe. L’APK est une version de test
   signée avec une clé de développement, pas une version de publication.
6. Après téléchargement/installation, le réseau n’est plus nécessaire : activer le
   mode avion, couper aussi le Wi-Fi s’il reste actif. Ne lancer aucune autre app JARVIS.

**Si une version précédente est installée :** essayer la mise à jour proposée,
sans désinstaller et sans effacer les données. Les APK debug produits par des runs
CI différents peuvent avoir des signatures différentes. Si Android refuse la mise
à jour (« conflit avec un package », signature incompatible…), **arrêter et relever
le message**. Ne pas désinstaller pour forcer l’essai si une disposition doit être
conservée : cela supprimerait les données privées. La réinitialisation des panneaux
n’est pas une solution à un conflit de signature. Une stratégie de signature stable
reste à définir pour les futures distributions.

Le commit du run et les résultats locaux ne constituent pas une preuve que ce même
APK CI a été testé sur ton appareil. Toujours noter l’origine exacte de l’APK.

## 2. Préparer la fiche de test

Copier [la grille](grille-appareils.csv) ou le [modèle de retour](retour-utilisateur.md).
Noter modèle exact, version Android/MIUI/HyperOS, orientation, taille de texte,
affichage agrandi ou non, batterie, chargeur branché ou non, date et commit du run.
Ne pas effacer les dispositions existantes avant les essais de conservation.
Pour les essais destructifs de réinitialisation, noter/capturer d’abord les positions.

Résultats autorisés : **OK**, **ÉCHEC**, **BLOQUÉ**, **NON TESTÉ**. « Sans anomalie
observée » n’équivaut pas à une mesure de CPU ou de mémoire.

## 3. Scénarios à réaliser sur chacun des deux appareils

| ID | Manipulation | Résultat attendu |
| --- | --- | --- |
| T01 | Installer ou mettre à jour, puis ouvrir depuis l’icône. | Accueil sans plantage ; origine/commit de l’APK notés. |
| T02 | En mode avion, fermer puis rouvrir. | Interface utilisable sans réseau et sans autre application JARVIS. |
| T03 | Parcourir tout le dashboard, y compris les parties défilantes. | Quatre panneaux : Avatar, État système, Applications JARVIS, Commandes. Avatar et accès externes restent provisoires. |
| T04 | Appuyer Modifier, puis Terminer ; essayer aussi Retour Android. | Poignées présentes uniquement en édition. En mode normal, les balayages ne déplacent rien. |
| T05 | En édition, glisser la poignée **Déplacer** d’Avatar d’une ligne dans un espace libre. | Aperçu aligné ; la nouvelle position est appliquée au relâchement. Un balayage du texte du panneau fait défiler sans le déplacer. |
| T06 | Depuis une disposition initiale, agrandir Avatar d’une ligne avec **Taille**, puis le réduire ; verrouiller avec ○. Essayer de déplacer/redimensionner, puis déverrouiller avec ●. | Taille validée au relâchement. Verrou : poignées désactivées ; aucune modification tant qu’il est verrouillé. |
| T07 | Proposer un chevauchement, une sortie de grille, puis une taille sous 3×3. | Refus annoncé, dernière disposition valide conservée, voisins inchangés. Ne pas réinitialiser les données pour ce test. |
| T08 | Déplacer/redimensionner/verrouiller, noter les cellules affichées en édition, attendre **Disposition enregistrée**, quitter avec Accueil, retirer l’app des récentes et rouvrir. | Même géométrie et mêmes verrous. Le retrait des récentes n’est pas une preuve de mort du processus. |
| T09 | Après **Disposition enregistrée**, ouvrir Paramètres Android → Applications → JARVIS INTERFACE → **Forcer l’arrêt**, confirmer, puis rouvrir par l’icône. Ne pas choisir Effacer les données. | Disposition identique ; accueil hors édition. Cet arrêt forcé est distinct d’une rotation ou du retrait des récentes. |
| T10 | Configurer deux dispositions différentes : fenêtre étroite puis large, si le système le permet ; revenir à la première. | Profils indépendants : compact 6×12, large 12×10 à partir de 700 dp **disponibles pour la grille**. Le nom de l’appareil ne choisit pas le profil. Une rotation de téléphone peut rester compacte. Si aucun changement de profil n’est possible, noter NON TESTÉ pour le second profil. |
| T11 | Tourner portrait/paysage, refaire T04–T07 ; essayer la fenêtre partagée si disponible. | Contrôles atteignables, grille défilable, pas de perte des cellules/locks. Le changement de profil peut afficher l’autre disposition sauvegardée. |
| T12a | Paramètres Android → Affichage → Taille du texte : valeur maximale ; refaire T03–T06, paramètres, statut de sauvegarde, portrait/paysage. Restaurer ensuite le réglage initial. | Titres/poignées lisibles ou accessibles ; aucun contrôle nécessaire définitivement hors d’atteinte. Défilement possible en fenêtre courte. Noter les textes tronqués et les cibles difficiles. |
| T12b | Activer TalkBack dans Accessibilité, noter la version. Explorer les quatre panneaux, Modifier, les poignées, les annonces et les paramètres. | Noms compréhensibles ; actions personnalisées de la poignée : déplacer d’une cellule / agrandir / réduire. La poignée verrouillée est désactivée. Test réel de lecture à faire : les assertions de sémantique automatisées ne prouvent pas la qualité des annonces vocales. |
| T13 | Paramètres de l’app → Restaurer la disposition par défaut : **Annuler** d’abord, puis recommencer et **Restaurer**. Attendre l’accusé, forcer l’arrêt et relancer. | Annuler ne change rien. Confirmer restaure les deux profils et leurs verrous, durablement, sans effacer d’autres données. |
| T14 | Après modification enregistrée, aller à l’accueil Android, ouvrir une autre application pendant une minute, revenir à JARVIS INTERFACE. | Pas de plantage ni perte de disposition ; aucun déplacement involontaire. Aucun service JARVIS requis. |

Pour T05–T07 à partir des valeurs initiales : Avatar est en haut à gauche ; une
ligne libre permet son déplacement/agrandissement vertical. L’édition montre les
cellules courantes et les minimums. Si la disposition est déjà personnalisée, choisir
un espace libre réellement disponible ; sauvegarder tes observations avant T13.

Avec TalkBack, toucher une fois pour sélectionner, toucher deux fois pour activer,
défiler à deux doigts. Les actions personnalisées sont dans le menu des actions
TalkBack (la commande exacte dépend de sa version). Ne pas exiger un glissement
à un doigt pour déplacer un panneau lorsque TalkBack intercepte ce geste.

En fenêtre courte ou avec un grand texte, **l’en-tête peut défiler indépendamment
de la grille** : balayer sa zone pour retrouver Paramètres/Modifier et le statut
de sauvegarde ; balayer la grille pour retrouver les panneaux. Attendre le statut
**Disposition enregistrée** avant les essais d’arrêt.

**À relever pendant les gestes :** nombre de tentatives pour attraper une poignée,
geste pris par le défilement, déplacement involontaire, taille minimale illisible,
statut de sauvegarde visible/atteignable, et toute erreur reproductible. Une photo
ou capture aide ; décrire surtout la séquence exacte et les réglages.

## 4. Performances : observations sans ordinateur

Utiliser le même APK et les mêmes réglages. Noter température ambiante approximative,
chargeur, niveau de batterie, économie d’énergie et fréquence d’écran configurée.

- Démarrage : après Forcer l’arrêt, chronométrer l’ouverture jusqu’au dashboard
  utilisable, 5 fois. Reporter les 5 durées ; il s’agit d’une approximation humaine,
  pas d’une mesure précise en millisecondes. Écarter séparément le premier lancement.
- Fluidité : déplacer/redimensionner pendant 30 s puis parcourir la grille ; noter
  les saccades et leur reproductibilité, pas un nombre de FPS estimé à l’œil.
- Thermique : noter sensation avant/après 5 min et éventuels avertissements système.
  Sans capteur lisible, laisser la température **NON MESURÉE** ; « tiède » n’est pas °C.
- Arrière-plan : laisser l’app en arrière-plan 5 min, vérifier absence de son/activité
  visible, puis reprendre. La page Batterie Android peut être consultée après une
  session suffisamment longue ; ses valeurs agrégées ne mesurent pas la seule app
  pendant quelques secondes.
- CPU et RAM : si Android ne fournit aucune valeur exploitable, inscrire
  **NON MESURÉS**. Une mesure fiable demande l’outillage optionnel ci-dessous.

## 5. Avec ordinateur (optionnel)

Le débogage USB n’est pas nécessaire pour les essais manuels. Sur appareil de test,
avec le SDK Android et son ADB :

```sh
adb devices -l
adb -s NUMERO_SERIE install -r app-debug.apk
adb -s NUMERO_SERIE shell am force-stop com.jarvis.interfaceapp
adb -s NUMERO_SERIE shell am start -W -n com.jarvis.interfaceapp/.MainActivity
adb -s NUMERO_SERIE shell dumpsys meminfo com.jarvis.interfaceapp
adb -s NUMERO_SERIE shell dumpsys cpuinfo
adb -s NUMERO_SERIE shell dumpsys thermalservice
adb -s NUMERO_SERIE shell dumpsys gfxinfo com.jarvis.interfaceapp reset
# Manipuler les panneaux pendant 30 secondes.
adb -s NUMERO_SERIE shell dumpsys gfxinfo com.jarvis.interfaceapp framestats
```

Noter TotalTime/WaitTime et le caractère froid/chaud du lancement ; `am start -W`
ne prouve pas à lui seul le moment où tous les contrôles sont utilisables. Pour
la RAM, relever Total PSS après stabilisation au repos, après 30 s de gestes puis
après 5 min en arrière-plan. Pour CPU, conserver l’en-tête donnant la fenêtre de
mesure et la ligne du processus ; ne pas confondre CPU système et CPU application.
Conserver les sorties brutes et les durées. `thermalservice` peut être absent ou
restreint ; la température batterie n’est pas celle du SoC. Pour la fluidité, noter
la fréquence d’écran et les limites des statistiques gfxinfo ; ne pas appliquer
un budget fixe 16,67 ms à tous les appareils.

Les tests instrumentés (`./gradlew connectedDebugAndroidTest`) réinitialisent les
dispositions pour isoler les scénarios : **appareil de test sans disposition à
conserver uniquement**. Le script `verify_storage_restart.py` est réservé aux
émulateurs jetables et refuse les téléphones. Ne pas l’adapter pour effacer leurs fichiers.

## 6. Retour attendu

Envoyer le modèle de retour rempli pour **chaque appareil**, avec les ID en échec,
les étapes exactes, les réglages et les mesures réellement relevées. Ne pas remplacer
NON TESTÉ par OK. La mission physique reste en attente de ces résultats ; aucune
mission suivante ne commence automatiquement.

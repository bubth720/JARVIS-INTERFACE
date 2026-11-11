# Mission 008 — résultats et livrables

Prototype natif livré sur `feature/interface-cockpit-v2-008`, [PR #22 en brouillon](https://github.com/bubth720/JARVIS-INTERFACE/pull/22).
Base exacte : `ebd6c5fc873054d3f04bb2388d53e407dc73b7b7`. Révision exécutée : `6e9dea5efa746c922d0d28a8573621e833892c41`.
Les ajouts documentaires suivants ne changent ni l’application ni les tests.
Aucune fusion, modification d’autre dépôt ou mission 009 engagée.

## Preuves de compilation

[GitHub Actions Android](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42995632626) : APK application et APK de tests
compilées, **66 tests JVM, zéro échec/erreur/ignoré**, Lint **0 erreur,
25 avertissements**. Les 59 tests historiques restent présents,
avec 7 tests de session V2. Les XML/HTML et le rapport Lint complet sont dans
[l’archive de rapports](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42995632626/artifacts/13844101679).
Les [deux APK de compilation](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42995632626/artifacts/13843598281) constituent
une paire signée par ce runner ; ils sont distincts des binaires des runners d’essai.

La compilation locale a également réussi : 66 tests JVM, Lint 0 erreur / 24
avertissements et APK instrumentée compilée. Le runtime Java initial n’avait pas
javac : un JDK Temurin 17 vérifié et le magasin TLS système ont été employés.
Les premières révisions ont révélé des défauts de contraste/cadrage et des échecs
de saisie, de focus et de défilement dans les nouveaux essais. Le bilan ci-dessous
concerne uniquement la révision exécutée indiquée, après corrections ; ces premières
exécutions ne sont pas présentées comme réussies.

## Installation et essais Android

[Exécution instrumentée et mise à jour réelle](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42995632542). Les deux
émulateurs sont AOSP **API 34 x86_64**, avec Wi-Fi et données mobiles désactivés.
Ce ne sont pas des appareils POCO X8 Pro / Redmi Pad SE physiques.

| Profil générique | Portrait / paysage constatés | Densité | Suite complète | Capture après réinstallation |
| --- | --- | --- | --- | --- |
| Téléphone | 1080×1920 / 1920×1080 | 420 dpi | 27 tests, 0 échec/erreur/ignoré | `OK (1 test)` |
| Tablette | 1200×1920 / 1920×1200 | 240 dpi | 27 tests, 0 échec/erreur/ignoré | `OK (1 test)` |

Les **20 tests historiques** (gestes, verrouillages, restauration, erreurs et
navigation du dashboard) sont conservés. Sept scénarios V2 s’y ajoutent :

| Critère | Vérification réellement exécutée |
| --- | --- |
| Dashboard par défaut et sauvegardes intactes | `oldDashboardRemainsDefaultAndV2NeverChangesSavedBytes` : deux profils non initiaux, verrouillage, octets JSON avant/après et retour aux quatre panneaux |
| Accès volontaire V2 et retour | Bouton Paramètres, retour affiché et dispatcher Retour ; anciennes routes réellement ouvertes |
| Cinq composants et fiche fermée par défaut | Avatar, sphère, barre et roue présents ; fiche absente jusqu’à l’action volontaire ; sources de démonstration explicites |
| Roue unique, ouverture/fermeture et destinations | `singleWheelAndAllAvailableDestinationsHaveRealRoutes` : un seul contrôle, menu, retour, dashboard/paramètres, exemple de fiche et trois entrées désactivées |
| Fiche ouverte/fermée, cockpit conservé | Bouton et menu de démonstration, fermeture et Retour ; sphère toujours présente, visible dans la fenêtre normale du test |
| Saisie RAM et rotation | `configurationRecreationRetainsOnlyLiveSessionAndInput` et `rotationKeepsDraftAndSphereWhileCapturingRealPortraitAndLandscape` : texte et mode retenus pendant rotation/recréation, texte vide après sortie/réouverture |
| Aucune action externe | `disabledCoreAndVoiceDoNotLaunchAnythingAndKeyboardDoneOnlyDismissesInput` : toucher du champ avec focus vérifié, touches sur contrôles désactivés, action IME Terminé, moniteur d’activités externes resté à zéro |
| Fenêtre courte et texte agrandi | `shortWindowAndDoubleTextKeepEveryActionReachableWithoutOverlappingTargets` : fenêtre Compose 360×320 dp, texte ×2, défilements de page/fiche, actions atteintes, commandes sans intersection |
| Accessibilité disponible | Descriptions, états désactivés et sources accessibles ; roue ≥48 dp ; `wheelAcceptsKeyboardActivationAndHasFingerSizedTargets` : événements Android Tab/Entrée injectés par Instrumentation |
| Hors ligne | Radios désactivées, manifeste inchangé sans permission INTERNET/RECORD_AUDIO et toutes ces interactions locales exécutées |

Les assertions de navigation ne valent pas essai vocal TalkBack, de clavier physique
ou de tous les formats possibles. L’agrandissement du texte est une densité Compose
contrôlée dans une fenêtre bornée ; ce n’est pas une validation matérielle du réglage
Android sur POCO/Redmi. Les fenêtres très courtes nécessitent du défilement.

## Mise à jour 006 → 008 et mort réelle du processus

La CI reconstruit l’APK de la base exacte dans un worktree, l’installe et ouvre une
fixture JSON v1 valide, puis installe la nouvelle APK **avec la même clé de test,
sans désinstallation**. Elle confirme le statut « Disposition enregistrée » et
l’identité exacte des octets des deux profils, géométries, verrous et extension
inconnue de fixture. Aucun champ 008 n’est ajouté au format par l’application.

Le texte est saisi par la voie Android réelle (`adb input text`) dans le champ
focalisé. Un arrêt `am force-stop` supprime le processus. Après relance : PID
différent, dashboard historique par défaut, texte non restauré après réouverture
V2 et JSON inchangé. Résultats structurés :
[téléphone](evidence/phone-upgrade.json), [tablette](evidence/tablet-upgrade.json).

Le script est réservé à un émulateur jetable et exige une autorisation explicite
de remplacer sa fixture de disposition. Il ne s’exécute pas sur les appareils
physiques de l’utilisateur. Le garde-fou de source confirme **13 fichiers protégés
identiques à 006** : modèle, contrôleur, grille, stockage, manifeste et configuration.

## APK de démonstration et captures

**[Archive téléphone, APK effectivement installée et preuves](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42995632542/artifacts/13843912916)** :
`app/build/outputs/apk/debug/app-debug.apk`. Signature debug v2 vérifiée, paquet
`com.jarvis.interfaceapp`, version **0.1.0**, code **1**, minimum API 26, cible API 35,
conservés de 006. Empreinte recalculée après téléchargement :

```text
24e2964fe0e3147d373e2e7d9421629afeb89057a19b305d40c301b2ca397d2f
```

[L’archive tablette](https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42995632542/artifacts/13844022553) contient son
APK installée et les preuves correspondantes. Chaque runner génère sa clé debug ;
les empreintes/signatures peuvent différer. La mise à jour d’une installation
physique antérieure nécessite une clé compatible. Ne pas désinstaller ni effacer
les données pour contourner un conflit de signature ou de version.

Les quatre captures finales sont des pixels du rendu Android natif obtenus par
PixelCopy de la racine Compose, sans reconstitution graphique ni HTML. Elles ont
été ouvertes et inspectées. Portrait : fiche fermée par défaut. Paysage tablette : exemple ouvert
volontairement. Sur téléphone en paysage, la page défile ; sa capture montre
la zone initiale, la fiche et les commandes restant atteignables plus bas. La barre système Android n’est pas une partie du rendu
Compose capturé.

| Profil | Portrait | Paysage |
| --- | --- | --- |
| Téléphone | [PNG](evidence/phone-portrait.png) | [PNG](evidence/phone-landscape.png) |
| Tablette | [PNG](evidence/tablet-portrait.png) | [PNG](evidence/tablet-landscape.png) |

Les [compteurs, dimensions et empreintes](evidence/qualification.json) ainsi que
les sorties de capture et signatures sont conservés avec ces images. Les archives
GitHub Actions contiennent les XML/HTML complets et les relevés bruts ; leur accès
peut demander une connexion et leur rétention est limitée.

## Fonctionnalités, limites et risques

Opérationnels dans les environnements testés : ouverture volontaire/retour, cinq
composants, fiche de démonstration, roue et routes existantes, saisie locale en RAM,
rotation, états d’indisponibilité, sauvegardes de disposition conservées et mode
historique au redémarrage. Les dessins restent **statiques et provisoires**.
Aucun moteur 3D/animation d’avatar, CORE, SERVICES, VOICE, nouvelle permission ou
stockage de conversation/préférence personnelle n’est intégré.

Les journaux de crash collectés sont vides.
Cela ne garantit pas l’absence de crash dans tous les usages. Les snapshots mémoire
sont indicatifs ; fluidité, CPU soutenu, batterie et température sur matériel réel
ne sont pas qualifiés. Les appareils physiques, la lecture vocale TalkBack et les
petites fenêtres particulières restent à essayer selon le [guide](README.md).

L’image de cockpit et les livrables 007 hors dépôt sont absents des fichiers fournis :
aucune fidélité artistique exacte n’est certifiée. La sélection V2 reste volatile.
L’audit de réutilisation et les branches/commits examinés sont dans [AUDIT.md](AUDIT.md)
et [reuse-inventory.json](reuse-inventory.json). Aucune fusion de ces branches.
Les fichiers modifiés sont listés dans [modified-files.txt](modified-files.txt).

**Mission 009 proposée uniquement** : consolidation visuelle sur l’image officielle,
validation physique/accessibilité et contrat explicite de capacités pour de futures
intégrations. Détails dans le guide. Attente de validation avant toute mission suivante.

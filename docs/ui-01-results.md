# UI-01 — résultats vérifiés, 6 novembre 2026

Livraison PARTIELLE. Ne satisfait pas encore les critères de clôture UI-01.

## Code et périmètre

PR brouillon : https://github.com/bubth720/JARVIS-INTERFACE/pull/16
Branche : feat/ui-01-native-cockpit.
Base : development/mission-002, d1c0931c4507e9af8f1aa60951947c23f4394b56.
Révision compilée : 018702dd1b7ff9f3e74a42317c65a5056be124ca.
Les commits documentaires ultérieurs ne changent pas ce code testé.

L’inventaire des branches a aussi identifié feat/interface-final-01, ui-02, ui-03
et des travaux avatar plus avancés. Ils sont intacts et ne sont pas intégrés
à cette proposition basée sur la branche par défaut. Cette PR n’atteste pas
la compatibilité avec ces autres lignes de développement.

## Réussites effectivement observées

Exécution : https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42239137360
Job build 127964969465, succès.
Commande : ./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest --no-daemon
Journal : BUILD SUCCESSFUL in 2m 24s, 15:04:48 UTC.
Compilation Android réelle, tests JVM et Lint réussis.
Tests instrumentés compilés : cela ne signifie pas qu’ils sont exécutés.

Étape Verify debug APK réussie :
- apksigner verify --verbose : signature debug vérifiée ;
- aapt2 dump badging : inspection du paquet ;
- sha256sum : empreinte calculée et conservée dans le rapport.

APK et rapports :
https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42239137360/artifacts/13514826148
Dans le ZIP : outputs/apk/debug/app-debug.apk,
reports/ui-01/signature.txt, package.txt, apk-sha256.txt,
rapports JVM et Lint. Signature de développement, pas de publication.

## Émulateurs : blocage avant installation

Exécution :
https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42239137396

Profil tablette générique 1200 x 1920 / 240 dpi, API 34 AOSP x86_64 :
job 127964490485 en échec lors du téléchargement de l’image système :
« Error on ZipFile unknown archive », puis connexion émulateur refusée.
L’APK n’a donc pas été installé sur cette cible.
Une première tentative Google APIs API 35 avait également échoué au téléchargement.
Aucun contournement de la vérification TLS ni d’une politique réseau n’est effectué.

Profil téléphone générique 1080 x 2400 / 420 dpi :
exécution encore en cours lors de ce relevé (15:05 UTC).
Consulter le lien pour son statut ultérieur ; aucune réussite revendiquée.
Ces émulateurs ne sont pas les appareils physiques demandés.

Conséquences : aucune capture réelle validée, aucun résultat instrumenté réussi
attesté, aucune preuve d’absence de crash à l’exécution, aucune mesure de
performance ou de consommation. Les scripts de collecte existent mais leur présence
ne remplace pas une exécution. Les snapshots CPU/batterie prévus ne constituent
pas un benchmark ni une mesure de consommation.

## Développé dans le code

Cockpit natif avec bureau vectoriel animé, dispositions adaptatives,
grille 4 x 2 ou rangée de huit modules, huit écrans métier explicitement
indisponibles, Retour Android, navigation supervision/avatar, paramètres
locaux d’intensité/éclairage/économie, sauvegarde locale, état de navigation
restaurable, suspension des animations hors RESUMED et en économie.

Horloge locale identifiée ; cinq plages lumineuses artistiques.
LocalWeatherStore conserve une observation avec source et horodatage et n’invente
aucune valeur. Aucun fournisseur météo actif. Variantes Canvas pour ciel dégagé,
nuages, pluie, orage, brouillard et neige à géométrie du bureau constante.
Slot avatar composable et contrats futurs sans moteur ni raccordement externe.

Ces comportements sont implémentés et compilés ; validation sur appareil en attente.

## Reste à réaliser / vérifier

- Obtenir 13496.png et 13498.png, absentes de l’espace local et des arbres
  des branches inspectées, puis reconstruire et comparer fidèlement les deux rendus.
- Finaliser décor, proportions, transitions et éclairage intérieur à partir des images.
- Import natif et validation des futures références de scène (contrat seulement présent).
- Éventuel adaptateur météo autorisé, avec tests des observations périmées ;
  aucun service ou cloud ajouté actuellement.
- Exécuter les tests instrumentés et valider toutes les actions, stockage après
  arrêt du processus, orientation répétée, mode avion, texte agrandi et TalkBack.
- Fournir et inspecter des captures réelles de cette révision.
- Tester POCO X8 Pro et Redmi Pad SE en distinguant appareil réel et émulation.
- Mesurer CPU, RAM, fluidité, batterie et température avec protocole reproductible.
- Vérifier installation et absence de crash ; validation finale utilisateur.

Aucun fichier supprimé, aucune fusion, aucune modification d’un autre module JARVIS.

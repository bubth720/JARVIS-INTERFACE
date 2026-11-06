# UI-01 — résultats vérifiés, 6 novembre 2026

Livraison PARTIELLE. Les critères de clôture UI-01 ne sont pas tous satisfaits.

## Périmètre et source

PR brouillon : https://github.com/bubth720/JARVIS-INTERFACE/pull/16
Branche : feat/ui-01-native-cockpit.
Base : development/mission-002, d1c0931c4507e9af8f1aa60951947c23f4394b56.
Révision compilée et testée : 84841841f3e4bd788f7097932e0536714f24c3d3.
Les commits documentaires ultérieurs ne changent pas ce code testé.
Code complet dans la branche ; architecture dans ui-01.md.

Les branches plus avancées feat/interface-final-01, ui-02, ui-03 et avatar
restent intactes. Cette proposition part de la branche par défaut et ne certifie
pas une intégration avec ces autres lignes de développement. Aucun fichier
supprimé, aucune fusion, aucun autre module JARVIS modifié.

## Compilation Android et APK réel

Exécution réussie : https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42241433909
Job 127973223536. Commande :
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest --no-daemon

- Compilation APK Android : réussie.
- Tests JVM : 4 tests, zéro échec/erreur/ignoré (XML inspectés).
- Lint : zéro erreur, 29 avertissements ; rapport inclus, aucune revendication
  de zéro avertissement. Versions Android/dépendances conservées pour compatibilité.
- Tests instrumentés : compilés, puis exécutés séparément ci-dessous.
- apksigner verify --verbose : signature debug v2 vérifiée.
- aapt2 dump badging : paquet com.jarvis.interfaceapp, version 0.1.0/code 1,
  minimum API 26, cible API 35. Pas de permission INTERNET.
- Empreinte SHA-256 de l’APK téléchargé :
  737a42f8dccf5f5476069b5d6af1e1e632657076489a63efc96843987484d6e0.

APK, signature, empreinte, manifeste et rapports JVM/Lint :
https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42241433909/artifacts/13516221275
Dans le ZIP : outputs/apk/debug/app-debug.apk et reports/ui-01/.
Signature de développement, pas de publication.

## Installation et tests sur émulateurs

Exécution réussie : https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42241434015
API 34 AOSP x86_64. Aucun appareil physique testé.

| Profil | Dimensions réellement constatées | Densité | Tests |
| --- | --- | --- | --- |
| Téléphone générique | 1080 × 1920 ; paysage 1920 × 1080 | 420 dpi | 6 réussis, 0 échec/erreur/ignoré |
| Tablette générique | 1200 × 1920 ; paysage 1920 × 1200 | 240 dpi | 6 réussis, 0 échec/erreur/ignoré |

Le profil téléphone demandé à 1080 × 2400 est limité par cet émulateur à
1080 × 1920 (wm size et PNG inspectés). Ces profils ne certifient pas le
POCO X8 Pro ou la Redmi Pad SE physiques.

Les tests couvrent démarrage/supervision/avatar sans partenaire, les huit routes
métier et leurs retours, retour des paramètres, recréation de l’activité avec
écran restauré, préférence économie persistante et Retour Android, stockage
avec source/horodatage d’une observation météo de test. La fixture météo est
explicitement synthétique, confinée aux tests et effacée avant les captures.

Wi-Fi et données mobiles désactivés avant les tests. L’application n’a pas la
permission INTERNET et ne requiert aucun autre module JARVIS. Le stockage après
arrêt forcé du processus n’est pas couvert par le seul test de recréation.

Après les tests, l’APK est explicitement réinstallé, lancé et son processus
vérifié. Les changements portrait/paysage ont produit les quatre captures
inspectées. Aucun chevauchement observé sur ces vues au texte standard ;
le téléphone défile verticalement, y compris en paysage. Sur tablette paysage,
les huit modules sont visibles en une rangée ; tablette portrait en grille 4 × 2.
Les panneaux défilés et toutes les tailles de texte ne sont pas validés visuellement.

## Captures et relevés réellement inspectés

Téléphone :
https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42241434015/artifacts/13516771190
Tablette :
https://github.com/bubth720/JARVIS-INTERFACE/actions/runs/42241434015/artifacts/13515996541

Chaque ZIP contient evidence/portrait.png, landscape.png, memory.txt,
frames.txt, cpu-snapshot.txt, battery-snapshot.txt, crashes.txt, display-size.txt,
display-density.txt, ainsi que les XML/rapports Android instrumentés.
Les barres système et le dock tablette visibles appartiennent à Android.

Snapshots après lancement et rotation : téléphone PSS 59 563 Ko, RSS 166 768 Ko ;
tablette PSS 59 585 Ko, RSS 166 256 Ko. Aucun swap rapporté. Journaux crash vides
sur les deux exécutions inspectées ; cela ne garantit pas l’absence de tout crash futur.

Gfxinfo ne rapporte que quatre frames (100 % classées janky). Cet échantillon de
lancement/rotation est trop court et défavorable pour conclure à la fluidité.
Les préférences peuvent laisser le mode économie activé après son test.
Aucun benchmark d’animation ni comparaison énergétique n’est revendiqué.
Snapshots CPU/batterie conservés, sans conclusion sur la consommation réelle,
l’autonomie ou la température. Mesures sur matériel encore requises.

## Échecs et corrections documentés

Le bac à sable local bloquait initialement le proxy ; accès réseau explicite
ensuite utilisé pour cloner et télécharger, sans contournement du proxy/TLS.
Le SDK/adb/KVM ne sont pas disponibles localement ; compilation/exécution en CI.

Le workflow initial installait le paquet SDK obsolète tools : corrigé en
platform-tools. Certaines tentatives ont reçu une archive d’image système
invalide ; les exécutions réussies ci-dessus sont distinctes de ces échecs.

Les premières captures montraient le lanceur : le runner désinstalle l’application
en fin de test. Réinstallation explicite ajoutée. Ces anciennes captures et
les relevés « No process found » sont invalides comme preuves du rendu JARVIS.
L’inspection suivante a révélé du texte sombre et des modules hors fenêtre en
paysage : contentColor explicite, barres système adaptées et hauteur du décor
bornée sans changer son ratio. Les captures citées ci-dessus sont les vues corrigées.

## Développé

Cockpit Kotlin/Compose, bureau vectoriel, huit écrans de modules explicitement
sans fonctions métier raccordées, supervision sans données fictives, navigation
et Retour Android, paramètres locaux intensité/éclairage/économie, préférence
persistante et écran restaurable, suspension d’animation hors RESUMED et en économie.
Horloge locale, cinq plages lumineuses artistiques, cache météo identifié nullable,
six variantes météo à géométrie constante, slot avatar composable et contrats futurs.
Aucun fournisseur météo, moteur avatar externe, CORE/LLM/VOICE ou cloud activé.

## Reste à réaliser avant validation complète

- Obtenir 13496.png et 13498.png, absentes des arbres de branches inspectés et
  de l’espace fourni. Le décor vectoriel est provisoire : fidélité NON validée.
- Reprendre décor, proportions et éclairages d’après les deux références.
- Import natif et validation des futures références de scène : contrat présent,
  sélecteur et import non développés.
- Éventuel adaptateur météo autorisé et tests d’observations périmées.
- Tests POCO X8 Pro et Redmi Pad SE physiques, texte agrandi/TalkBack,
  arrière-plan/reprise, arrêt du processus et orientations répétées.
- Benchmark CPU/RAM/fluidité et protocole batterie/température reproductible.
- Validation des transitions et de toutes les variantes météo/horaires.

PR maintenue en brouillon. Aucune fusion sans autorisation du propriétaire.

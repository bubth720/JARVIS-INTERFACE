# M51-R1 — diagnostic Android borné

Base exacte PR18 : `9c8960a7c9d3f572dff41fe18a5b002c9f66ac8a`, tree `39427d75257a841208e6e75e2f1755d1cb564d85`. Branche `codex/m51-r1-android-stability`. Sources des incidents : UNIFIED PR21, réception `a7625a31513be5c56fa36ed946fbdcd3c23c2a15`.

Diagnostic d'abord ; aucun correctif applicatif présumé. Les 89 fichiers UI-01C et les assertions historiques restent inchangés. L'option explicite `-Pm51R1=true` ajoute un listener et deux observations dans une copie générée du test SAF, sans changer une assertion ni un clic. L'APK applicative conserve les sources et paramètres M51 ; les modèles et autres modules restent OFF.

Campagne D1 : une seule tentative de boot par API29/35, même budget AVD2cores/2048M, délai configuré300s inchangé. Chaque boot doit d'abord passer60s de stabilité SystemUI/launcher/Settings sans JARVIS installé. Aucun test JARVIS si un ANR/crash système est observé. API29 : un appel isolé de chaque cas fautif ; puis un unique contrôle ordonné capture/projet seulement si aucun crash critique n'a été observé. Un crash arrête la campagne API concernée, sans retry. Les délais d'assertions15/20s et le budget instrumentation900s ne sont pas augmentés.

Le listener observe les threads sans dessiner, sans lire des données métier et sans intercepter la terminaison du processus. Ses prélèvements bornés sont diagnostiques, pas des benchmarks. Les traces originales et corrigées restent distinctes. Une campagne Q1 de qualification sera préparée après examen de D1 et des causes.

Un probe séparé vérifie l'accélération du runner standard gratuit macos-15-intel d'un dépôt public ; aucun boot si l'hyperviseur est indisponible. La restriction officielle concerne notamment macOS ARM ; sa disponibilité sur Intel n'est pas présumée. Pas de modification de permissionsKVM/groupes/udev, aucun achat cloud.

Sources amont exactes : Google Maven Compose1.8.0 (sources publiées Apache-2.0) ; captureToImage utilise PixelCopy et performSemanticsAction passe par runOnUiThread. Le crash SnapshotStateObserver reste UNKNOWN tant que la reproduction n'identifie pas le second thread et son chemin d'appel. Aucun upgrade Compose proposé sans preuve.

Aucune fusion, installation personnelle, modification JNI/CORE/LLM/VOICE/NEXUS, migration privée ou activation. L'APK compilée reste NON QUALIFIÉE jusqu'à réception Android complète. Rollback : abandon de cette branche, option R1 omise ; aucune base ou donnée historique modifiée.

## Laboratoire alternatif Q1

Le probe D1 macOSIntel a réellement renvoyé exit0 / Hypervisor.FrameworkOSX15.7 ; archive13630008815 et SHA dans RECEIPTS.json, vérifiés après téléchargement. Cela permet de préparer une campagne indépendante Q1, sans présenter le probe comme un boot ou une stabilité Android. Les diagnosticsD1Linux se poursuivent séparément et leurs échecs ne sont pas effacés.

Q1 est exécutée sur deux runnersstandardsIntel, un boot maximum parAPI, seulement après relecture du supportHVF du runner courant. Chaque émulateur passe ensuite le même préflight sans JARVIS. Les trois cas fautifs sont rejoués individuellement sans sampler, puis le banc M51 original intact vérifie18baseline/24candidat, force-stop/restart, hors-ligne et fenêtres de frames comparables. Aucun passage à l'étape suivante si un cas individuel échoue. Le budget reste2cores/2048M, les assertions et délais originaux sont conservés. Au maximum deux boots parAPI pour la mission (D1Linux etQ1Intel), aucun retry automatique.

Les seuls paquetsbaseline créés par les trois tests isolés peuvent être retirés après leur succès pour retrouver le garde de paquetneuf du banc complet. Le candidat et toutes les applications historiques sont exclus de ce nettoyage. ROLLBACK.md précise les garde-fous et leurs tests. La phaseQ1 n'affirme aucune cause applicative résolue : toute cause insuffisamment établie resteUNKNOWN.

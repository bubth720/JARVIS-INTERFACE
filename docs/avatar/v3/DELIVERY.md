# AVA-04 — livraison du pipeline et proposition artistique privée

Branche `feat/ava-04-delivery`, base AVA-03
`ab0a3f5a992a90ca53dc20c9c56d3bd8a0c2b3a0`. Le commit livré est indiqué dans
le bilan final et l'historique. [Mandat #1](https://github.com/bubth720/JARVIS-INTERFACE/issues/1).

**REVIEW_PIPELINE_READY / ART_REVIEW_PENDING / RIG_PENDING / DEVICE_NOT_QUALIFIED.**
La mission n'est pas déclarée artistiquement achevée : le choix esthétique, les
calques articulés et les accords de diffusion/activation définitive restent requis.

## Résultat concret

Douze vrais portraits RGBA semi-réalistes ont été créés dans l'espace privé,
avec lunettes noires, cheveux cuivrés/dorés, yeux noisette et uniforme bleu nuit.
Huit expressions de face, deux trois-quarts, parole et clin d'œil peuvent être
examinés dans la galerie privée. Ce sont des propositions IMAGE_REPLACEMENT,
pas les PNG synthétiques existants rebaptisés et pas un rig terminé.

Le packager planifie les dimensions, vérifie alpha/CRC/dimensions/budgets, écrit
un manifeste V2, ses SHA et un ZIP déterministe. Le validateur Android AVA-03 a
réellement chargé et décodé le pack privé. La galerie Compose affiche les états
et perspectives, avec une entrée depuis la preview V2 existante. Aucun portrait
privé n'est incorporé à l'APK public ni au dépôt.

Voir [ART-SPEC](ART-SPEC.md) pour les fichiers, les limites et la revue artistique.
Fichiers privés : `/workspace/artifacts/athena-private/ava04/` :
`galerie-privee.html`, `galerie-android-privee.html`, `portraits/*.png`,
`production-spec.json`, `pack/{manifest.json,athena-review.zip,review-request.json}`
et `captures/*.png`. La galerie source est autonome avec agrandissement ; les
captures sont du vrai Compose, pas des montages de la référence.

## Validation exécutée

- `./scripts/check.sh connectedDebugAndroidTest --no-daemon` : **BUILD SUCCESSFUL
  en 5m21s**, 77 tâches, 10 exécutées/67 incrémentales.
- **129 tests JVM**, 0 échec/erreur/ignoré (125 précédents +4 consentements).
- **48 tests Android**, 0 échec/erreur/ignoré (47 précédents +1 galerie avec fixture
  publique). Régression du dashboard, du stockage, de l'avatar et du chat conservée.
- `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest` et
  `connectedDebugAndroidTest` réussis. Lint : **No issues found.**
- `python3 -m unittest discover -s tools/avatar -p 'test_*.py' -v` : **9/9 réussis**.
  Alpha réel, CRC corrompu, PNG opaque, budgets, reproductibilité, chemin de sortie
  public interdit, source hors racine, absence de neutre et ZIP supplémentaire.
- Contrôle privé hors ligne : 12 ressources, 2 039 184 pixels, alpha réellement
  décodé. Le budget cumulé avec la démo de preview reste à 2 079 184 pixels.
- **3 exécutions Android privées supplémentaires réussies, 0 échec** : un contrôle
  initial portrait et deux parcours finaux, chacun parcourant les 12 correspondances
  avec le même vrai validateur. Captures **720×1280 et 1280×720**, 26 fichiers privés.
  Le mode privé nécessite une provision volontaire du ZIP dans le stockage interne
  du debug APK et refuse les appareils non émulateurs. Aucune image privée en CI.
- La boucle de captures privée a été ajoutée au test après la régression publique,
  puis `assembleDebugAndroidTest lintDebug` a réussi (28s) et les deux parcours
  privés finaux l'ont exécutée. Le code de production est inchangé par cette étape.

Environnement : Android 8.0/API26 x86_64, AVD logiciel sans KVM, 2 vCPU, RAM 2 GiB,
densité 320, mode avion. La rotation réelle du display produit les deux formats,
sans prétendre une validation sur POCO/Redmi. Aucun appareil personnel utilisé.
JVM/Android/APK : [relevé numérique](validation/build-tests.json) et
[JUnit public](validation/android-public-fixture.xml). Les trois traces privées
versionnées ne contiennent que les résultats JUnit, aucun visage ni média.

## Conservation de l'existant

Seul `AvatarV2Preview.kt` reçoit une entrée de galerie. Le contrôleur, le fallback,
les ressources V1, les modèles/règles/gestes, le chat UI-01, DataStore JSON V1,
Gradle et le manifeste Android restent inchangés. Le nouveau fichier de galerie
n'a ni boucle animée, ni décodage par frame ; il réutilise les textures validées.
Le renderer et sa suspension lifecycle restent ceux d'AVA-03. Aucun IPC/INTERNET,
service, micro, permission globale ou moteur IA. Aucun merge ni changement de main.

La CI exécute les contrôles de périmètre/absence d'assets privés et les tests Python,
JVM/Lint, compile les deux APK et conserve les artefacts antérieurs. Les médias
privés ne sont jamais transmis au runner. Les accords esthétique, publication et
activation sont indépendants et restent faux ; une sélection temporaire n'accorde
aucun de ces droits.

## Prochaine étape

Faire examiner les visuels privés par l'utilisateur et corriger les détails demandés,
puis produire/qualifier les vrais calques articulés sur l'identité retenue. AVA-05
reste en attente des assets nécessaires approuvés. UI-02 peut être livrée séparément ;
UI-03 ne dispose encore d'aucun transport de texte CORE approuvé.

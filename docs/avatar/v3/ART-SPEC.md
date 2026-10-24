# AVA-04 — Athéna, spécification artistique et revue privée

Référence de base : AVA-03 `ab0a3f5a992a90ca53dc20c9c56d3bd8a0c2b3a0`.
Mission intégralement lue : [issue #1](https://github.com/bubth720/JARVIS-INTERFACE/issues/1).
Branche : `feat/ava-04-delivery`. Aucune fusion ou activation définitive.

## Proposition réellement créée

Le personnage cible est une femme adulte illustrée dans un style anime cinématique
semi-réaliste premium : cheveux longs cuivrés/dorés, yeux noisette, lunettes noires,
uniforme bleu nuit à liserés cyan discrets. Le visage, la monture, les mèches et la
tenue doivent rester cohérents entre les expressions et les angles. La planche
utilisateur est une bible privée, pas un atlas prêt à découper.

De nouveaux médias ont été générés pour cette mission : portrait maître, planche de
huit expressions (deux versions de proposition), vues trois-quarts gauche/droite,
parole et clin d'œil. Les portraits du pack sont extraits **des nouvelles planches
générées**, jamais des vignettes de la référence utilisateur. Le découpage régulier,
le redimensionnement et l'encodage RGBA sont réalisés par ImageMagick, conformément
aux conversions raster autorisées dans la mission. Aucune retouche automatique
n'est présentée comme une création de calques articulés.

Les fichiers et la galerie HTML autonome sont dans le workspace privé
`/workspace/artifacts/athena-private/ava04/`, hors dépôt. La galerie permet
l'agrandissement et le changement de fond pour examiner les transparences.
L'utilisateur doit encore examiner/corriger ces propositions. Ni son silence,
ni une prévisualisation, ni une sélection temporaire ne vaut accord de publication.

## Inventaire privé proposé

- FACE : NEUTRAL, ATTENTIVE, THOUGHTFUL, SURPRISE, LAUGH, CONCERNED, WAITING, FOCUSED.
- FACE : TALKING et WINK supplémentaires.
- THREE_QUARTER_LEFT / THREE_QUARTER_RIGHT : NEUTRAL.
- 12 PNG RGBA 8 bits, 357×476 pixels chacun, artboard logique 240×320.
- 2 039 184 pixels, 8 156 736 octets RGBA théoriques ; réserve de 40 000 pixels pour
  la démo technique préchargée AVA-03, soit 2 079 184 pixels cumulés ≤2 097 152.
- ≤16 ressources/calques, ≤2 MiB par PNG, ≤8 MiB décompressés ; limites inchangées.
- Manifeste V2 et ZIP déterministe privés, SHA-256 et provenance dans les fichiers
  de revue privés. Aucun fichier graphique privé ne rejoint les assets Android.

La planification `plan_dimensions` choisit des dimensions 3:4 en réservant le cache
existant. Le packager refuse une sortie publique dans le checkout ; seul le dossier
privé ignoré est permis à l'intérieur du dépôt. Aucune commande de publication ou
d'activation n'est fournie. Le contrôle Python décode réellement l'alpha RGBA et
les filtres PNG ; il ne remplace pas le validateur Android V2, testé séparément.

## Statut et points à corriger

**ART_REVIEW_PENDING / RIG_PENDING**, pas de personnage définitif approuvé.
Le premier ensemble avait des expressions trop proches ; une seconde proposition
accentue écoute/réflexion/concentration. À examiner : naturel du regard concentré
(possiblement trop sévère), proportions visage/nez, finesse des lunettes, coiffure
et identité entre face et trois-quarts, occlusion des mèches, bordures et cadrage.
Aucune équivalence parfaite de tous les traits n'est certifiée automatiquement.

Ce pack est honnêtement déclaré **IMAGE_REPLACEMENT** : portraits pleine pose,
avec fondu via le renderer existant. Il ne contient pas encore les calques séparés
buste/cou/cheveux/visage/paupières/bouche nécessaires au rig 2.5D et à AVA-05.
Les animations et le fallback V1 restent disponibles et inchangés. Il faudra figer
l'identité avec l'utilisateur avant de produire ces calques, vérifier leurs pivots
et occlusions puis qualifier les animations. Un portrait TALKING n'est pas du lip-sync.

## Code livré et consentement

- `tools/avatar/make_athena_pack.py` : planification, alpha/CRC/budgets, manifeste,
  ZIP stable et demande de revue sans aucune autorisation accordée.
- `tools/avatar/check_athena_pack.py`, `test_athena_pack.py` : contrôle hors ligne
  et tests, uniquement avec petits fichiers synthétiques publics en CI.
- `avatar/review/ArtApprovalReceipt.kt` : consentements esthétique/publication/
  activation permanente indépendants, liés au SHA du manifeste et à l'epoch.
  Cette structure n'est pas une preuve d'autorisation signée par CORE.
- `avatar/review/AthenaGalleryScreen.kt` : galerie Compose statique utilisant les
  textures déjà décodées, deux/trois colonnes, états/vues explicites, sans bouton
  de publication ni activation. Entrée additive depuis `AvatarV2Preview`.
- Tests JVM des consentements et test Android de galerie. Ce dernier utilise la
  démo publique par défaut ; le mode privé est réservé à l'émulateur et lit un ZIP
  provisionné volontairement dans le stockage interne de l'APK de test.

Approbation artistique, diffusion et activation restent trois décisions distinctes.
La branche contient le code et les preuves numériques sans visage ; les visuels et
captures premium restent exclusivement dans l'espace privé.

# AVA-03 — assets et contrat V2

## Inventaire réel

**ART_ASSETS_MISSING** pour Athéna premium. Le pack utilisateur contient un dossier
Markdown et une planche 1536×1024, pas des textures RGBA articulables. La référence
est conservée hors checkout ; son empreinte est
`5d226d51391248b8e5f55771f9be341696c75ba1105de996303247386543f84c`.
Aucune copie, découpe, visage dérivé ou capture de cette image n'est publié.

Les seuls assets graphiques livrés sont les PNG synthétiques **déjà publics en
AVA-01**, inchangés. L'adaptateur V2 réutilise cinq textures 80×100 à partir de
`app/src/main/assets/avatar-demo/`, avec artboard logique 240×320 :

| Fichier | SHA-256 | Canal V2 |
| --- | --- | --- |
| back-hair.png | 8f9d72a5099b80b2e2d8f63663990342b28f66e38cae467e2d896f7fdba1ab44 | BACK_HAIR |
| body.png | 4d57d3b749dd89a3d00471ebda559fe74427547dd52ab85bd5f57c2ebe0b6db2 | BODY |
| face.png | 00e2aeceba199c58c4a59e40ac4c785417cbf9043b898f21b8f250cc71742d47 | HEAD |
| eyes.png | d3241ade53c3590aa52709b3c577d529dcb693490e27daf7ccfde9bf2c0f913d | HEAD, non articulé dans cette démo |
| front-hair.png | ef495f0e5d7ecacd0b992bd322b4624e18f9079139300be50a1944d743d08d05 | HEAD |

Les deux variantes bronze V1 sont conservées dans le sélecteur historique. Elles
ne deviennent pas des ressources artistiques premium. Provenance : commit AVA-01
`0c6d8a1bdeabe8f23fe8f98263cfe88d8c3c216e`, vérifications SHA/CRC/transparence
par `scripts/check_avatar_assets.py`. Aucun nouveau personnage de production.

[ZIP technique importable](fixtures/ava03-technical-demo.zip) : manifeste V2 + ces
cinq PNG, sans référence utilisateur. Il démontre IDLE/NEUTRAL/FACE, respiration et
inclinaison. Il n'offre pas huit expressions, de nouveaux angles, de paupières
fermées ni de bouche articulée. L'avatar vectoriel V1 conserve son clignement.

## Schéma de pack

Le ZIP volontaire doit contenir exactement `manifest.json` et ses PNG, à la racine,
sans dossiers, noms alternatifs, fichiers supplémentaires ou archive imbriquée.
Le [manifeste de démonstration](../../../app/src/main/assets/avatar-v2-demo/manifest.json)
est un exemple complet validable. `avatar-v2/availability.json` décrit uniquement
l'absence du pack officiel ; ce n'est pas un manifeste de production vide accepté.

- Racine stricte : `version: 2`, `id`, `author`, `license`, `artGrade`,
  `width: 240`, `height: 320`, `resources`, `layers`, `frames`.
- `artGrade` : DEMONSTRATION ou PREMIUM_CANDIDATE. Une déclaration dans le fichier
  ne certifie ni les droits d'auteur ni la qualité ; la confirmation visuelle est
  distincte, locale à la session.
- Ressource : `id`, `file`, `sha256` hexadécimal minuscule, `width`, `height` réels.
  PNG RGBA 8 bits, pixels transparents **et** visibles requis ; APNG refusé.
- Calque : `id`, `resource`, `order` unique 0..100, `x`, `y`, `width`, `height`
  dans l'artboard, `pivotX`, `pivotY`, `depth` 0..1, `channel`.
- Canaux : STATIC, BODY, BACK_HAIR, HEAD, EYES_OPEN/EYES_CLOSED,
  MOUTH_NEUTRAL/MOUTH_OPEN. Les canaux œil/bouche sont acceptés par paires dans
  chaque frame. Les calques du visage partagent leur pivot ; position et marge
  doivent être préparées par l'artiste.
- Frame : `state`, `expression`, `perspective`, `layers` (IDs uniques), `mode`.
  La clé à trois composantes est unique. IDLE/NEUTRAL/FACE est obligatoire.
  Pas de ressource ou calque inutilisé caché dans le pack.
- `mode: LAYERS` : plans articulables. `IMAGE_REPLACEMENT` : un seul calque STATIC,
  explicitement présenté dans l'interface comme remplacement d'image pleine pose.
- États : IDLE, LISTENING, THINKING, SPEAKING, WAITING, WORKING, REACTION, ERROR.
- Expressions : NEUTRAL, SMILE, SURPRISE, LAUGH, WINK, ATTENTIVE, THOUGHTFUL,
  CONCERNED, TALKING, WAITING, FOCUSED.
- Perspectives : FACE, THREE_QUARTER_LEFT, THREE_QUARTER_RIGHT, LEFT, RIGHT, BACK.

La propriété `completeCandidate` vérifie uniquement la couverture déclarée (huit
états, au moins huit expressions, face/3⁄4 gauche/droit, canaux articulés). Elle ne
certifie ni la distinction visuelle des images, ni le style, ni les droits et ne
fait jamais passer automatiquement `ART_ASSETS_READY` à vrai.

Une sélection exacte absente est refusée ; aucun état artistique n'est simulé par
un renommage du neutre. La sélection d'un état recherche son expression disponible
dans la perspective courante. Les sélections d'expression/perspective sont exactes.
Le retour neutre est un événement explicite ; le renderer refuse une interruption
de fondu en cours (~250 ms) pour conserver la continuité.

## Budgets conservés

Manifest ≤64 KiB ; ≤16 ressources, ≤16 calques ; PNG ≤2 MiB ; ZIP décompressé
≤8 MiB ; côté ≤2048 px ; somme des textures ≤2 097 152 pixels. Aucun changement du
V1. Le registre V2 applique aussi ce budget à la somme des packs conservés en
mémoire pour comparer/revenir au précédent. Un pack avec ID réutilisé et contenu
différent est refusé. Fermer/recréer la session libère ce registre ; l'import ne
persiste ni fichier, ni URI, ni autorisation SAF.

La démo V2 occupe **40 000 pixels / 160 000 octets RGBA théoriques**. Le V1 calques
complet à sept textures représente 56 000 pixels / 224 000 octets. Le V1 vectoriel
par défaut n'a pas de textures PNG. Ces calculs excluent objets, copies temporaires,
GPU et overhead du processus. L'inspection décode/recycle une image à la fois,
puis le chargement final conserve les textures ; aucune allocation d'image par frame.

## Assets manquants pour le candidat premium

1. Un buste étalon transparent fidèle à l'identité visuelle, puis face et 3/4 gauche
   et droit cohérents, sans découper la mosaïque.
2. Au moins huit expressions distinctes : neutre/sourire, écoute, réflexion,
   surprise, joie/rire, inquiétude/erreur, parole, attente/concentration/réaction.
3. Pour les vues articulées : buste/cou, cheveux arrière, visage, mèches avant,
   lunettes noires, yeux ouverts/fermés et bouche neutre/ouverte séparés, masques
   dessinés dans les textures et repères d'alignement. Les variantes partagent les
   pivots et l'artboard avec marges suffisantes.
4. Manifeste de correspondance, empreintes, source éditable, provenance/licence
   et autorisation de publication des ressources dérivées de la référence.

Un atlas de plusieurs états doit rester dans les budgets ci-dessus. Si l'artiste
requiert davantage de calques/résolution, chiffrer une proposition séparée ; cette
mission n'augmente aucune limite. Aucun visème phonétique n'est livré ni promis.

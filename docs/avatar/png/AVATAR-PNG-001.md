# AVATAR-PNG-001 — Bibliothèque privée de 36 expressions

## Périmètre
Branche : feature/avatar-png-001
Base : feat/interface-final-01 @ db459ba388a3fea992d80320c3989be0edda8500
Source privée : dossier Google Drive JARVIS_AVATAR (36 PNG créés par l'utilisateur).
Les photos et leurs dérivés ne sont **pas** inclus dans GitHub, CI, APK, fixtures ou captures publiques.

## Audit graphique du 11 novembre 2026
- 36/36 fichiers RGBA ouverts et vérifiés, noms JARVIS_001..036 sans manque.
- Dimensions des sources : environ 1024 x 1535/1536 (un fichier fait 1025 x 1535).
- Taille source totale : environ 92,85 Mio.
- Cohérence du personnage et des accessoires confirmée sur planche de contact, avec de petites variations de pose/cadrage.
- 026, 031, 034 : régions sombres connectées aux bords et alpha imparfait ; corrections conservatrices effectuées sur les copies de pack, originaux non modifiés.
- Des déformations ou halos résiduels fins doivent toujours être acceptés ou corrigés lors de la revue visuelle utilisateur ; le nettoyage ne transforme pas les images en rig articulé.
- Le style des sources privées (lunettes, coiffure et tenue beige) est distinct des assets antérieurs en uniforme bleu nuit : ne pas mélanger les packs dans un fondu sans revue artistique.

## Pack livré hors dépôt
Fichier local livré séparément : JARVIS_AVATAR_PNG_V1_36.zip.
- Format : jarvis.avatar.png/1, manifest.json + 36 entrées poses/.
- Rééchantillonnage 640 x 960 RGBA ; originaux préservés sur Drive.
- Taille ZIP : 34 111 397 octets (32,53 Mio).
- SHA-256 du ZIP : 984ae1cbb1d43236b5bf23b0df08bb28abb524cec7387821fcc7538a72af8d49.
- 36 SHA-256 individuels dans le manifest.json, testés et revérifiés sur le ZIP fini.
- Image neutre initiale : 031.
- Catalogue d'intentions bornées : IDLE, LISTENING, THINKING, EXPLAINING, SUCCESS,
  ERROR, SURPRISED, HAPPY, PLAYFUL, GREETING, COMPASSION, CALM, CONFIDENT, WARM.
- Les images sont des poses fixes. Ni parole phonétique, ni respiration, ni vraie animation.

## Intégration Android — projet public sans fuite
Le lecteur est distinct de l'importeur AVA-03 (max 16 calques, 8 Mio). Il ne modifie
pas les limites ni le contrat V2 et réutilise l'interface AvatarArtwork.
- Dans Avatar -> Démo 2.5D : bouton « JARVIS AVATAR · bibliothèque PNG ».
- Import volontaire depuis le sélecteur Android OpenDocument.
- Validation du catalogue fermé, des noms/chemins, du nombre, des bornes de taille,
  des dimensions PNG et du SHA-256 de chaque ressource.
- Décompression vers filesDir/avatar-png privé, jamais vers des chemins ZIP arbitraires.
- Chargement d'une image à la fois, décodage échantillonné hors fil UI.
- Aperçu des 36 poses. Activation explicite de la pose choisie et sauvegarde séparée
  du pointeur local ; l'ancienne image vectorielle reste disponible.
- Aucun réseau, permission INTERNET, IPC CORE, VOICE, demande au LLM ou transfert Drive automatique.
- Aucun remplacement du dashboard DataStore, des dispositions, des anciens media,
  des contrats IDENTITY ou des fichiers originaux.
- L'affichage des PNG remplace visuellement le dessin animé 2.5D ; ne pas lui attribuer
  les clignements ou la synchronisation vocale de l'ancien moteur.

## Tests et limites
Vérification de préparation exécutée localement hors Android : 36 PNG lisibles,
dimensions, intégrité ZIP, SHA individuels, numérotation et manifest.
Les tests Kotlin/Android et la compilation du nouveau code sont à exécuter sur CI :
leur résultat ne doit pas être présumé. Qualification physique POCO X8 Pro et
Redmi Pad SE reste à faire ; vérifier le parcours SAF, la rotation, les changements
de poses, le fond/alpha et la mémoire sur l'appareil.

**Sélection contextuelle automatique (futur)** : le manifest contient les intentions,
mais aucun événement CORE/VOICE n'est relié ici. Cela exige un adaptateur local
authentifié et des règles de priorité/continuité validées.

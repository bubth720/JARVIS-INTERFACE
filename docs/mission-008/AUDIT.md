# Mission 008 — audit préalable et réutilisation

Audit effectué avant modification du code. Base exacte :
`test/interface-device-006` / `ebd6c5fc873054d3f04bb2388d53e407dc73b7b7`.
Développement dans un worktree distinct, branche `feature/interface-cockpit-v2-008`.
Aucune fusion de branche. Les SHA et empreintes des contrats examinés sont dans
[reuse-inventory.json](reuse-inventory.json).

| Source | Compatible / décision | Risque évité |
| --- | --- | --- |
| Base 006 | Conserver MainActivity, les quatre panneaux, DashboardController, HomeState et HomeAction | Remplacement de l’accueil ou perte des dispositions |
| Base 005/006 | Conserver sans modification LayoutRepository, LayoutCodec v1, DataStoreLayoutRepository et JarvisApplication | Migration ou écriture de mode/conversation dans dashboard.json |
| UI-01 native cockpit | Retenir les principes de composants Compose, variantes de largeur et mentions hors ligne ; ne pas importer son point d’entrée ni son SQLite | Cette branche remplace MainActivity et n’embarque pas le stockage de disposition 005 |
| UI-02 delivery | Contrats de stockage identiques à 006 ; ne pas importer les écrans de chat/identité ou leurs contrôleurs | Collision JarvisApp, nouvelles routes et consentements hors mission |
| Avatar 001 / AVA-01 | Conserver le contrat AvatarSession déjà présent ; ne charger aucun moteur | Animation / horloge / cycle de vie non autorisés |
| AVA-03 / AVA-04 | Réutiliser uniquement la géométrie du fallback statique normalisé 240×320, avec attribution dans le code | Les galeries, loaders, packs, players et assets premium non validés ne sont pas nécessaires |
| UI-01 UI-avatar / fix Athéna premium | Examiner les variantes et leur point d’entrée ; ne copier ni moteur ni médias privés | Activation par défaut ou publication d’assets hors autorisation |
| codex/m54-avatar-preparation | Examiner les contrats sans les raccorder | Préparation avatar ne vaut pas autorisation d’activation |

Le dessin de secours provient de `avatar/AvatarArtwork.kt` dans AVA-04, fonction
`drawStaticAvatarFallback`. Il est adapté comme visuel provisoire sans AvatarPose,
controller, horloge ou animation. Les médias premium ne sont pas copiés.

## Architecture retenue

Un paquet `cockpitv2` contient un état de session RAM, le rendu Compose et deux
slots graphiques remplaçables. HomeViewModel possède cette session en plus de
son contrôleur historique inchangé. Seul le bouton volontaire dans Paramètres
ouvre V2. Le mode, le champ, la roue et la fiche ne sont ni sérialisés, ni placés
dans SavedStateHandle/rememberSaveable. Une rotation garde le ViewModel ; une
nouvelle session de processus reprend le dashboard avec un champ vide.
Quitter V2 efface aussi le brouillon RAM.

La roue a deux destinations existantes réelles : dashboard historique et
paramètres. Une entrée ouvre l’exemple de fiche. CORE/SERVICES/VOICE sont
disponibles uniquement comme entrées désactivées explicitement décrites.
Le champ n’envoie rien ; envoi et microphone sont désactivés avec justification
accessible. Il n’existe aucun port réseau, audio ou client de service.

Les zones se réorganisent suivant la largeur réellement disponible. Une fenêtre
courte ou un texte agrandi passe en page défilante ; les commandes ne chevauchent
pas le contenu. Les dessins sont statiques. La fiche fermée n’est pas composée ;
la sphère reste dans la composition principale lorsqu’elle est ouverte.

## Entrées non fournies

L’audit 007 hors dépôt et l’image de cockpit citée ne sont pas présents dans les
fichiers accessibles au début de cette mission. Aucun artefact d’une ancienne
mission n’est renommé pour les remplacer. Le prototype suit les cinq composants
décrits ; un comparatif artistique avec l’image reste conditionné à sa réception.

# UI-01 — Écran conversationnel local, intégration CORE PARTIELLE

Dépôt `bubth720/JARVIS-INTERFACE`, branche `feat/ui-01-ui-avatar`.
Départ conservé : AVA-01 `0c6d8a1bdeabe8f23fe8f98263cfe88d8c3c216e`.
Base de fiche : `139bfdc4aac5d2fc9b841c5b804c3b89bfc57ec6` ; préparation `50f5f61708d5ce6b141dec98fe0c73d89eaf00e3`.
Dépendance CORE-02 vérifiée localement et sur GitHub : `e86107b8213b0aeaf9d22025cf6bef91a8e544a5`.
Le SHA final de livraison est celui de la branche, fourni dans le compte rendu Git.

## Code livré et parcours

Le bouton **Conversation — local expérimental** ouvre un vrai écran Compose : liste LazyColumn par pages
20/200 messages, saisie limitée à4000 caractères, envoi/annulation, statut du port, notifications bornées,
retour dashboard. Zones d'en-tête et saisie bornées/défilables pour petites fenêtres et clavier. Le ViewModel
conserve la session en RAM lors d'une rotation ; messages et brouillon ne sont jamais écrits dans DataStore,
SQLite, logs ou SavedState. Seul l'indicateur de navigation booléen est restaurable.

Par défaut : **CORE NON CONNECTÉ**, Envoyer indisponible, aucun moteur ni outil exécuté. La bascule explicite
**Démo locale, sans CORE ni IA** permet d'essayer saisie, progression logicielle, suivi et annulation. Chaque
texte de démonstration porte **FIXTURE — PAS UNE RÉPONSE IA** ; la réponse explique qu'aucun CORE/LLM n'a tourné.
Cette réponse prédéfinie n'est jamais présentée comme de l'inférence. Désactiver la démo efface seulement
son état RAM après fin de travail ; aucun profil ni préférence n'est persisté.

`CoreEventUiPort` est un port local proposé, pas un IPC autorisé. Le contrôleur attend l'ACK portant les UUID
métier attribués par le port ; il ne transforme pas un UUID en bail JNI. Les mises à jour sont corrélées par
session/requête/génération/séquence/ID d'événement. Tampon pré-ACK borné32, rejet doublons, anciennes générations,
réponses tardives et ACK réutilisé ; aucun redémarrage automatique d'une session inconnue. Annuler conserve
busy jusqu'au terminal corrélé. Perte d'ACK/flux et ressources non confirmées restent bloquantes ; une erreur
générique ultérieure ne lève pas le blocage de ressources. Aucune preuve de déchargement n'est inventée.

## Compatibilité réelle et limites

Les sources CORE-02 et leurs SHA-256 sont dans `CORE02-REFERENCE.json`. La copie exacte de CoreEventBus est
compilée **uniquement dans les tests JVM**. Un test pousse de vrais événements de ce bus local vers le contrôleur
INTERFACE ; aucun moteur ou génération réelle n'intervient. La copie n'est pas dans le DEX de l'APK.
Les indices de chunks, codes d'erreur et UUID sont représentés sans convertir leurs métadonnées en permissions.

CORE-02 publie seulement des métadonnées : **pas de texte de réponse ni d'API externe de soumission**.
Un RESPONSE_COMPLETED réel ne crée donc aucune fausse réponse assistant ; l'écran indique « contenu non raccordé ».
La lecture de contenu, l'authentification/transport inter-app, les outils autorisés et notifications de mémoire
confirmée nécessitent leurs contrats et missions distincts. Aucun service/Binder/permission n'est ajouté.
La mission est **PARTIAL** pour le raccordement conversationnel réel. Aucun essai CORE→LLM→INTERFACE ou physique
n'est revendiqué. UI-02 et AVA-02 restent soumis à leurs dépendances propres.

## Préservation

DashboardGrid, IDs/placements/verrous, HomeViewModel/Controller, DataStore/LayoutCodec V1, avatar et ressources
historiques inchangés. L'écran est une route UI V1 distincte : aucun nouveau PanelId ajouté au document V1.
52 fichiers protégés octet pour octet depuis AVA-01 ; seule JarvisApp ajoute la navigation et app/build.gradle
ajoute le sourceSet de test épinglé. Manifest, package, minSdk26/target35, signature/configuration et dépendances
préservés. Aucun réseau, poids, audio, profil privé ou seconde inférence.

## Validations exécutées

- JDK17, wrapper Gradle8.13/Sdk35 : tests JVM, assembleDebug, lintDebug et assembleDebugAndroidTest réussis
  (`BUILD SUCCESSFUL in 49s`,76 tâches dont19 exécutées/57 en cache).
- **94 tests JVM réussis**, dont84 historiques +10 nouveaux, zéro échec/erreur/ignoré. Les nouveaux cas couvrent
  bus CORE réel local, absence de CORE, annulation avant ACK, doublons/réponses tardives, rupture/reconnexion,
  ressources inconnues, démonstration et refus de fausse réponse injectée.
- Lint : **zéro issue**. Garde52fichiers, source épinglée et absence du bus CORE dans l'APK : PASS.
- Sept PNG et archives AVA : SHA/CRC/transparence/aller-retour ZIP vérifiés.
- APK signé valide, package/minSdk/target inchangés. Aucune permission Internet, micro ou stockage global ;
  seul receiver privé AndroidX déjà existant. Aucune installation.
- Deux nouveaux tests d'interaction Compose compilés **non exécutés** : pas d'émulateur/image système/KVM
  disponible. Aucun pixel, capture ou essai physique inventé. Petite fenêtre/rotation/écran doivent encore
  être exercés sur émulateur ; les tests historiques de stockage ont été réexécutés en JVM.

Commandes et résultats machine : `RECEIPT-UI-01.json`. Dans ce cloud, les commandes Gradle utilisent le helper
existant `bash /workspace/JAVIS-Local-v2/tools/identity-cloud-env.sh ./gradlew ...` pour JDK/SDK/CA/proxy partagés,
sans modifier CORE. En environnement Android configuré, utiliser directement le wrapper de ce dépôt.
Prochaine action : qualifier l'écran en émulateur, puis proposer séparément le transport et accès au contenu
CORE. Aucun élargissement de contrat ou activation inter-APK n'est implicite.

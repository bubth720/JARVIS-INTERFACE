# UI-02 — éditeur de personnalité et consentements

**Code de laboratoire fonctionnel ; CORE réel NON CONNECTÉ.**
Base intégrée : AVA-04 `283ed2f3cd361d5b0b6bc927432bf836af8db1f6`, qui contient AVA-03.
Base de préparation : AVA-03 `ab0a3f5a992a90ca53dc20c9c56d3bd8a0c2b3a0`.
Branche de livraison : `feat/ui-02-delivery` ; le bilan final de l'intégration
indiquera son commit et le run distant. Aucun merge ni modification de `main`.

## Utilisation

Depuis le dashboard : **Identité et personnalité**. Au premier affichage, aucun
profil n'est chargé ; l'éditeur et la mémoire sont inertes. Cliquer volontairement
**Ouvrir le laboratoire local** pour examiner les deux profils de démonstration
Athéna/Neutre en RAM. Cela n'active pas Athéna et ne connecte pas CORE.

Choisir défauts ou DAILY/WORK/URGENT. Un contexte hérite de ses défauts tant que
l'override explicite n'est pas coché. Les sliders sont bornés et accessibles par
leur nom. Les goûts du personnage sont marqués SIMULÉS. Les gabarits avant/après
et paramètres effectifs respectent les plafonds WORK/URGENT, sans réponse LLM.

**Proposer et comparer** ouvre la liste des différences, le digest et l'epoch.
**Approuver** est désactivé jusqu'à confirmation de cet aperçu exact. Le refus,
l'expiration, la concurrence de révision ou l'absence d'ACK ne deviennent jamais
un succès. UNKNOWN impose un rechargement. Réinitialiser/restaurer propose une
nouvelle révision, après approbation, en conservant l'historique.

La vue mémoire est fermée par défaut, même en labo. Autoriser sa projection puis
saisir un exemple public fictif ; une proposition seule n'enregistre rien. Confirmer
le contenu exact pour créer une mémoire RAM, suspendre/reprendre, demander une
suppression confirmée ou sélectionner un export. Chaque export présente son JSON
et son digest, demande consentement puis une destination SAF nouvelle et vide.
La copie est relue pour vérification ; révocation et résultat inconnu ne produisent
pas un message de succès. Aucun import ne crée de mémoire.

La rotation conserve le brouillon dans le ViewModel de l'activité, sans SavedState
personnel, fichier de profil ni DataStore supplémentaire. **Retour** ferme le labo,
révoque ses projections et libère sa RAM ; aucun historique CORE réel n'est effacé.
Le header défile séparément en fenêtre courte afin de garder l'éditeur accessible.
Les contrôles ont des labels sémantiques ; TalkBack physique reste à qualifier.

## Fichiers et propriété

- `ui/identity/IdentitySettingsPort.kt`, `LaboratoryIdentitySettingsPort.kt` :
  projections et simulateur RAM, défaut indisponible.
- `ProfileDiffUi.kt` : différence brute/effective et aperçu éditorial borné.
- `IdentitySettingsController.kt`, `PersonaSettingsViewModel.kt` : flux unidirectionnel,
  clôture de génération, digest/epoch, ACK et invalidations de consentement.
- `IdentitySettingsScreen.kt` : éditeur, accessibilité et confirmation explicite.
- `ui/memory/MemoryConsentUiPort.kt`, `ConfirmedMemoryPanel.kt` : projection opt-in,
  consentement, suspension/suppression, export SAF volontaire ; pas de base locale.
- `JarvisApp.kt` : patch minimal séparé, navigation seulement. Fichier commun à
  intégrer par le responsable une fois AVA-04 isolée ; aucune édition de l'avatar.
- `IdentitySettingsTest.kt` : 20 tests JVM ; `IdentitySettingsInteractionTest.kt` :
  cinq scénarios Android compilés, dont recréation réelle de MainActivity.

## Résultats préparatoires réellement exécutés

Validation dans une copie temporaire isolée AVA-03, avec les nouveaux fichiers et
le patch de navigation. Le checkout du responsable et ses branches n'ont pas été
modifiés par la préparation UI-02.

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest --no-daemon --max-workers=1
```

**BUILD SUCCESSFUL en 1m10s**, 76 tâches (21 exécutées, 55 incrémentales).
**145 tests JVM réussis**, 0 échec/erreur/ignoré (125 précédents +20 nouveaux).
**Lint : No issues found.** Deux APK présents. Les cinq tests Android nouveaux
sont **compilés mais non exécutés** par cette préparation ; aucun émulateur ni
appareil personnel n'a été lancé/installé par cet agent.

Les assertions JVM couvrent défaut déconnecté, opt-in, bornes, héritage/suppression,
policy de contexte, conservation des règles lexicales, digest/epoch, expiration,
CAS, restauration, mutation après aperçu, UNKNOWN, ACK tardif après fermeture,
consentement mémoire exact, révocation, export et référence périmée.

[Rapport et empreintes de préparation](validation/build-staged.json),
[JUnit UI-02](validation/ui02-jvm.xml), [alignement CORE](CORE-ALIGNMENT.md).
Les captures et le run GitHub du commit intégré doivent être ajoutés par l'intégration.

## Intégration réellement validée

Base commune intégrée : AVA-04 `283ed2f3cd361d5b0b6bc927432bf836af8db1f6` (code de galerie `1d10ddb`, puis reçu de validation).
`./scripts/check.sh connectedDebugAndroidTest --no-daemon` : **BUILD SUCCESSFUL
5m56s**, 77 tâches (17 exécutées,60 incrémentales). **149 tests JVM** (129 base
AVA-04 +20 UI-02), **53 tests Android** (48 base +5 UI-02), zéro échec/erreur/ignoré.
`assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest` et
`connectedDebugAndroidTest` réussis ; Lint **No issues found.**

L'examen des premières captures a révélé un fond transparent laissant l'ancien
écran visible derrière l'éditeur. Une Surface opaque et sa couleur de contenu
corrigent ce défaut ; les valeurs de comparaison sont présentées en français
sans modifier le digest ni les valeurs exactes du port. Les 149/53 tests ont été
réexécutés après correction, avec succès. La galerie AVA-04 reçoit le même correctif
sur sa propre branche ; l'intégration conserve sa version corrigée.

Captures du vrai Compose, données publiques de labo seulement :
[défaut non connecté](validation/captures/disconnected.png),
[laboratoire volontaire](validation/captures/laboratory.png),
[diff exact non approuvé](validation/captures/exact-diff-unapproved.png).
Aucun visage privé n'est publié. Le contrôle manuel n'a pas accordé de consentement
réel CORE ; le dialogue capturé reste non approuvé.

[Comptes et empreintes intégrés](validation/build-integrated.json),
[JUnit Android](validation/android-integrated.xml). La persistance via propriétaire
CORE reste non exécutée faute de port autorisé ; les tests RAM ne la remplacent pas.
Tests sur POCO/Redmi, TalkBack physique et API35 non exécutés ; minSdk26 préservé.

## Limites

Aucune personnalité active, connexion CORE réelle, écriture durable de profil,
lecture de mémoire personnelle ou migration n'est revendiquée. Les réglages RAM
sont volontairement perdus à la fermeture du laboratoire/processus. Le propriétaire
et sa communication restent à qualifier sous autorisation distincte. Les budgets,
signatures, permissions Android, DataStore V1 et classes d'avatar sont inchangés.

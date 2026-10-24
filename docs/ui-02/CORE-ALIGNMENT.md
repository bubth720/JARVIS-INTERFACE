# UI-02 — alignement CORE et limites

Référence lue intégralement pour les classes pertinentes : dépôt réel
`bubth720/JAVIS-Local-v2`, source ID-02
`283423cb8b9d567539f33cf7f2f1fafa602cf974` et MEM-02
`49ad428e94edd75c2a1d115c8c2504f26609eace`.
[Chemins et empreintes exactes](CORE-ALIGNMENT.json). Les sources sont inspectées
hors checkout ; aucune classe CORE ni copie de sa base n'est ajoutée à l'APK.

## ID-02

- `IdentityProfile.kt` définit un éditeur de laboratoire, non des champs wire de
  `jarvis.identity/1`. Valeurs 0..100, nom ≤120 octets UTF-8 sans contrôle,
  ATHENA=(75,60,100,TU,ELEGANT), NEUTRAL=(50,0,0,TU,PLAIN), maximum 64 révisions.
- `ProfilePreviewEngine.kt` distingue l'override brut de sa valeur effective :
  supprimer un override identique aux défauts produit malgré tout une différence
  `override:WORK → INHERIT_DEFAULTS`. L'UI conserve cette distinction.
- `ContextStylePolicy.kt` limite l'humour WORK à 30 et impose URGENT humour=0,
  initiative≤40. Les règles lexicales sont suspendues en URGENT.
- `LanguageRuleEngine.kt` utilise un vocabulaire fini de gabarits, pas du texte
  généré ni des documents personnels. L'UI préserve les règles existantes et
  présente les modèles avant/après explicitement comme non-LLM.
- `IdentityChangeJournal.kt` lie nonce/base/révision suivante par digest, expire à
  90 secondes et applique une révision par CAS. L'UI transporte un digest opaque
  du propriétaire ; son simulateur RAM a un digest **UI-lab différent**, jamais
  annoncé comme un digest wire/une signature de CORE. L'epoch est une clôture de
  projection/session UI supplémentaire, pas un nouveau champ de contrat figé.
- `ProfileRepository.restore` crée une nouvelle révision, sans supprimer les
  anciennes. Le laboratoire démontre ce comportement en RAM ; il n'ouvre ni ne
  migre SQLite et ne touche pas l'archive B.
- **Important :** `IdentityComposerAdapter.proposedProfileSupport` retourne
  `CONTRACT_EXTENSION_REQUIRED`. L'éditeur ne peut donc pas activer ces pourcentages
  dans une personnalité CORE réelle en convertissant arbitrairement le contrat B.
  Le port de production reste absent et l'écran démarre NON CONNECTÉ.

## MEM-02

- `MemoryApprovalWorkflow` exige session, exact contenu affiché, décision explicite,
  contrôle de génération, péremption 90s et consentement à usage unique.
- `ConfirmedMemoryStore` conserve seul les mémoires de propriétaire et ses traces.
  Aucun store de profil/mémoire n'est créé dans INTERFACE. Le labo utilise seulement
  des objets en RAM et n'affirme jamais un ACK durable.
- `MemoryRecallIndex`, `ConfirmedMemoryLookup` et `ProjectionPolicy` sont fermés
  par défaut et excluent SENSITIVE. L'UI n'expose aucun choix permettant d'activer
  cette portée ; elle filtre aussi les projections pour défense en profondeur.
- `MemoryExportService` lie aperçu/références/digest/session, refuse les destinations
  non vides/inconnues et n'a pas d'application automatique des imports. L'UI lab
  peut exporter volontairement **sa RAM** via SAF, après confirmation puis vérification
  de copie. Un résultat de fournisseur inconnu n'est jamais affiché comme succès.
- Le format `jarvis.ui.memory-lab-export/1` est un export de démonstration, distinct
  de `jarvis.confirmed-memory-export/1`. Il ne contient aucune autorisation de
  créer une mémoire ni d'activer une personnalité. Aucun import-apply n'est fourni.

## Qualification

Les ports sont des projections pour l'éditeur et des implémentations RAM explicitement
opt-in. Aucun service, Binder, signature permission, transport inter-APK ou moteur
LLM n'est installé/activé. Le futur adaptateur de propriétaire doit être qualifié
et autorisé séparément ; sa persistance, ses consentements et ses ACK réels ne sont
pas revendiqués par les tests du laboratoire.

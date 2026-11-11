# V1-A — CORE réel + moteur JARVIS-LLM (A02) + INTERFACE

Laboratoire Android autonome (`fr.jarvis.v1a.lab`, min29/target35), séparé de l'application
historique INTERFACE et du laboratoire M50-S1B, qui restent inchangés (garde successeur
`tools/check_scope.py`, qui exécute aussi la garde M50 originale sur son commit `b9a6c19`).

## Composition

- **CORE** (`bubth720/JAVIS-Local-v2@5da9838`, inchangé) : `SessionCoordinator`, `LocalLanguageModel`,
  `InferenceContract`, modèles et événements — 6 fichiers extraits par blob Git.
- **INTERFACE** (`3a08412`, inchangé) : client de conversation et port d'événements — 3 fichiers.
- **LLM** (`bubth720/JARVIS-LLM@c00035b`) : `libjarvis_llm_a02.so` compilé depuis `a02/native`, qui
  inclut le `bridge.cpp` protégé inchangé. Chargement **uniquement** par `loadSealedFd` (memfd scellé,
  SHA-256 revérifié) ; le chargeur par chemin n'est pas exporté. Un seul moteur dans l'APK ;
  `llama-android` de CORE n'y est pas.
- Code du labo : `A02LanguageModel` (implémente `LocalLanguageModel` de CORE), `V1aCoreTransport`
  (protocole M50 inchangé, sans aucune réponse synthétique), `ConversationHistory` (multi-tour borné
  par `validateInference` de CORE), `ModelStore` (import SAF + SHA-256 saisi, copie atomique).

## Utilisation (après autorisation explicite uniquement)

1. Saisir le SHA-256 attendu du GGUF autorisé, puis « Importer un modèle GGUF local ».
2. « Démarrer JARVIS » : vérification A02 et chargement. Échecs visibles, jamais remplacés par du texte.
3. Mode avion possible : aucune permission, aucun accès réseau.

Aucun modèle n'est embarqué ni téléchargé. **Statut : GO-LAB-ONLY** tant que DEVICE-01 n'est pas
réalisé et que la provenance Android du GGUF n'est pas approuvée.

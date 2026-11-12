# V1-B — conversation multi-tour corrigée, réponses jusqu'à 128 jetons, mesures Android

Laboratoire Android autonome (`fr.jarvis.v1b.lab`, min29/target35, testOnly). V1-A (`labs/v1a`,
`4ed3f99`), le laboratoire M50 et l'application INTERFACE historique restent **inchangés** : la garde
successeur `tools/check_scope.py` exécute la garde V1-A sur `4ed3f99` (qui exécute elle-même la garde
M50 d'origine sur `b9a6c19`), puis gèle tous les fichiers de V1-A.

## Ce qui change par rapport à V1-A

| Sujet | V1-A | V1-B |
|---|---|---|
| Format du dialogue | historique aplati dans **un seul** message `user` (`UTILISATEUR: … ASSISTANT: …`) : les réponses précédentes ressemblaient à du texte utilisateur | vrais rôles ChatML `system` / `user` / `assistant` via `generateStreamTurns` (A02), validés par le chemin P2 protégé |
| Troncature | budget CORE en **octets** sur tout l'historique | budget CORE (inchangé) sur la question courante ; historique choisi par **comptage exact de jetons** (`countTurnsTokens`), les échanges les plus récents d'abord, 3 au maximum (limite protégée de 8 entrées) |
| Plafond de sortie | 48 (test) / 80 (défaut) ≤ 96 (protégé) | **128 par défaut**, **256 en option expérimentale**, plafond dur 256, contexte 768 seulement (`a02/native/a02_output_policy.h`) ; arrêt naturel sur fin de réponse (EOG) |
| Consigne système | prompt CORE générique (documents, mémoire) | consigne courte subordonnée : retenir ce que l'utilisateur dit de lui, ne jamais prendre son prénom |
| Mesures | durée et premier texte visible | jetons du prompt, jetons générés, premier jeton (horloge native), jetons/s, total, Stop, mémoire, cycles de rechargement |
| Fin de session | déchargement | déchargement + destruction du handle natif + arrêt du thread ; rechargement par une nouvelle session |

## Inchangé (vérifié en CI)

Chargement A02 : memfd scellé (4 sceaux), SHA-256 sur les octets copiés puis re-haché, identité
inode/périphérique, aucun chemin fourni par l'hôte, chargeur par chemin non exporté, inode source jamais
projeté. Moteur protégé `bridge.cpp` et `native_configuration.h` : **non modifiés** (contexte 768,
**un seul thread**, lots, cache KV F16, délai d'annulation de 90 s). Le plafond de sortie est
réinitialisé à 96 par chaque chargement protégé ; l'hôte doit le redemander, sinon il refuse de démarrer.

## Plafond configurable et documenté

- Défaut : `V1bPolicy.DEFAULT_OUTPUT = 128`. Option : case « Expérimental : plafond 256 jetons ».
- Bornes natives : `1 ≤ plafond ≤ 256`, et `plafond + 256 + 8 ≤ 768`. Au-delà : `A02_OUTPUT_POLICY_REFUSED`.
- Le moteur protégé refuse toujours `maxTokens > plafond` et `jetons du prompt + maxTokens + 8 > 768`.
- La mémoire résidente ne dépend pas du plafond : le cache KV est dimensionné par le contexte (768).
- Le budget CORE (`validateInference`, en octets) s'applique à la question : avec 256, les questions
  admises sont plus courtes (au plus 184 octets UTF-8) qu'avec 128 (au plus 312 octets).

Aucun modèle n'est embarqué ni téléchargé par l'application. **Statut : GO-LAB-ONLY** ; le POCO
(DEVICE-01) n'est pas qualifié sans essai physique autorisé.

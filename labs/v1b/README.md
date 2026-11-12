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
| Consigne système | prompt CORE générique (documents, mémoire) | consigne subordonnée **routée par intention** (message sur le prénom → consigne « prénom » ; sinon consigne générale) + **mémoire de session** : le dernier prénom déclaré par l'utilisateur est transmis comme fait connu, même hors de la fenêtre d'historique |
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
- Le budget CORE (`validateInference`, en octets, inchangé) compte la consigne réellement envoyée :
  question générale ≤ 299 octets UTF-8 avec 128 (≤ 171 avec 256) ; message sur le prénom, consigne
  « prénom » + fait mémorisé, ≤ environ 175 octets avec 128 (≤ environ 47 avec 256). Au-delà : refus
  visible « Question trop longue pour le budget CORE », rien n'est enregistré.

## Choix de la consigne (mesuré, pas supposé)

Étude sur le modèle réel (banc Linux, JARVIS-LLM `a02/tests/jni/SealedNativeBridge.java`, porte stricte
`a02/tests/dialogue_gate.py`) : 4 tours d'étude, 13 consignes. Aucune consigne unique ne traite à la fois
le rappel du prénom et les questions générales avec Qwen3-0.6B (meilleur : 2/4). Un gabarit cité
(`« Vous vous appelez <prénom> »`) est recopié tel quel par le modèle. Le routage par intention + mémoire
atteint 3/4 (tous les scénarios de prénom) ; l'échec restant est une erreur de connaissance
(« capitale de l'Italie : Lucca »). Les réponses restent intégralement générées par le modèle.

Aucun modèle n'est embarqué ni téléchargé par l'application. **Statut : GO-LAB-ONLY** ; le POCO
(DEVICE-01) n'est pas qualifié sans essai physique autorisé.

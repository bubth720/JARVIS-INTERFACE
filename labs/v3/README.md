# JARVIS V3 — Holographic Cockpit (labs/v3)

Réalisation du cahier des charges « JARVIS / INTERFACE V3 — Holographic Cockpit » (13/11/2026). Périmètre : **A** sphère holographique, **C** fiches contextuelles, **D** menu radial. **B** (avatar) est reporté.

## Isolement

- Application séparée `fr.jarvis.v3` (« JARVIS V3 »), qui peut s'installer à côté de la V1 livrée `fr.jarvis.v1`.
- Le moteur est la session V1 qualifiée (CORE → LLM A02, whisper.cpp, Pocket TTS), compilée **sans modification** depuis `labs/v1`.
- `tools/check_scope.py` vérifie que les 430 fichiers du commit V1 `9599d99` sont inchangés, `labs/v1` compris. La seule addition autorisée est `labs/v3/`.
- Aucune modification de l'avatar, du LLM, de CORE ni de VOICE. Aucun accès réseau. Une seule permission : le micro.

## Composants (§08)

| Composant | Fichier | Rôle |
|---|---|---|
| VisualTheme | `VisualTheme.kt` | jetons de couleur §02, verre fumé, typographie, budgets de mouvement, icônes linéaires, décor HUD statique |
| HologramSphere | `HologramSphere.kt` | couches : halo, socle de projection, coque, réseau de nœuds, liaisons, anneaux orbitaux, noyau, accents ; gestes |
| SphereStateAdapter | `model/HoloState.kt` | état visuel dérivé de l'état observable du moteur ; ne commande jamais le moteur |
| ContextCardHost | `ContextCardHost.kt`, `model/Cards.kt` | fiches avec provenance, compactes ou agrandies, épingler, citer, explorer ; états vides honnêtes |
| RadialNavigation | `RadialNavigation.kt`, `model/Navigation.kt` | 5 destinations en arc, retour Android, variante en liste pour TalkBack |
| ConversationDock | `ConversationDock.kt` | saisie persistante, micro, envoi ou arrêt, onde réelle, accès radial |
| PerformanceGovernor | `PerformanceGovernor.kt` | profils Économie / Équilibré / Cinématique (régulateur M55-D réutilisé), chaleur, batterie, arrière-plan, mouvement réduit, mode statique |
| CockpitScene | `CockpitScene.kt`, `CockpitActivity.kt` | composition portrait et paysage, premier lancement, panneaux |
| Galerie des états | `StateGalleryActivity.kt` | prototype visuel étiqueté EXEMPLE, banc de mesure avant/après (non exportée) |

## Règles respectées

- Les mots-clés autour de la sphère viennent **uniquement** du dernier échange réel.
- Une fiche « sujet » ne contient que des phrases réellement présentes dans la conversation. Sinon : « Source indisponible hors ligne ».
- Une question écrite sur l'appareil (batterie, température, mémoire, stockage) reçoit une **mesure Android**, jamais une réponse du LLM.
- Documents (NEXUS) et Services (SERVICES) apparaissent comme **non connectés** ; aucun contenu inventé.
- La transcription s'affiche par un anneau indéterminé, sans aucun pourcentage. La réflexion n'affiche aucune « pensée » fictive.
- Les niveaux d'écoute et de parole viennent des RMS réels du micro et du PCM Pocket, avec seuil de bruit et lissage.

## Qualification

Workflow `.github/workflows/v3-cockpit.yml`, sur API 29 et 35, profil pixel_6, en mode avion. Les captures réduites et les rapports JSON sont versionnés dans `captures/run-<id>/`. Le rapport de mission se trouve dans JARVIS-UNIFIED `reports/v3/RAPPORT.md`.

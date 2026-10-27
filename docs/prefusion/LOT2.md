# UI-PF-01 — deuxième lot pré-fusion / 2026-10-27

Base mission vérifiée Git/GitHub : `82675ade011e69eb3bccd99efbf46d0697a3276b`, branche `mission/prefusion-gates-20261026`.
Développement : `codex/prefusion-lot2-20261027`. PR brouillon vers la branche mission, jamais main. SHA publié et résultats CI exacts dans la description de PR ; aucune auto-référence inventée.

## Changement borné

Correction de deux défauts de frontière : READY provenant d’une origine NONE est projeté UNAVAILABLE, et réutilisation d’eventId à une nouvelle séquence entraîne UNKNOWN au lieu de perdre silencieusement un événement. Les vrais doublons déjà reçus restent ignorés comme auparavant. Deux tests de régression ajoutés, sans supprimer ni changer les assertions historiques.

## Validation et limites

Gardes UI01, AVA04 et FUSION02 locaux PASS ; CI propriétaire complète conserve build/tests/lint et compilation instrumentation. Aucun nouvel essai instrumenté API26/35 ou physique local.

Les nouveaux tests Kotlin sont soumis à la CI après publication ; aucun résultat anticipé. Tests JVM/host != instrumentation != preuve physique. SDK, ABI, moteurs, modèles, budgets, contrats et anciennes preuves conservés. Aucun dépôt du premier lot modifié. Les gardes historiques CORE refusent volontairement le nouveau périmètre au HEAD ; leur garde dédié historique n’est ni assoupli ni présenté comme preuve actuelle. Les résultats nouveaux sont distincts.

## Blocages et décision

Transport CORE UID/certificat/permission non approuvé. Personnalité transactionnelle et nouvelles transitions avatar restent non implémentées dans cette tranche ciblée ; aucun art, stockage, contrat ou citation active modifié.

Les essais sur appareil personnel, toute migration ou activation sensible attendent validation. Aucun merge main, déploiement, permission réseau/runtime ou nouvel import de dépendance. Les missions complètes restent PARTIAL/BLOCKED, seul ce lot ciblé est livré.

## Retour arrière et suite

Revert des commits de cette branche ; aucune migration ni donnée produit à restaurer. Lire la CI au SHA publié, corriger ses échecs sans skip ; ensuite traiter une tranche OFF indépendante. L’architecture d’ensemble, ADR-002 et les raccordements actifs ne sont pas présumés approuvés.

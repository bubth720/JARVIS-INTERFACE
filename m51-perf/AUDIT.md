# M51.05 — audit court de UI-01C et méthode de mesure

Module INTERFACE, branche `codex/m51-ui-performance-review`, référence exacte PR16 `0dfeef8ff09d60a68d18398ccb6b2530f3b87df4`. Aucun changement aux sources actives. La référence et ses images sont publiques ; aucune donnée privée ou asset utilisateur ajouté.

## Constats de code, pas causes mesurées

`CockpitScreen.kt:285–290` maintient une valeur `pulse` toutes les 33/66 ms via delay, indépendamment du vsync. La valeur est lue DANS le lambda Canvas (`:316`, `:323`, `:332`, `:340`). Ce fait n'établit pas que toute la composition est recomposée à chaque tick : ne pas attribuer le jank à une recomposition globale sans tracing. Le Canvas alloue plusieurs couleurs/shapes et un radialGradient au dessin. Le coût n'a pas été mesuré isolément.

`CockpitScreen.kt:94–105` actualise l'heure et PowerManager toutes les 30 s en RESUMED ; un arrêt de lifecycle désactive animate. La scène n'est composée que sur les routes concernées. La réduction des animations système n'est pas utilisée par le timer de pulse ; vérifier le respect du mode sans animation. `LocalWorkspace` emploie SQLite et calculs de collections synchrones depuis les écrans ; la taille du corpus et le thread doivent être tracés avant tout refactor. Aucune causalité ni gain n'est affirmé ici.

Un candidat aligné sur une animation Compose/vsync, borné en cadence visuelle par état dérivé et interrompu sur ReduceMotion/ECO/masquage, est une hypothèse pertinente. Attention : une animation infinie peut empêcher les tests Compose d'attendre l'idle. Tester les effets réels de Global animator_duration_scale=0, la navigation, l'arrêt et le nombre de frames générées. Ne pas présenter une cadence cible de 15/30 Hz comme un résultat d'animation mesuré.

## Preuves antérieures analysées

Les fichiers sous docs/evidence/ui-01c/ sont des captures **du commit de référence**, pas de nouveaux essais de M51. Téléphone générique API34 : 151 frames, modern 94,70 %, legacy 0,66 %. Tablette générique API34 : 151 frames, modern 98,01 %, legacy 51,66 %. Les deux définitions divergent et ne doivent être ni moyennées ni remplacées par un seuil universel de 16,67 ms.

La tablette rapporte des percentiles GPU de 4950 ms avec une forte occupation du dernier bucket. Il s'agit d'une valeur de histogramme/bucket/sentinel dont la durée exacte et la cause ne sont pas qualifiées ; **ce n'est pas une preuve de GPU à 4,95 secondes**. Les anciens fichiers n'ont pas les lignes PROFILEDATA de timestamps bruts. Aucun percentile raw ne peut être reconstruit depuis ces histogrammes et aucune performance physique POCO/Redmi ne peut être déduite.

## Banc livré

`gfxinfo.py` préserve les compteurs modern/legacy, les percentiles Android binned et leurs limites ; il extrait les CSV framestats par fenêtre, conserve les outliers et compte les lignes rejetées. p50/max raw si au moins une frame valide, p95 raw seulement à partir de 20 frames comparables. Les fenêtres ne sont pas additionnées ; les sections de processus multiples rendent le résumé UNKNOWN.

`comparison.py` refuse une comparaison sans commit/APK et mêmes API, ABI, taille/densité/refresh, profil, échelles animations, scénario/durée, révision émulateur et renderer. Aucun delta manquant n'est remplacé par zéro. Une comparaison valide de conditions n'attribue pas automatiquement PASS à la fluidité.

Des tests de parseurs utilisent les captures publiques historiques et des CSV explicitement synthétiques. Ces tests ne sont pas une campagne Android, une qualification graphique ou un benchmark du LLM. La nouvelle campagne Android et le comparatif M51 sont NOT_RUN dans cette branche ; ils appartiennent à la composition root à son SHA précis.

## Rollback et gates

Abandonner cette branche retire uniquement les outils/documents de revue. Aucun fichier actif n'a été modifié ; aucune installation, modèle, permission, garde, seuil ou contrat productif n'est activé. La prochaine action est de collecter baseline et candidat M51 sur le même émulateur/API réellement accessible, puis d'appliquer ces parseurs aux traces expurgées.

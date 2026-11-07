# Capture comparable, sans modification de protections

L'APK M51 est pilotée par la branche de composition, pas par ce dossier. Le benchmark doit retenir une identité APK/commit/tree et un profil identique par paire. Les performances physiques restent NOT_RUN tant qu'aucun appareil autorisé n'est testé.

1. Vérifier un serial émulateur explicite et `ro.kernel.qemu=1`, API/ABI, accès KVM déjà présent et réellement utilisable. Aucun chmod, udev, module noyau, contournement ou cible personnelle. Ne pas recommencer les hôtes ARM64 HV_UNSUPPORTED connus.
2. Enregistrer résolution/densité/refresh, API/ABI/image émulateur/renderer, mode énergie, échelles d'animations et scénario. La désactivation des animations Android n'arrête pas forcément le timer Canvas historique.
3. Alterner baseline/candidat ; séparer démarrage à froid, idle animé, idle ECO, navigation/scroll et masquage. Garder les mêmes fenêtres et durées (par exemple 30 secondes), repos et comptes d'interactions. Ne pas choisir les meilleurs essais et jeter les autres.
4. Capturer les valeurs brutes `adb -s SERIAL shell dumpsys gfxinfo PACKAGE reset` avant le scénario et `adb -s SERIAL shell dumpsys gfxinfo PACKAGE framestats` après. Réserver les autres outils de profiling hors fenêtre lorsque leur coût perturbe la mesure. Un reset borne les compteurs mais pas nécessairement toutes les couches ; garder Stats since et les timestamps.
5. Capturer meminfo, identité display, crash/ANR expurgés et screenshots publics de fixtures. Ne pas publier SharedPreferences, DB utilisateur, prompts, chemins privés, clés, poids ou ressources artistiques privées.
6. Utiliser `python3 m51-perf/gfxinfo.py TRACE --output REPORT`. Les timestamps IntendedVsync/FrameCompleted sont soustraits uniquement entre eux, dans le même domaine Android. Ne pas soustraire un temps hôte, elapsedRealtimeNanos ou uptime externe sans calibration qualifiée.
7. Comparer avec comparison.py des enveloppes `{source_commit, apk_sha256, conditions, gfxinfo}`. Les champs de provenance saisis doivent être confrontés au vrai build/APK ; leur présence ne prouve pas leur authenticité. Conserver chaque échec/refus, des métriques null si absentes et aucun p95 raw sous 20 frames.

Les valeurs de FrameMetrics sont une autre source : conserver leur API/champ, cadence, pertes de callback et provenance. Ne pas fusionner leurs durées avec les compteurs gfxinfo ni inférer une cause CPU/GPU d'un seul compteur.

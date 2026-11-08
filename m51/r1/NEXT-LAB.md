# Préparation de la suite — aucune nouvelle tentative exécutée

Q1 est close après les échecs système. D1 et Q1 restent distinctes ; la limite de deux boots par API est consommée. Ne pas utiliser Re-run failed jobs ni pousser une modification exécutable pour rejouer Q1 silencieusement. Les rapports finaux `[skip ci]` ne modifient pas les assertions ni les résultats pour fabriquer un PASS.

## Correctifs du banc à préparer sur une nouvelle référence

1. Identifier versions/hash de l'émulateur et de l'image, AVD, accélérateur et géométrie. Configurer explicitement un fuseau IANA valide par l'option documentée `-timezone Etc/UTC`, sans changer les protections de l'hôte. Le crash Unknown/Unknown justifie cette piste ; il ne démontre pas la cause du crash SystemUI et ne garantit pas sa correction.
2. Remplacer l'assertion Wi-Fi instantanée R1 par le contrôle de stabilisation déjà présent dans m51/device_campaign.py : observation monotone bornée à 10 s, journal de toutes les valeurs, condition finale stricte wifi_on==0. Aucun settings put wifi_on0 ni timeout de test augmenté. Tests à préparer :1→0, toujours1, null, erreurADB, expiration ; tout état non attesté interdit l'admission. Ce correctif n'est pas appliqué dans cette livraison et ne doit pas être attribué au code Q1.
3. Passer explicitement l'identité de campagne au helper commun, liée au SHA/run, en refusant les contradictions. Ne pas réécrire le libellé R1-D1 des preuves Q1 passées.
4. Mesurer le coût des commandes de préflight et distinguer stabilité observée/budget de collecte. Ne pas augmenter arbitrairement 120 s ni réduire les contrôles de crash. Une observation incomplète reste BLOCKED.

Sources officielles : https://developer.android.com/studio/run/emulator-commandline ; https://developer.android.com/studio/run/emulator-acceleration . HVF était disponible sur les runners Intel mais Android y a échoué : le probe ne les qualifie pas.

## Nouvelle campagne explicitement identifiée

Une nouvelle campagne suppose un diff du banc identifié, ses tests de non-régression et des préconditions corrigées, avec une nouvelle référence et un compteur annoncé. Aucun achat, appareil personnel, accès privilégié ou assouplissement de garde. Ne pas relancer indéfiniment les mêmes paramètres.

Ordre : SystemUI/launcher/Settings stables sans JARVIS ; trois cas fautifs isolés ; si PASS, baseline 18 complète puis candidat 24, force-stop/restart et parcours métier ; ensuite frames comparables. Premier crash critique : arrêt de la campagne API et conservation des logs/fixtures/fenêtres/captures. Ne pas ignorer d'assertion ni remplacer le sélecteur par un accès direct à la base.

Sur Android admis, observer la fixture SAF puis la fenêtre sans modifier sélection/conservation. Navigation et projet isolés ; sampler séparé des benchmarks. Le contrôle ordonné capture/projet reste diagnostique. Si SnapshotStateObserver réapparaît, relever les deux threads/piles et la source Compose exacte ; aucun patch global captureToImage-sur-main ni upgrade arbitraire.

La décision technique restante est la disponibilité d'un laboratoire stable et sa correction vérifiable. Aucun accord de fusion ou installation personnelle n'est demandé sur ces résultats. Les modules externes restent hors périmètre ; M51-R1 ne sera acceptée qu'après réception Android réelle sur un laboratoire autorisé.

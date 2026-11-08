# M51 — cockpit de laboratoire indépendant

Base UI-01C exacte : `0dfeef8ff09d60a68d18398ccb6b2530f3b87df4` (PR16), tree `57ea00745ddfb0bc3c992c18ba4b61240c85706b`. Branche `codex/m51-v1-foundation`. Mandat : cahier des charges V1 Acceleration v2.0 du 7novembre2026, PDF SHA256 `93cb9f7bb8acf518ea4218ab2db0d450beba8aa14a465760f63a7bac70915c8c`.

L'hôte s'active seulement avec `-I m51/lab.init.gradle`. Deux APK `testOnly` : candidat `fr.jarvis.v1.lab`, comparaison `fr.jarvis.v1.lab.baseline`. Les **89 fichiers historiques restent identiques** ; aucun défaut de :app, certificat personnel, schéma de données ou contrat CORE/LLM ne change. Les données du nouveau paquet sont isolées, non importées depuis les applications historiques.

Les vraies sources UI-01C sont copiées automatiquement en sortie de build, comparées au Git exact et accompagnées de SHA256. Ce staging n'est pas une seconde copie maintenue du cockpit. Seul CockpitScreen est adapté par des remplacements à préimages uniques dans `prepare_sources.py` ; le reçu contient les hashes avant/après. Les ressources, CRUD, SQLite, navigation et tests originaux sont réutilisés. Le candidat ajoute « Modules & état » via Supervision, export volontaire expurgé et réduction des mouvements. Aucun modèle ni autre runtime n'est raccordé. Les espaces Projets/Finance/etc. restent des fonctions locales, pas des SERVICES intégrés.

Les huit modules cibles et UNIFIED ont des dimensions séparées code_present/connected/enabled/runtime_ready/user_approved/device_qualified. LLM reste BLOCKED ; les autres intégrations OFF. L'UI locale est LAB_ONLY, jamais ACTIVE_VERIFIED sans les six dimensions. Les boutons de preuve ouvrent une référence externe uniquement sur action utilisateur ; aucune permission INTERNET, aucune dépendance réseau de fonctionnement. La permission de signature interne AndroidX pour les receivers non exportés est conservée, pas supprimée pour produire un faux « zéro permission ».

## Qualification et preuves

La CI compile base, laboratoire baseline/candidat et tests, vérifie manifeste/testOnly/permissions/certificat/hashes. AndroidX inclut le petit helper `libandroidx.graphics.path.so` sur quatre ABI : ses octets doivent être **identiques** entre baseline et candidat. Ce n'est ni JNI LLM ni un moteur de génération ; la qualification Android16Kio reste NOT_RUN. Les APK/test APK d'une même campagne proviennent d'un unique build et sont transmis aux jobs appareils sans recompilation ni nouvelle signature.

Les tests appareils utilisent uniquement une série `emulator-*`, qemu=1, API et ABI vérifiés. Aucune écriture sur appareil personnel. KVM est inspecté sans chmod, udev, changement de groupe, module ou sudo. Si le runner x86_64 ne dispose pas de droits KVM, une seule tentative **logicielle TCG** bornée à300s est permise ; aucune répétition macOS HV_UNSUPPORTED/ARM64, aucun achat. Un échec de boot reste BLOCKED/NOT_RUN, pas un PASS Android. Les essais UI x86_64 ne qualifient jamais le LLM ARM64 ou les appareils physiques.

Le protocole `PERF-PROTOCOL.md` distingue frames modernes/legacy, FrameMetrics, démarrage, animation et ECO. Les valeurs ne prouvent ni TTFT CORE–INTERFACE ni autonomie/thermique. Les résultats définitifs du commit exact, captures, artefacts et SHA256 sont publiés dans la PR après vérification. Configuration d'un workflow ≠ résultat PASS.

[Rapport/critères](REPORT.md), [rollback](ROLLBACK.md), [guide Android](INSTALL-GUIDE.md), [contrats et composition](COMPOSITION.md).

M52 : NO-GO selon [audit LLM16](https://github.com/bubth720/JARVIS-LLM/pull/16). Aucun patch LAB_CLOUD, clé, téléchargement de modèle ou GATE-B implicitement autorisé. Les préparatifs NEXUS/SERVICES, AVATAR et la coordination UNIFIED restent séparés par SHA/PR. Les références artistiques approuvées sont absentes : décor provisoire conservé et avatar emplacement seulement.

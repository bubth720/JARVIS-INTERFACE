# REUSE FIRST R1+ — banc Android C1 isolé

## Réception C1 et correctif postérieur, sans nouveau boot

La campagne C1 est close au commit 7282fd769d4ab787467d08fd8059b2f1e18ca139,
run 42524234101, un boot/API sur Intel/HVF. API35 : ANR SystemUI avant JARVIS,
BLOCKED. API29 : préflight brut PASS, puis le seul test SAF commence et échoue ;
navigation, projet, suites 18/24, restart et benchmarks NOT_RUN. Le mode avion,
Wi-Fi 0 et fuseau Etc/UTC ont été effectivement observés sur les deux API.

Une revue du premier dumpsys API29 découvre un LAST ANR daté, ignoré par le parseur
historique : le PASS brut n'est pas accepté comme stabilité. Les preuves restent
inchangées. Le nouveau refus recorded_system_anr est testé sur un extrait hashé
de cette capture, sans requalification ni relance Android. Ce correctif résout
le défaut de classification reproduit ; il ne résout pas l'ANR ou le problème SAF.
La capture finale du sélecteur affiche Downloads sans élément, mais elle est
postérieure au finally du test qui supprime sa fixture : elle ne prouve pas
l'absence du fichier pendant les 20 secondes de sélection. L'état IS_PENDING,
les octets et la hiérarchie pendant la sélection restent à recueillir : Q1/C1
ne rapatrient pas le picker.jsonl pourtant écrit par le test instrumenté.

La branche C1 demeure figée et ne doit recevoir aucun push supplémentaire.
Les commits postérieurs de la branche ordinaire ne construisent/testent que le
banc. Aucun retry silencieux ou correction applicative n'est annoncé.

Base M51-R1 b9a9506785b8851f55789ed4329c9394a3937447, preuves D1/Q1 intactes.
Deux helpers R1 reçoivent une option explicite C1 ; leurs modes par défaut restent
R1-D1/R1-Q1, sans réécrire les anciens résultats. Sources app, tests historiques,
Compose, ressources, signature productive et protections restent inchangés.

La branche normale construit et teste seulement. Après réception de ce build,
une seule publication du même commit sur `codex/reuse-first-r1plus-c1-android`
autorise la campagne C1 : un boot par API29/35, aucun retry et aucun rerun de job.
Cette branche de campagne est ensuite figée. Les mises à jour documentaires ne
doivent pas y être poussées. La création de C1 n'est pas un nouveau budget D1/Q1.

Le probe Linux ne modifie aucun droit KVM. Si l'accélérateur n'est pas accessible,
la campagne choisit le runner standard Intel/HVF puis recontrôle son accès ; sans
accélérateur réellement disponible, Android reste BLOCKED sans boot logiciel.
L'émulateur reçoit `-timezone Etc/UTC`, mêmes 2 cœurs / 2048 Mio, boot 300 s,
préflight 120 s, assertions et instrumentation historiques inchangées. Le système
doit être stable sans aucun JARVIS installé, avant les trois reproductions isolées,
puis les 18 baseline, 24 candidat, restart et mesures. Premier incident critique :
arrêt de l'API concernée et conservation des preuves. Aucun achat ou appareil personnel.

L'observation Wi-Fi reprend le budget M51 de 10 s, avec timeout ADB restant,
toutes les valeurs enregistrées et refus des états inconnus/tardifs. L'identité
de campagne lie SHA, build, run et première tentative. Les performances du banc
sont des coûts de collecte, pas une amélioration du LLM ou du produit.

```sh
python3 -m unittest discover -s m51 -p 'test_*.py' -v
python3 m51/reuse_r1plus/check.py
```

Les tests utilisent des horloges injectées et cas synthétiques explicitement
identifiés ; seul un futur rapport Android réel peut qualifier le cockpit.
La collecte classe laboratoire/application/configuration/inconnu sans tirer une
cause applicative d'un crash système. L'absence de rapport après échec du runner
reste UNKNOWN jusqu'à lecture de son journal. Les anciennes gardes ne sont pas
affaiblies : elles sont rejouées sur leur base exacte, séparément du garde C1.

LLM, CORE externe, VOICE, renderer M54 et pouvoirs SERVICES restent OFF/BLOCKED.
Rollback : abandon de la branche et omission de `--campaign REUSE-R1PLUS-C1` ;
aucune migration ou restauration de données historiques.

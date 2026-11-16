# GPU-FORGE-G02 — runner et captures

Base auditée : f73d4f5c20739cbeae21479c4b3f107b90370456. Autorisation utilisateur du 16 novembre 2026 : corrections ciblées, CI et captures API29/35 ; aucune modification V1, modèle, architecture sensible ou installation téléphone.

Quatre échecs initiaux avec CAPTURES_ONLY=1 : healthy_run_completes, device_lost_between_tests, device_lost_during_a_test, shell_hangs_while_state_is_device. Le harnais recevait le mode de la CI et attendait les assertions de la campagne complète.

Les scénarios choisissent désormais explicitement leur mode : les sept historiques restent en campagne, onze scénarios couvrent la visite et la collecte. Le chemin de campagne complète, la préparation et les révisions des ressources sont comparés octet pour octet à la base. Le garde V1 et ses 430 fichiers restent intacts.

Le mode captures vérifie la perte d'émulateur après la visite et pendant les transferts, le code de sortie de l'instrumentation, l'index complet et les PNG reçus. Il ne déclare pas une capture absente comme réussie. L'avatar peut être explicitement indisponible ; le rapport en conserve la raison.

Le test Android attend la vraie proposition mémoire, vérifie chaque onglet effectivement rendu après fermeture/réouverture du panneau, et capture NEXUS dans son écran de bibliothèque. Les captures utilisent uniquement des données synthétiques. Athéna est le rendu procédural 2D disponible dans cette version.

Contrôles CPU :

```sh
PYTHONDONTWRITEBYTECODE=1 CAPTURES_ONLY=1 bash labs/v3/tools/tests/test_run_emulator.sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s labs/v3/tools/tests -p 'test_*.py' -v
python3 labs/v3/tools/check_scope.py --interface .
```

Les pushes sur la branche codex/gpu-forge-g02-captures-* déclenchent le workflow V3 en mode captures uniquement, après les gardes et tests CPU. La branche historique et workflow_dispatch avec captures_only=false gardent la campagne complète. Les modèles et leurs contrôles d'intégrité ne changent pas.

Les artefacts conservent PNG originaux, index, empreintes, logs, rapports JVM/Lint et SHA APK. Les JPEG de revue sont publiés par la CI dans captures/run-<id>, avec l'identifiant du run et du commit testé. Une campagne de captures réussie ne qualifie pas la campagne Android complète ou le téléphone.

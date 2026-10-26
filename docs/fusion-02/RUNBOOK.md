# Reproduire FUSION-02 INTERFACE

## Outils et builds

Java17, SDK35 / build-tools35.0.0, wrapper Gradle8.13 et versions du catalogue
existant. Ne pas installer une SQLite native ou modifier les dépendances pour ce lot.
Utiliser le checkout existant, sans worktree supplémentaire.

```sh
./scripts/check.sh --no-daemon
python3 scripts/check_ui01_scope.py --apk
python3 tools/avatar/check_ava04_scope.py
python3 scripts/check_fusion02.py --results
python3 scripts/check_athena_distribution.py app/build/outputs/apk/debug/app-debug.apk
python3 -m unittest discover -s tools/avatar -p 'test_*.py' -v
```

La CI produit un APK **sans art privé**. Pour la validation premium locale, suivre
`docs/athena-visible/DELIVERY.md` : répertoire d'assets privé hors checkout, ZIP validé,
propriété `-PathenaPrivateAssets=/chemin/prive`. Cette propriété est refusée en CI.
Ne pas mettre le répertoire privé dans les chemins upload-artifact.

## Émulateur de laboratoire seulement

Démarrer un AVD API26, mode avion. Vérifier `adb -s emulator-5554 shell getprop
ro.kernel.qemu` égal à1 avant les opérations de test. Aucun appareil personnel.
La suite historique AthénaPrimaryTest requiert le pack privé réel local ;
ce pack n'est ni téléchargé ni reconstruit par la CI publique.

```sh
./scripts/check.sh connectedDebugAndroidTest \
  -PathenaPrivateAssets=/chemin/prive --no-daemon
```

Scénarios ciblés sans moteur CORE :

```sh
./gradlew connectedDebugAndroidTest \
  -PathenaPrivateAssets=/chemin/prive \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jarvis.interfaceapp.fusion.FusionInteractionTest \
  --no-daemon
```

Après la suite, les captures synthétiques seules sont activables avec l'argument
instrumentation `fusionCapture=true`. Le test vérifie l'absence de `avatar-renderer`
avant toute capture. Extraire uniquement `files/fusion-disconnected.png`,
`files/fusion-unknown.png`, `files/fusion-portrait.png` de notre package debug.
Ne pas extraire ou publier les anciens fichiers athena-*.png.

## Scénarios manuels

1. Studio → Fusion : sept capacités NON_CONNECTÉ/UNKNOWN, aucune preuve supposée.
2. Ouvrir volontairement le laboratoire : bannière LAB, source synthétique seulement.
3. Choisir la source : URI fictive, version/page/offset/hash ; aucun picker SAF réel.
4. Aperçu CSV : somme0.30, aucune invocation SERVICES.
5. Examiner puis annuler : aucun reçu ; confirmer : reçu simulé, aucun fichier.
6. Refaire avec ACK perdu : UNKNOWN, nouvelle confirmation désactivée.
7. Révoquer avant la confirmation : REFUSED, aucun effet. Après engagement : aucune
   promesse d'effacement ni faux rollback.
8. Rotation/recréation : brouillon conservé. Arrêt forcé du processus puis lancement :
   laboratoire fermé ; données dashboard/workspace inchangées ; aucun replay.
9. Fenêtre courte, portrait/paysage, texte agrandi : contrôles atteignables par
   défilement ; lire statuts et commandes avec TalkBack sur appareil disponible.
10. Vérifier les quatre panneaux et le portrait premium dans le build privé ;
    déplacement/redimensionnement/verrouillage, paramètres et dispositions conservés.

Ne pas transformer ces vérifications de façade en réception multimodule. Les tests
SAF réel, grants/UID/signatures, FTS5, exports SERVICES et mort d'un worker producteur
requièrent les gates et autorisations indiqués dans REPORT.md. Ne pas désinstaller
une version utilisateur pour contourner une signature : aucune migration autorisée.

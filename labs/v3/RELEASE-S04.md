# Build release signé de JARVIS V3

La CI S04 construit une variante release non signée pour inspecter son APK
sur disque. Le build de qualification utilise des émulateurs et publie les
rapports. La signature de distribution se fait localement, par le propriétaire
du certificat de JARVIS.

## Construction

Partir du commit qualifié sur les deux API. Reprendre les quatre révisions
consommées et la préparation des sources de `v3-cockpit.yml`. Utiliser Java 17, Gradle 8.13,
SDK 35, Build Tools 35.0.0, NDK 28.1.13356709 et CMake 3.22.1. Exécuter les gardes V1
et les contrôles de provenance avant la compilation.

Reprendre la commande Gradle V3 de la CI, avec ses chemins vérifiés, et utiliser
`:app:assembleRelease` comme tâche. Inclure `arm64-v8a` pour les appareils ARM64.
L'APK de sortie est `labs/v3/app/build/outputs/apk/release/app-release-unsigned.apk`.
Les ressources sont importées après installation pendant une mission de qualification
distincte. [Workflow V3](../../.github/workflows/v3-cockpit.yml)

## Audit statique et signature

Vérifier l'APK non signé avec `tools/inspect_s04_apk.sh`. L'audit confirme le
paquet `fr.jarvis.v3`, la permission micro, le statut non débogable, le refus du
trafic en clair, `allowBackup=false` et les exclusions dans les deux blocs
`cloud-backup` et `device-transfer`. Les exclusions couvrent notamment les
fichiers de conversation et les bases privées de la mémoire et de la bibliothèque.
Le comportement du transfert entre deux appareils dépend encore d'un essai dédié.

Sur un poste de signature de confiance, employer le keystore de distribution
protégé du propriétaire. Stocker ce keystore hors du checkout. Renseigner le chemin
local et l'alias dans l'environnement local, et laisser `apksigner` demander les
mots de passe via son invite. Exécuter la signature sans traçage shell ni
enregistrement de session. Conserver un répertoire de sortie privé.

```bash
set -euo pipefail
set +x
umask 077
bt="$ANDROID_HOME/build-tools/35.0.0"
unsigned=labs/v3/app/build/outputs/apk/release/app-release-unsigned.apk
release_dir=$(mktemp -d "${TMPDIR:-/tmp}/jarvis-v3-release.XXXXXX")
bash labs/v3/tools/inspect_s04_apk.sh "$unsigned" "$bt" "$release_dir/unsigned-audit" release
"$bt/zipalign" -P 16 4 "$unsigned" "$release_dir/aligned.apk"
"$bt/apksigner" sign --ks "$JARVIS_RELEASE_KEYSTORE" --ks-key-alias "$JARVIS_RELEASE_ALIAS" \
  --out "$release_dir/jarvis-v3-release.apk" "$release_dir/aligned.apk"
"$bt/apksigner" verify --verbose --print-certs "$release_dir/jarvis-v3-release.apk"
"$bt/zipalign" -c -P 16 4 "$release_dir/jarvis-v3-release.apk"
bash labs/v3/tools/inspect_s04_apk.sh "$release_dir/jarvis-v3-release.apk" "$bt" "$release_dir/signed-audit" release
sha256sum "$release_dir/jarvis-v3-release.apk"
```

Comparer l'empreinte SHA-256 du certificat imprimée par `apksigner` à la référence
de distribution connue du propriétaire. Le reçu de build peut contenir le commit,
l'empreinte du certificat public, l'empreinte de l'APK et les rapports statiques.
Les clés, mots de passe et keystores restent dans leur stockage protégé.

La qualification émulateur S04 porte sur l'APK de test. Une installation existante
signée avec un autre certificat exige une décision distincte sur les données avant
toute désinstallation ou mise à niveau.

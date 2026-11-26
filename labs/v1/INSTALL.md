# JARVIS V1 — installation et utilisation

APK `fr.jarvis.v1` (nom affiché : **JARVIS**), Android 10 (API 29) minimum, ARM64 (`arm64-v8a`, ex. POCO) et x86_64 (émulateurs).
Elle s'installe **directement sur le téléphone** (plus de mode `testOnly`).

Une seule permission : le **microphone**, demandé au premier appui sur « Parler ». **Aucune permission Internet** :
l'application ne peut joindre aucun réseau. Toute la conversation reste sur l'appareil ; le mode avion ne change rien.

## 1. Fichiers nécessaires (non inclus dans l'APK)

| Rôle | Fichier | Obligatoire ? | Taille | SHA-256 |
|---|---|---|---|---|
| Modèle de langage | `Qwen3-0.6B-Q4_K_M.gguf` (unsloth/Qwen3-0.6B-GGUF @ `50968a44`, apache-2.0) | **oui** | 396 705 472 o | `ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a` |
| Reconnaissance vocale | `ggml-base-q5_1.bin` (ggerganov/whisper.cpp @ `5359861c`, MIT) | non (sans lui : pas de micro) | 59 707 625 o | `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898` |
| Voix (Pocket TTS, voix publique « alba ») | `pocket-fr-6l-alba.zip` (JARVIS-VOICE `scripts/pocket/prepare_model.py` @ `92d0745`, CC BY 4.0) | non (sans elle : réponses écrites) | ≈ 100 Mo | chaque fichier du ZIP est vérifié contre le verrou épinglé `model-lock.json` |

Les trois fichiers, vérifiés, sont publiés par la CI dans l'artefact **`JARVIS-V1-RESSOURCES-<commit>`**
(avec `SHA256SUMS.txt` et l'attribution `MODEL_LICENSE.txt`). Les deux premiers se téléchargent aussi directement :

- https://huggingface.co/unsloth/Qwen3-0.6B-GGUF/resolve/50968a4468ef4233ed78cd7c3de230dd1d61a56b/Qwen3-0.6B-Q4_K_M.gguf
- https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base-q5_1.bin

L'application **vérifie** chaque fichier (taille + SHA-256) avant de l'utiliser et refuse tout fichier différent. Elle ne télécharge rien.

## 2. Installation — sans ordinateur (recommandé)

1. Télécharger l'artefact `JARVIS-V1-APK-<commit>` (ZIP), l'extraire, vérifier si possible `app-debug.apk` contre `apk-sha256.txt`.
2. Ouvrir `app-debug.apk` sur le téléphone ; autoriser une fois « Installer des applications inconnues » pour le gestionnaire de fichiers.
   (MIUI/HyperOS peut demander une confirmation supplémentaire : c'est normal pour une APK hors Play Store.)
3. Copier sur le téléphone au moins `Qwen3-0.6B-Q4_K_M.gguf` (et, pour la voix, les deux autres fichiers).
4. Ouvrir **JARVIS**, toucher **« Importer des fichiers… »**, sélectionner les fichiers. Chaque ligne passe à ✓ après vérification ;
   les fichiers sont copiés dans le stockage privé de l'application (les originaux peuvent ensuite être supprimés).

## 2 bis. Installation — avec ordinateur (adb)

```sh
sha256sum app-debug.apk                       # doit égaler apk-sha256.txt
adb install app-debug.apk
adb shell mkdir -p /sdcard/Android/data/fr.jarvis.v1/files/import
adb push Qwen3-0.6B-Q4_K_M.gguf ggml-base-q5_1.bin pocket-fr-6l-alba.zip /sdcard/Android/data/fr.jarvis.v1/files/import/
```

Puis dans l'application : **« Rechercher »** (import depuis ce dossier).

## 3. Utilisation

1. **« Démarrer JARVIS »** charge le modèle (chargement scellé A02, vérifié par empreinte), puis la reconnaissance vocale et la voix si elles sont installées. Compter 10 à 30 s.
2. **Écrire** une question dans « Ou écrivez » puis **« Envoyer »** : la réponse est générée localement, affichée, et dite à voix haute si la voix est installée.
3. **« 🎤 Parler »** (si la reconnaissance vocale est installée) : parler en français ; l'écoute s'arrête seule après ~1 s de silence, ou avec « Terminer ».
4. Le **contexte** de la conversation est conservé d'un message à l'autre pendant la session.
5. **« Interrompre »** coupe l'écoute, la réflexion ou la parole. « Parler » pendant que JARVIS parle l'interrompt et relance l'écoute.
6. **« Fermer »** libère tout (modèle, reconnaissance, voix, mémoire). Les fichiers importés restent installés : à la réouverture, il suffit de toucher « Démarrer JARVIS ».

Protections conservées : nouvelle question refusée si l'appareil est trop chaud (état thermique SÉVÈRE) ou si la mémoire système est basse ;
chargement refusé s'il dépasse le budget mémoire disponible ; modèle revérifié à chaque chargement.

## 4. Désinstallation

Paramètres → Applications → JARVIS → Désinstaller (supprime l'application, ses modèles vérifiés et sa session),
ou `adb uninstall fr.jarvis.v1`.

Note : l'APK V1 est signée avec la clé de débogage de la CI, différente à chaque build. Pour installer une version plus récente,
désinstaller d'abord la précédente (les fichiers importés seront à réimporter).

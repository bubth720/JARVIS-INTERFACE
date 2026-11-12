# JARVIS V1 — installation et utilisation

APK `fr.jarvis.v1`, Android 10 (API 29) minimum. C'est une APK **testOnly** : elle s'installe avec `adb install -t`.

Une seule permission : le **microphone**, demandé au premier appui sur « Parler ». **Aucun accès Internet.** Toute la conversation reste sur l'appareil.

## 1. Fichiers nécessaires (non inclus dans l'APK)

| Rôle | Fichier | Taille | SHA-256 |
|---|---|---|---|
| Modèle de langage | `Qwen3-0.6B-Q4_K_M.gguf` (unsloth/Qwen3-0.6B-GGUF @ `50968a44`) | 396 705 472 o | `ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a` |
| Reconnaissance vocale | `ggml-base-q5_1.bin` (ggerganov/whisper.cpp @ `5359861c`) | 59 707 625 o | `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898` |
| Voix (Pocket TTS, voix publique « alba ») | `pocket-fr-6l-alba.zip`, produit par `JARVIS-VOICE/scripts/pocket/prepare_model.py` @ `92d0745` | env. 131 Mo | chaque fichier du ZIP est vérifié contre le verrou épinglé (`model-lock.json`) |

Le détail se trouve dans `resources.json`. L'application **vérifie** chaque fichier avant de l'utiliser et refuse tout fichier différent. Elle ne télécharge rien.

## 2. Installation (ordinateur et câble USB)

```sh
sha256sum app-debug.apk                       # doit égaler le SHA-256 publié avec l'APK
adb install -t app-debug.apk
adb shell mkdir -p /sdcard/Android/data/fr.jarvis.v1/files/import
adb push Qwen3-0.6B-Q4_K_M.gguf ggml-base-q5_1.bin pocket-fr-6l-alba.zip /sdcard/Android/data/fr.jarvis.v1/files/import/
```

Ouvrir **JARVIS V1**, puis toucher **« Rechercher »**. Les trois lignes passent à ✓ : les fichiers ont été vérifiés et copiés dans le stockage privé de l'application, et le dossier d'import peut ensuite être vidé.

Sans ordinateur : copier les trois fichiers sur le téléphone, toucher **« Importer des fichiers… »** et les sélectionner.

## 3. Utilisation

1. **« Démarrer JARVIS »** charge le modèle (chargement scellé A02), la reconnaissance vocale et la voix. Compter environ 10 à 30 s.
2. **« 🎤 Parler »** : parler en français. L'écoute s'arrête seule après environ 1 s de silence, ou en touchant « Terminer ». JARVIS transcrit, réfléchit puis répond **à voix haute**.
3. **Nouvelle question** : toucher à nouveau « Parler ». Le contexte de la conversation est conservé.
4. **« Interrompre »** coupe immédiatement l'écoute, la réflexion ou la parole. Toucher « Parler » pendant que JARVIS parle l'interrompt et relance l'écoute.
5. Le champ « Ou écrivez » permet de poser une question au clavier, qui suit le même parcours avec réponse vocale.
6. **« Fermer »** libère tout : modèle, reconnaissance, voix et mémoire.

Le mode avion est recommandé : rien ne change, car l'application n'a pas d'accès réseau.

Protections conservées :
- une nouvelle question est **refusée** si l'appareil est trop chaud (état thermique SÉVÈRE) ou si la mémoire système est basse ;
- le chargement du modèle est refusé s'il dépasse le budget mémoire disponible ;
- le modèle est vérifié par empreinte à chaque chargement.

## 4. Désinstallation

```sh
adb uninstall fr.jarvis.v1                    # supprime l'app, ses modèles vérifiés et sa session
```

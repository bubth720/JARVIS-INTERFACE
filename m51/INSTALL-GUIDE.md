# Guide Android de laboratoire — installation personnelle NON AUTORISÉE

Livrer depuis l'artefact GitHub vérifié : M51-candidate.apk, package fr.jarvis.v1.lab, version0.51.0-m51-lab/code51, minAPI26/cible35, signaturedebug et SHA256 exacts dans build.json. Ne pas substituer l'APK historique com.jarvis.interfaceapp. Aucun modèle ou pack privé n'est embarqué ; LLM/CORE/VOICE/NEXUS/SERVICES/AVATAR/EVOLUTION restent non raccordés.

Les APK testOnly sont destinées aux émulateurs/cloud de développement. L'installation par l'interface standard Android peut refuser testOnly ; **ne pas demander de Termux ou AndroidStudio à l'utilisateur ni transformer cela en installation personnelle automatique**. Une éventuelle campagne sur POCO/Redmi nécessitera accord distinct, APK et méthode adaptées, certificat/hashes, sauvegarde préalable et procédure réversible. Aucun droit de forcer -r ou d'effacer une application existante n'est accordé par ce guide.

Dans le périmètre cloud actuel, le script vérifie serialemulator-*, qemu/API/ABI, SHA et paquet avant install -t. L'utilisateur n'a aucune commande à exécuter aujourd'hui. L'app n'a aucune permission Android de réseau, micro, caméra, stockage global ou notification ; la permission signature interne AndroidX protège les receivers locaux.

Données : nouveau stockage privé propre au paquet. Projets, finances et documents restent locaux ; export uniquement après choix du fichier. L'export des modules ne contient pas les données métier, prompts, chemins privés ou clés. Les sources SAF choisies ne sont pas effacées en supprimant leur référence. La désinstallation d'un laboratoire après autorisation ne touche pas aux autres paquets, mais perd ses données locales : exporter volontairement avant toute suppression.

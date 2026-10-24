# AVA-03 — essayer le moteur V2

Livraison **PARTIAL** : code d'intégration et PNG techniques existants. Athéna
premium n'est pas fournie (`ART_ASSETS_MISSING`). Aucun appareil personnel n'a été
installé, modifié ou désinstallé par Codex.

## APK

Ouvrir les [Actions de la branche](https://github.com/bubth720/JARVIS-INTERFACE/actions?query=branch%3Afeat%2Fava-03-athena-premium),
choisir le run réussi du commit livré, télécharger `jarvis-interface-ava03-partial-kit`.
Extraire le ZIP avec Fichiers puis ouvrir `app-debug.apk` si vous choisissez de
l'installer sur un appareil de test. Autoriser temporairement ce gestionnaire de
fichiers puis retirer l'autorisation. Ne pas installer l'APK androidTest.

Les empreintes du kit CI correspondent à son propre APK ; la signature et le SHA
peuvent différer du build local archivé dans le rapport.

Un conflit de signature doit être signalé ; ne pas désinstaller ni effacer les
données pour le contourner. La signature debug d'un autre environnement peut
être différente. Aucun test physique n'est revendiqué.

## Prévisualisation et confirmation

1. Ouvrir le panneau Avatar → **Démo 2.5D** → **Athéna V2 · packs et comparaison**.
   Faire défiler les panneaux/fenêtres courtes pour atteindre les boutons.
2. Le panneau conserve initialement son avatar V1. Dans la preview, le pack technique
   V2 est chargé à la demande, explicitement étiqueté DEMONSTRATION.
3. **Comparer V1** / **Voir V2** affiche alternativement les rendus. Le secours
   vectoriel V1 et son animation restent disponibles. Le lien historique
   **Ressources et calques locaux** conserve les sept PNG et leur import V1.
4. Les huit boutons d'état sont accessibles. Avec la démo, seul IDLE/NEUTRAL/FACE
   est fourni ; les autres demandes affichent `ART_ASSET_UNAVAILABLE`, en conservant
   le dessin. Il en va de même des angles et expressions non fournis.
5. Les commandes d'animation existantes se trouvent sous **Démo 2.5D** de la surface
   affichée. Respiration/inclinaison utilisent les calques ; les clignements V2
   nécessitent des paupières ouvertes/fermées, absentes de la démo. Le bouton de
   bouche est désactivé si ses deux ressources manquent ; il ne fait aucune voix.
6. Le bouton **Utiliser le pack prévisualisé** est désactivé par défaut. Cocher
   **J'ai vérifié visuellement ce pack pour cette session**, puis appliquer.
   Cette validation de la démo n'est pas une approbation d'Athéna premium.
7. Réouvrir la preview pour **Revenir au pack précédent** ou **Restaurer le rendu V1**.
   Fermer sans appliquer ne change pas le panneau. Rotation/recréation/fermeture
   de l'hôte remet la sélection V1 ; aucun profil artistique n'est sauvegardé.
8. Tester masquage par défilement, arrière-plan/reprise et redimensionnement. Le
   moteur utilise les mêmes gates de visibilité/lifecycle. Les dispositions
   continuent de se sauvegarder indépendamment via DataStore V1.

## Import volontaire

Le kit contient `ava03-technical-demo.zip`, un vrai ZIP V2 de test à sélectionner
via **Importer volontairement un pack V2** → Fichiers. Le manifeste et les PNG
sont à sa racine. Aucun réseau, accès micro, permission de stockage globale ou
autorisation SAF persistante. L'import reste en mémoire et ne publie rien.

Le pack est validé et décodé hors du fil principal. Une erreur de fichier, checksum,
transparence, état, pivot ou budget conserve le pack courant. Aucun asset importé
ne devient automatiquement le rendu du panneau ; la confirmation est obligatoire.
Un conflit d'ID ou un ensemble de packs dépassant le budget est refusé : fermer
la session pour libérer les packs, puis importer à nouveau. Ne pas augmenter les
limites du manifeste pour contourner un rejet.

Pour un pack complet réel, parcourir les correspondances état/expression/vue,
observer le fondu et le retour neutre. La bouche éventuelle est une **simulation
manuelle non phonétique** ; le port VOICE n'a aucune implémentation. Une image
pleine pose est explicitement marquée « remplacement d'image », sans faux rig.

La planche officielle ne doit pas être sélectionnée comme ZIP ni publiée. Un
portrait aplati ou une découpe de mosaïque n'est pas un pack multicouche valide.

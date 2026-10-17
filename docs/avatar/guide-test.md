# AVATAR-001 — installer et observer le prototype

Aucun résultat physique POCO X8 Pro ou Redmi Pad SE n’est revendiqué. Les validations
physiques de mission 006 restent en attente. Ce prototype n’intègre ni voix ni CORE.

## Installation

Depuis Android, ouvrir le [workflow de cette branche](https://github.com/bubth720/JARVIS-INTERFACE/actions?query=branch%3Afeature%2Finterface-avatar-001),
choisir le run **Success** du commit livré, télécharger **jarvis-interface-avatar-kit**,
extraire le ZIP avec Fichiers puis ouvrir **app-debug.apk**. GitHub peut demander une
connexion et le mode bureau du navigateur. Le kit comprend les instructions et les
empreintes de ses propres fichiers.

Autoriser temporairement l’installation depuis ce gestionnaire de fichiers si Android
le demande, puis retirer cette autorisation. Ne pas installer l’APK `androidTest`
de l’autre artefact. Ne pas désinstaller/effacer les données en cas de conflit de
signature debug : signaler le message pour conserver les dispositions existantes.
Le réseau n’est nécessaire que pour le téléchargement ; tester ensuite en mode avion.

## Animations et commandes

1. Ouvrir le dashboard : les quatre panneaux doivent rester présents. Dans Avatar,
   observer le personnage provisoire (formes indépendantes, cheveux bruns/uniforme
   bleu nuit). La respiration et les clignements variables sont automatiques.
2. Appuyer **Démo 2.5D**. Faire défiler la prévisualisation pour les commandes.
   ANIM_001 = respiration IDLE (cycle 4,2 s) ; ANIM_002 = BLINK (~260 ms) ;
   ANIM_003 = HEAD_TILT (~2,2 s, jusqu’à ±5,5°). Les boutons d’animation ramènent
   la vue vers le personnage pour l’observer.
3. Déclencher BLINK et HEAD_TILT pendant la respiration : les mouvements se combinent.
   Réappuyer sur une animation déjà en cours ne doit pas réinitialiser sa pose.
4. **Arrêter les animations** : retour progressif au neutre (~350 ms), puis image
   statique. Ce bouton désactive aussi les clignements automatiques. BLINK manuel
   reste disponible ; Réinitialiser restaure la respiration et l’automatisme.
5. **Réinitialiser l’avatar** : retour progressif au neutre puis IDLE ; ne change
   aucun panneau ni disposition. Cela est distinct de la restauration des dispositions.
6. Les boutons IDLE/LISTENING/THINKING/SPEAKING changent uniquement le contrat d’état
   local. Il n’y a ni son, ni IA conversationnelle, ni animation de parole simulée.
7. Fermer la prévisualisation. En mode édition, déplacer/redimensionner/verrouiller
   le panneau par les poignées habituelles. Dans un petit panneau ou en édition,
   faire défiler son contenu pour retrouver le dessin et le bouton de prévisualisation.
   Le dessin s’adapte ; le toucher du dessin
   ne doit pas déplacer le panneau. Les autres panneaux et refus restent inchangés.
8. Faire défiler jusqu’à masquer complètement le dessin puis revenir. Le moteur
   suspend son horloge hors écran ; il reprend sans rattraper le temps caché. Si la
   prévisualisation est visible, elle devient l’unique surface animée (panneau figé).
9. Accueil Android, attendre quelques secondes, revenir : pas de saut de phase dû
   au temps passé en arrière-plan. Tester aussi portrait/paysage et recréation.
10. Modifier une disposition, attendre **Disposition enregistrée**, puis Forcer
    l’arrêt via Paramètres Android → Applications → JARVIS INTERFACE. Relancer :
    disposition restaurée, avatar neuf en IDLE. Les animations sont transitoires et
    ne sont pas ajoutées au JSON V1.

Un échec logique de renderer affiche un secours statique et suspend son horloge.
Le scénario est injecté dans les tests automatisés ; aucune erreur n’est provoquée
sur le téléphone pour le tester. Réinitialiser permet une nouvelle tentative.

## Retour utile

Indiquer appareil exact, Android/MIUI/HyperOS, commit/source de l’APK, taille de texte,
orientation et disposition. Pour chaque animation : observée ou non, raccords visibles,
sauts, dessin coupé, gêne tactile. Joindre les étapes de reproduction si une commande
ou une reprise échoue. Les estimations à l’œil ne sont pas des FPS ou mesures CPU.

Les tests instrumentés réinitialisent les dispositions : ne les lancer que sur un
émulateur jetable ou un appareil de test sans données à conserver. Le rapport détaille
les vérifications de compteur de frames et de pixels ; elles ne remplacent pas des
mesures physiques de démarrage, RAM, CPU, thermique ou fluidité.

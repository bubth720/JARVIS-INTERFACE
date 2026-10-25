# Installation et vérification utilisateur

Télécharger `jarvis-interface-apks` dans le run GitHub Actions de la branche
`feat/interface-final-01`, extraire `app-debug.apk` et l'ouvrir sur Android.
Autoriser temporairement cette source d'installation si Android le demande.
Aucune application JARVIS externe ni connexion Internet nécessaire au lancement.
Ne pas désinstaller une version existante pour contourner une signature différente :
cela efface ses données. En cas de conflit, conserver l'application et son fichier
exporté ; utiliser la même clé de signature pour mettre à jour. Aucune installation
personnelle n'est exécutée par Codex.

## Navigation

- Dashboard : Modifier, poignées Déplacer/Taille, verrouillage et Précision pour
  les coordonnées exactes. Une position refusée garde la géométrie précédente.
- Studio → Dispositions : nommer/enregistrer jusqu'à12 copies ; restaurer après
  confirmation ; importer/exporter seulement la géométrie et les verrouillages.
- Studio → Personnalité : régler défauts/contextes, langage et goûts simulés ;
  comparer et confirmer localement. Annuler ou préparer les défauts sans les appliquer.
- Studio → Apparence : thème, taille texte/éléments, réduction d'animations,
  transitions, mode de profil, panneaux visibles (au moins un). Un panneau masqué
  réserve son emplacement pour éviter une collision lors de sa réapparition.
- Conversation : activer volontairement la démo. Envoyer, annuler, sélectionner/copier,
  détails/sources et relancer en simulation. Aucun moteur IA exécuté. Effacer la session
  RAM n'efface pas l'archive. La conservation locale s'active dans le Studio.
- Studio → Historique : consulter et supprimer après confirmation. La suppression
  désactive aussi la conservation ; aucun document ou mémoire CORE n'est effacé.
- Modules : configurer un paquet exact puis vérifier sa version observable. Un paquet
  détecté reste NON CONNECTÉ ; toutes les capacités externes restent indisponibles.

## Grille physique — à remplir séparément

Pour chaque ligne : PASS / ÉCHEC / NON EXÉCUTÉ, avec modèle, Android, police, orientation,
version APK, étapes, résultat attendu/observé et capture si nécessaire.

| Vérification | POCO X8 Pro | Redmi Pad SE |
|---|---|---|
| Installation / lancement sans CORE | Non exécuté | Non exécuté |
| Mode avion / quatre panneaux | Non exécuté | Non exécuté |
| Déplacement, taille, collisions, verrouillage | Non exécuté | Non exécuté |
| Précision et copie/restauration des deux profils | Non exécuté | Non exécuté |
| Portrait / paysage / fenêtre courte | Non exécuté | Non exécuté |
| Texte agrandi150% + réglage Android | Non exécuté | Non exécuté |
| TalkBack : lecture, sliders, actions des poignées | Non exécuté | Non exécuté |
| Réduction d'animations : avatar statique puis reprise | Non exécuté | Non exécuté |
| Profil local confirmé puis arrêt forcé / relance | Non exécuté | Non exécuté |
| Historique opt-in, suppression, arrêt forcé | Non exécuté | Non exécuté |
| SAF export/import, annulation, fichier invalide | Non exécuté | Non exécuté |
| Module absent / installé mais non connecté | Non exécuté | Non exécuté |
| Retour arrière, verrouillage Android, reprise | Non exécuté | Non exécuté |
| Démarrage, mémoire, CPU, fluidité, chauffe, énergie | Non exécuté | Non exécuté |

Arrêt réel du processus sans ordinateur : Paramètres Android → Applications →
JARVIS INTERFACE → Forcer l'arrêt, puis ouvrir depuis son icône. Une rotation ou
un retour arrière n'est pas une mort du processus. Redémarrer ensuite le téléphone
pour contrôler la durabilité après reboot, sans effacer les données.

Pour les mesures physiques : préciser démarrage froid/chaud (3 essais), écran/fréquence,
police et réglages, version Android, mode avion, durée du geste, mémoire PSS, intervalle
CPU, température et outil de mesure. Une capture visuellement fluide ne mesure pas
les images perdues ou l'énergie. Les relevés sur émulateur sont séparés.

## Développement

```sh
./scripts/check.sh --no-daemon
./gradlew connectedDebugAndroidTest --no-daemon
python3 scripts/verify_storage_restart.py --help
```

Java17, Gradle8.13, Kotlin2.1.20, AGP8.9.2, compile/target35, minimum26.
Aucune nouvelle dépendance. Voir ARCHITECTURE.md et le rapport de validation de la
mission pour les tests effectivement exécutés et les limites.

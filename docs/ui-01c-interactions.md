# UI-01C — audit des interactions avant correction

Base auditée : `5aa1a9c`, branche feat/ui-01-native-cockpit. Sources inspectées :
CockpitScreen, WorkspaceScreens, LocalWorkspace et preview/index.html/app.js.
Tous les contrôles rendus sont recensés ci-dessous, y compris les saisies.
Une ligne de contrôle dynamique couvre chaque instance d’enregistrement de ce type.
Les panneaux, textes d’état, graphiques et mobilier ne sont pas cliquables :
ils ne reçoivent pas d’apparence de bouton. JarvisApp et LegacyCockpitScreen
sont des développements conservés mais non accessibles depuis le point d’entrée.

| Élément | Écran | Action attendue | Action auditée Android / HTML | État initial | Correction | Validation prévue |
| --- | --- | --- | --- | --- | --- | --- |
| Logo | Tous | Accueil | Texte sans action / titre sans action | inactif | Bouton accessible | NavigationContractTest / HTML navigation |
| Maison | Tous | Tableau de bord | Accueil / accueil | fonctionnel | Conserver | NavigationContractTest |
| Grille | Tous | Liste des huit modules | Absent / absent | inactif | Écran Modules | NavigationContractTest |
| Dossier | Tous | Documents locaux | Absent / absent | inactif | Index documents et accès import | CollectionTest / HTML collections |
| Calendrier | Tous | Échéances locales | Absent / absent | inactif | Calendrier mensuel et filtres | CollectionTest |
| Mois précédent/suivant/aujourd’hui, jour | Calendrier | Naviguer et filtrer | Absents | inactif | Actions de calendrier | CollectionTest |
| Statistiques | Tous | Progression calculée | Absent / absent | inactif | Tableau calculé | CollectionTest |
| Paramètres | Tous | Ouvrir réglages | Fonctionnel / barre de simulation seulement | incomplet | Page commune réglages | SettingsContractTest |
| Alimentation | Tous | Veille ou fermeture application | Absent / absent | inactif | Menu sans extinction appareil | PowerContractTest |
| Annuler, veille, reprendre, fermer | Alimentation | Actions réelles et réversibles sauf fermeture | Absents | inactif | Dialogue + état veille ; fermeture Android seulement | PowerContractTest |
| Supervision | Navigation | Ouvrir supervision | Page / simple défilement | incomplet | Page dédiée HTML | NavigationContractTest |
| Avatar | Navigation | Emplacement futur | Page réservée / page réservée | fonctionnel | Conserver sans moteur | NavigationContractTest |
| Modules Système, Administratif, Finance, Projets, Écriture, Création, Technique, Sécurité | Accueil/liste | Ouvrir espace dédié, teinter décor | Fonctionnel | fonctionnel | Conserver et tester chaque module | NavigationContractTest / HTML modules |
| Retour affiché et Retour Android/navigateur | Tous | Écran précédent | Toujours accueil / accueil | incomplet | Pile de navigation persistante | NavigationContractTest |
| Profils économie/équilibré/qualité | Paramètres | Modifier animation | Android fonctionnel / qualité identique à équilibré | incomplet | État effectif et persistance HTML | SettingsContractTest |
| Intensité | Paramètres | Effet réel | Android effectif / absent | incomplet | Curseur commun | SettingsContractTest |
| Économie et éclairage | Paramètres | Arrêter animation / éclairage | Android effectif / économie seulement | incomplet | Parité des réglages disponibles | SettingsContractTest |
| Cycle automatique / mode démonstration | Ambiance | Horloge locale ou simulation, cache réel intact | Démo Android / simulation HTML permanente | incomplet | Retour explicite automatique | AmbienceContractTest |
| Heure simulée | Ambiance | Modifier phase | Fonctionnel | fonctionnel | Conserver, tester valeurs limites | AmbienceContractTest |
| Soleil, nuages, pluie, orage, brouillard, neige | Ambiance | Décor simulé identifié | Fonctionnel | fonctionnel | Tester six choix, badge visible | AmbienceContractTest |
| Titre, texte, date, montant | Formulaires | Saisie validée et brouillons | Android persistants / HTML partiels | incomplet | Validation et brouillons communs | CrudContractTest |
| Dépense/revenu | Finance | Signe correct | Fonctionnel | fonctionnel | Tests des deux signes | FinanceContractTest |
| Ajouter | Cinq modules locaux | Enregistrer données | Fonctionnel | fonctionnel | Conserver, erreurs explicites | CrudContractTest |
| Budget et enregistrer budget | Finance | Budget du mois persistant | Fonctionnel | fonctionnel | Tests limites et totaux | FinanceContractTest |
| Modifier, champs d’édition, enregistrer, annuler | Entrées | Modifier sans dupliquer | Notes texte seulement / notes texte seulement | incomplet | Éditeur commun par ID | CrudContractTest |
| Supprimer, confirmer, annuler | Entrées | Retirer entrée choisie | Absents | inactif | Confirmation, fichier original conservé | CrudContractTest |
| Terminer/rouvrir, exclure/réinclure | Entrées | État réel, totaux actualisés | Fonctionnel sauf notes Android | incomplet | Parité, tests de mutation | CrudContractTest |
| Progression | Projets | Mise à jour persistante | Fonctionnel | fonctionnel | Historique et test du geste | ProjectContractTest |
| Nouvelle étape, ajouter, terminer/rouvrir | Projets/Création | Étapes persistantes | Absents | inactif | Table additive d’étapes | ProjectContractTest |
| Historique, fermer historique | Projets/Création | Consulter modifications réelles | Absents | inactif | Historique par entrée | ProjectContractTest |
| Référencer fichier | Administratif/Création/Documents | Import choisi | SAF Android / absent HTML | incomplet | Import navigateur limité explicite, tests frontière Android | FileBoundaryTest |
| Ouvrir fichier | Entrées/Documents | Lecteur compatible ou erreur | Android intent / absent HTML | incomplet | Erreur testée ; navigateur téléchargement choisi | FileBoundaryTest |
| Actualiser diagnostic | Système/Technique | Relever sources locales | Android effectif / absent HTML | incomplet | Diagnostic navigateur distinct | DiagnosticsContractTest |
| Exporter diagnostic | Système/Technique | Export choisi sans contenu personnel | SAF Android / absent HTML | incomplet | Téléchargement navigateur, tests frontière SAF | DiagnosticsContractTest |
| Permissions Android | Sécurité | Paramètres système réels | Android intent / information seule | incomplet | Tester intent ; navigateur indisponible explicite | SecurityContractTest |
| Confidentialité | Sécurité | FLAG_SECURE Android | Android effectif / absent HTML | incomplet | Tester fenêtre ; préférence visuelle navigateur distincte | SecurityContractTest |
| Portrait/paysage forcé | HTML | Simulation de disposition | Fonctionnel | fonctionnel | Conserver + test fichier autonome | HTML offline |

Les noms ci-dessus décrivent la couverture prévue au début de l’audit. La table
suivante les associe aux scénarios réellement implémentés ; leurs résultats et
révisions exécutées sont dans [ui-01c-results.md](ui-01c-results.md). Aucun site
n’est publié par cet audit.

## Contrats après correction

Les méthodes ci-dessous appartiennent à `Ui01cContractTest`, sauf mention
contraire. Le statut « opérationnel » concerne le contrat local décrit, pas
une intégration JARVIS future. Chaque ligne dynamique couvre toutes les
instances produites par le composant partagé ; les cinq types de données sont
exercés séparément. Les tests n’exhaustent pas tous les contenus possibles.

| Éléments / écrans | Action réalisée après correction | Statut livré / correction restante | Test Android | Test HTML (`preview.spec.cjs`) |
| --- | --- | --- | --- | --- |
| Logo, Maison, Grille ; huit modules ; Paramètres en-tête et barre ; Supervision, Avatar | Écrans distincts ; accueil par logo ; emplacement avatar sans moteur | Opérationnel localement ; avatar non connecté explicite | `navigationContractAllIconsAndModulesInBothOrientations` (dont cibles des huit modules ≥48 dp) + `StandaloneStartupTest` | 4 scénarios `navigation, settings, ambience, power and layout` : chaque route et module ; cibles des modules ≥48 px même en paysage forcé |
| Libellés de toute la barre latérale, Alimentation comprise | Texte borné et revenant à la ligne ; barre défilable | Rognage corrigé ; pas de contrôle désactivé masqué | Contrat navigation : `GetTextLayoutResult`, aucun `hasVisualOverflow`, deux orientations | Même scénario aux 4 formats : largeur du texte dans son bouton |
| Retour affiché, Retour Android, Retour navigateur et Alt+Gauche | Dépiler la route précédente, état conservé | Opérationnel | Contrat navigation ; `ProcessRestartProbe` | Scénarios navigation et fichier autonome |
| Dossier, importer depuis Documents, ouvrir document ; raccourci échéance et progression | Écrans Documents/Calendrier/Statistiques et liens vers module cible | Opérationnel ; lecteur externe selon disponibilité | `collectionContractCalendarDocumentsStatisticsAndChart` ; test fichier ci-dessous | Scénario `CRUD all local modules…` + navigation |
| Mois précédent/suivant, aujourd’hui, sélection d’un jour | Filtrer les échéances enregistrées, aucune date financière assimilée à une échéance | Opérationnel ; calendrier adaptatif, cibles mesurées ≥48 | Contrat collection + bornes des jours dans contrat navigation | CRUD collections + mesure des jours aux 4 formats |
| Saisies titre/texte/date/montant, type dépense/revenu, ajouter, budget/enregistrer | Données validées et enregistrées ; calculs financiers sur mouvements réels | Opérationnel | `WorkspaceInteractionTest.fiveLocalFormsSaveAndSurviveActivityRecreation`, contrat collection, `WorkspaceRulesTest` JVM | CRUD : cinq modules, validation montant/budget, types de mouvement |
| Modifier, champs d’édition, enregistrer/annuler ; supprimer/confirmer/annuler ; terminer/rouvrir ou exclure/réinclure | Mutations par ID ; annulations sans écriture ; confirmation avant suppression | Opérationnel pour les cinq types ; fichier original conservé | `crudContractEditsCancellationDeletionAndStatusAcrossFiveModules` | CRUD paramétré sur les cinq types |
| Progression, titre étape, ajouter étape, terminer/rouvrir ; historique et fermer | Progression manuelle, étapes indépendantes, journal réel par projet/travail créatif | Opérationnel | `projectContractStepsProgressAndHistoryAreReal` | CRUD : Projets et Création, progression et contenu historique |
| Référencer fichier Administratif/Création, ouvrir fichier | URI SAF native ; copie de démonstration et téléchargement navigateur | Opérationnel selon fournisseur/lecteur ; pas de suppression du fichier source | `DocumentPickerContractTest.actualAndroidDocumentPickerImportsLocalFileWithoutDeletingOriginal` (vrai DocumentsUI) ; contrat frontières pour annulation/lecteur | CRUD : formats refusés et TXT réel choisi/téléchargé dans les deux modules |
| Actualiser et exporter diagnostic Système/Technique | Diagnostic local de la plateforme concernée ; export réel et expurgé | Opérationnel ; pas de RAM Android inventée dans HTML | `externalBoundaryContractsExportImportCancellationViewerAndPermissions` : contenu réellement écrit | Navigation aux 4 formats : actualiser et télécharger, contenu vérifié |
| Permissions, protection de confidentialité Sécurité | Ouvrir paramètres Android ; appliquer FLAG_SECURE | Opérationnel Android ; capacités natives indisponibles explicitement dans HTML, masque visuel seulement | Contrat frontières : intent et drapeau réel de fenêtre, activé/désactivé | Navigation : texte d’indisponibilité et classe de masque visuel |
| Profils économie/équilibré/qualité, économie, intensité, éclairage | Réglages effectifs et persistants | Opérationnel ; fluidité/énergie physique à qualifier | `settingsContractAllWeatherProfilesSlidersAndRealCacheIsolation`, `CockpitPersistenceTest` | Navigation : trois profils, intensité, éclairage, classes effectives |
| Cycle automatique/démo, retour automatique, heure, six météos | Horloge locale ou simulation identifiée ; cache réel intact | Opérationnel ; aucun fournisseur météo réel activé | Contrat réglages ; `AmbienceRotationTest`, `CockpitPersistenceTest.lastKnownObservationRetainsItsSourceAndTimestamp` | Navigation : six météos et rendu neige/nuit, retour neutre |
| Couleurs de module et transition réversible | Éclairage et reflet changent, mobilier inchangé, météo indépendante | Opérationnel ; performance physique non qualifiée | Contrat réglages : pixels réellement dessinés Finance/Création/Sécurité/Projets | Navigation : huit accents exacts, sélection préservant météo |
| Alimentation, annuler, veille, reprendre, fermer | Veille réversible, arrêt des animations ; fermer seulement l’activité Android | Opérationnel Android ; fermeture native indisponible explicitement dans HTML | `powerContractCancelSleepResumeAndCloseOnlyThisActivity` : cycle de vie vérifié | Navigation : menu, annuler, veille/reprise, route conservée |
| Sélecteur disposition et bouton portrait/paysage HTML ; rotation Android | Réorganisation, conservation route/module/préférences | Opérationnel sur profils testés ; autres tailles/accessibilité à qualifier | Contrat navigation et `AmbienceRotationTest.simulationAndOrientationPreserveActivityAndRealWeather` | Navigation aux 4 tailles : nombre réel de colonnes, forçage des 2 dispositions |
| Persistance de toutes ces opérations | SQLite v2 additive + préférences ; stockage de démonstration navigateur séparé | Opérationnel dans environnements testés ; avertissement session si navigateur refuse stockage | `WorkspaceDeviceTest` (v1→v2 et réouverture), recréation, `ProcessRestartProbe` avant/après arrêt forcé réel | Reload dans CRUD et fichier HTML hors ligne après fermeture/réouverture |

Les cartes et graphiques informatifs ne sont pas des boutons. Les contrôles
générés utilisent les mêmes fonctions de mutation et des identifiants de test
stables. Les actions non disponibles sont des messages explicites, sans bouton
simulant une réussite. Les anciennes vues archivées ne sont pas rendues par
l’application et ne constituent pas des écrans fonctionnels revendiqués.

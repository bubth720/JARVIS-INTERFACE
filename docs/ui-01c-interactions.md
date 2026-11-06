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

Les noms ci-dessus décrivent la couverture prévue, pas des tests déjà réussis.
Les résultats, limites des fournisseurs Android externes et écarts de parité
seront consignés dans ui-01c-results.md. Aucun site n’est publié par cet audit.

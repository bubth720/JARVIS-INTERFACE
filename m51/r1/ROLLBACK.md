# Retour arrière M51-R1

Référence conservée : PR18/9c8960a7c9d3f572dff41fe18a5b002c9f66ac8a. La branche R1 reste isolée. Aucun fichier applicatif UI-01C, ancien test, schéma SQLite, permission ou contrat partagé n'est réécrit. Ne pas fusionner sans accord.

Omettre `-Pm51R1=true` conserve le testsource historique. Fermer la PR R1 abandonne le listener/staging et les campagnes R1. Les APK debug testOnly ont des certificats éphémères par build ; elles ne remplacent pas une application personnelle et aucun `install -r` n'est autorisé.

Les données de tous les émulateurs sont des fixtures publiques synthétiques. La qualification peut supprimer uniquement les deux paquetsbaseline qu'elle vient de créer sur l'émulateur neuf, après succès des trois cas isolés, pour retrouver la condition de fraîcheur du banc original. Aucun nettoyage après échec n'est utilisé pour effacer une preuve ou refaire passer un test. Les tests du garde de nettoyage refusent tout paquet historique, candidat ou non créé dans cette invocation.

Après crash critique, arrêter la campagne API, conserver les logs/empreintes/screenshots/causesUNKNOWN et ne pas relancer automatiquement. Une suppression de cache ou un rollback de code ne prouve aucune libération native : aucun moteur LLM n'est chargé dans ce laboratoire.

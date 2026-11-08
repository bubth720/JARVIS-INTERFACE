# Contrat candidat, ressources et risques

Le contrat ancien AvatarSession reste identique à SHA256 `fd7a8791574f63f6039372c422a739d1f2411397e28781b2fcf8543cfc211045`. AvatarSceneV1 est un nouveau type qui l'étend ; aucun consommateur historique n'est modifié. Le controller ne prend que des états et métadonnées de profil sans path, texte de chat ou credential. Il ne détient aucune autorité CORE, accès aux données, bail MODEL/HEAVY ou capacité réseau.

| Risque | Protection de revue | Qualification restante |
|---|---|---|
| Geste après Stop/HIDDEN/ERROR | File annulée, priorité, epoch invalidée et délai idle renouvelé | Comportement Android/renderer NOT_RUN |
| Audio tardif ou bouche sur texte | PCM positif requis, epoch/sequence, aucun port token | VOICE réel et première parole audible NOT_RUN |
| Animation permanente / coût | Tick caller-driven, aucune thread, cadence suggérée ECO/réduction | CPU/GPU/batterie réels NOT_RUN, cadence suggérée non mesurée |
| Nettoyage échoué | État CLEANUP_FAILED, instance terminale, pas de retry automatique | Aucun vrai lecteur/texture à qualifier aujourd'hui |
| Dérive de master / visèmes fictifs | Master absent explicite, visèmes UNSUPPORTED | Validation humaine et chronologie visémique nécessaires |
| Références privées publiées | Aucun fichier média ajouté au Git/artefact ; CLI sortie privée hors code | Stockage/autorisation d'un vrai lot restent à approuver |
| Import/path/volumes malveillants | Roots privés, pas de symlinks/hardlinks/traversal, SHA et budgets, compte cumulatif même sur refus | Décodage média complet non exécuté ; aucune admission production |
| Licence présentée comme preuve | Droits seulement déclarés, master/admission toujours fermés | Revue de droits et décision artistique humaine |

La détection de doublons est exacte par SHA, pas une identification de personnes ou un regroupement biométrique. Aucune extraction de visage, reconnaissance d'identité, génération de design ou fusion de coiffure n'est réalisée. Les formats autres que PNG/MP4 restent refusés par ce premier inventory ; les originaux sont conservés sans transformation.

Aucune co-résidence VOICE–LLM n'est autorisée. Les budgets mémoire/thermiques existants ne sont ni lus comme des valeurs inventées ni augmentés. La plus grande taille écran tablette ne justifie pas un budget de rendu supérieur. Un futur adaptateur doit suspendre/libérer réellement son renderer hors écran et expurger ses exceptions avant export.

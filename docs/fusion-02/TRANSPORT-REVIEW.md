# FUSION-02 — revue de contrat de présentation, sans activation

Sources : CORE `36761cea101b5f88b87cd4136dc075d28a1adb96`,
`docs/fusion-01/CONVERSATION-DESIGN.md` lu intégralement, issue INTERFACE #4 et
mission INTERFACE #6 / UNIFIED #5. Aucun contrat externe v1 n'est amendé.

## Réemploi

Le client `ui/chat/core/CoreConversationClientAdapter` reste séparé de Compose.
Submit / Ack / Chunk / Stop / Error passent par le port UI-03 existant. UUID
session/request, génération, séquence, tampon pré-ACK, remise en ordre bornée,
déduplication et état UNKNOWN bloquant sont conservés. L'origine disponible en
production reste NONE ; LABORATORY ne peut jamais se présenter comme CORE.
Les métadonnées seules ne créent aucune réponse d'assistant ni citation.

Défaut reproduit : `transport.stop` suspendu n'avait aucune échéance, y compris
lorsque l'annulation précédait l'ACK. Deux tests échouent sur l'ancien code
(`evidence/stop-before-fix.xml`). Les deux chemins appellent maintenant un arrêt
borné à cinq secondes. Timeout/refus -> UNKNOWN, aucun nouveau submit, aucune
preuve de déchargement JNI. Une fin logique déjà reçue ne devient pas UNKNOWN à
cause d'un retour tardif du port stop. Les porteurs de transport doivent respecter
l'annulation coopérative Kotlin ; aucun appel natif bloquant n'est ajouté.

`ContentClientBudget.Fusion02Review` permet de tester les bornes proposées CORE :
question 1024 octets UTF-8, chunk 4096, agrégat 256 KiB / 256 chunks ; aucune
troncature. Les valeurs UI-03 historiques 16 KiB sont conservées par défaut pour
son banc v1, pas négociées silencieusement. Il n'existe pas encore de codec wire,
serveur ou négociation approuvée pour l'un ou l'autre. La nouvelle politique est
un paramètre local de consommateur, testé ; pas une déclaration de compatibilité.

## Présentation des capacités /2 proposée

`HostCapabilityReport` est une projection locale : epoch UUID canonique, génération,
séquence, observation monotone, statut par capacité, code technique borné, version2.
`CapabilityProjection` exige un `HostEvidenceVerifier` qui authentifie **le rapport
exact**, epoch/génération courants, séquence supérieure au watermark et fraîcheur
<=30 secondes. Origine LAB, doublons, version inconnue, rapport périmé, erreur du
vérificateur ou absence de preuve -> NON_CONNECTÉ / UNKNOWN. Un statut READY ne
s'étend pas aux capacités absentes. Le composant Compose réutilisable ne vérifie
jamais un paquet/certificat à partir d'une chaîne déclarative.

**Aucun vérificateur Android de confiance n'est fourni ni activé.** Les tests
positifs injectent un vérificateur de test ; ils ne démontrent aucune signature
Android. La composition actuelle affiche les sept capacités NON_CONNECTÉ/UNKNOWN.
L'adaptateur futur possédera epoch et watermark ; l'évaluateur reste pur et ne doit
pas être utilisé comme autorité ou journal anti-rejeu à lui seul.

## Conditions avant un vrai transport

- Accord distinct sur identité Android : UID observé, paquet/variante et empreinte
  de certificat épinglés, grant CORE révocable ; jamais un flag `trusted` reçu.
- Version/hash commun approuvés, epoch fourni par CORE après authentification,
  compteur/génération et journal propriétaire ; pas de conversion implicite v1.
- Contrôle du grant au commit chez CORE/SERVICES ; la confirmation UI et une phrase
  de chat ne sont jamais une permission d'exécution. ACK distinct de COMMITTED.
- Citations préservées : URI sous grant, source/version/page/offset/hash réellement
  relus par le producteur ; affichage sans ouvrir automatiquement une URI privée.
- Tests mauvaise signature/UID, rejeu après mort du processus, révocation, délais,
  duplication, ACK perdu, fermeture, récupération sans retry et fin native inconnue.
- Gates NEXUS FTS5 et SERVICES SAF/fence réellement instrumentés, puis preuve CORE.

Binder, AIDL, service exporté, accès réseau, permissions, partage de mémoire ou
identité ne sont ni ajoutés ni activés. Le pilote multimodule Android n'est pas GO.

Précisions de revue : les offsets du laboratoire sont des indices UTF-16 de la
chaîne Kotlin, le hash porte sur ses octets UTF-8. Une future citation wire doit
porter explicitement son unité, jamais convertir silencieusement offsets/pages.
La fraîcheur de capacité est évaluée dans le domaine monotone local de l'adaptateur
vérifié : `observedAtMs` doit être estampillé à la réception de preuve authentifiée,
jamais comparé aveuglément à une horloge étrangère. Ces exigences ne qualifient pas
un transport absent.

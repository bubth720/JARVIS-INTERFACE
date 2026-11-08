# Composition M51 et frontières

Seul INTERFACE s'exécute : sources UI-01C PR16 épinglées, Kotlin2.1.20/Gradle8.13/AGP8.9.2, ComposeBOM2025.04.01, JDK17, min26/target35. L'hôte a son applicationId/testOnly/manifeste, sans configChanges : Android peut réellement recréer l'Activity. Les ressources et sources métier viennent du commit0dfeef8. Aucun nouveau store métier concurrent ; LocalWorkspace schéma2 existant réutilisé dans l'espace privé du nouveau paquet. Migration1→2 testée seulement sur une base synthétique dédiée.

Le manifeste multi-dépôts et les inconnues de licence/outillage sont dans UNIFIED PR21. Référence de coordination préparée : `3420a4e0a099bad7e79f2ac5fb1218cb522d6994`, tree `7f4dbde3633d29776ee8c9cdfa453fc46307a1c4` ; les références métier du registre restent les SHAs de reprise auditée. Une référence de source ne signifie ni code inclus ni intégration active dans l'APK.

CORE5da9838, LLM69400fe (auditM51 distinct08fbda5), NEXUSbe70faa, SERVICES8d80d09, VOICEfe57452, AVATARcontrat0dfeef8, EVOLUTION84ebc2e : aucune dépendance Gradle/runtime introduite vers ces modules. UNIFIED coordonne mais ne devient pas un deuxième runtime. Les écarts Gradle/Kotlin/SDK des modules ne sont pas masqués par une fusion de builds.

Contrat `jarvis.capabilities/1` nouveau, local à ce laboratoire : source/version, disponibilité, six dimensions, autorité/profil, dépendances, erreur fixe expurgée, preuve et tested_commit. Aucune API d'activation n'existe ; access() refuse tout sauf UI locale effectivement montée. Le registre ne forge ni coreMandateSha256 ni bail ni preuve de résidence. Les liens GitHub sont volontaires et strictement bornés à un SHA/source connus.

L'audit LLM M51 NO_GO révèle des risques non résolus : séquence de notifications de révocation, moniteur partagé/deadline, snapshot coopératif/FD et logs. Ni application du patch8df826…, ni provisionnement de racine, ni acquisition du GGUF n'est autorisé ici. Les futures M52–M54 se poursuivent seulement en code/test de préparation indépendant ou documentation ; aucune route productive implicite.

Les assets artistiques privés et le profil vocalB ne sont ni lus, copiés ni embarqués. Les captures présentes dans la branche source sont les captures publiques UI-01C ; les nouvelles captures sont produites sur données synthétiques d'émulateur. Aucun master/avatar animé ou visème qualifié n'est déclaré.

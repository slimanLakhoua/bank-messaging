# Périmètre et exigences

## Objectif

Recevoir les messages déposés par des applications Back Office dans IBM MQ, conserver leur contenu et leurs métadonnées dans une base relationnelle, puis permettre leur consultation depuis une API REST et une interface web.

## Fonctionnalités

| Fonction | Comportement attendu |
|---|---|
| Réception | Consommer une file IBM MQ avec une session JMS transactionnelle |
| Stockage | Enregistrer durablement texte, identité de transport, corrélation, taille et date |
| Déduplication | Une seule ligne pour un couple file source / JMSMessageID, y compris en concurrence |
| Résilience | Reprendre après panne SQL/MQ ou arrêt du processus, sans assimiler une panne globale à un message invalide |
| Isolement des erreurs | Transférer les erreurs permanentes en backout après un nombre borné de livraisons |
| Consultation | Liste paginée, filtres exacts, période UTC et tri stable ; détail avec payload |
| Interface web | Recherche, pagination, détail et états chargement/vide/erreur |
| Documentation interactive | Explorer et exécuter les deux opérations de consultation dans Swagger UI |
| Déploiement local | Docker Compose avec volumes et contrôles de santé |

## Contraintes techniques

Java 21 ou supérieur, Spring Boot 4 ou supérieur, Angular 22 et projet Maven pour le backend. La configuration livrée cible Java 21 et les versions exactes de [l’environnement](ENVIRONNEMENT.md). PostgreSQL est la base retenue. IBM MQ est le broker utilisé pour l’application et les validations d’intégration.

## Hors périmètre

Authentification et autorisations applicatives ; modification ou suppression HTTP des messages ; routage sortant ; interprétation, transformation ou validation métier bancaire ; recherche dans le payload ; export massif ; rejeu automatique du backout ; rétention automatique ; haute disponibilité et coordination XA.

Le routage vers des destinations tierces appartient au contexte métier, mais aucun moteur de routage n’est implémenté. Le traitement est volontairement limité à la conservation et à la consultation.

## Hypothèses

- Entrée acceptée : `TextMessage` JMS possédant un JMSMessageID non vide. Un corps vide est accepté ; un corps null est refusé.
- Identité : `(source_queue, jms_message_id)` au sein d’un gestionnaire MQ logique. Deux messages de même texte mais d’identité différente restent distincts.
- Les producteurs doivent utiliser la persistance MQ pour résister au redémarrage du gestionnaire.
- Aucun ordre global n’est garanti avec plusieurs consommateurs.
- Aucune cible contractuelle de volume, rétention, débit ou latence n’est définie. Les résultats sont des observations de test.
- Les services sont configurés pour un accès local ; une exposition publique nécessite une conception d’exploitation et de sécurité adaptée.

## Critères d’acceptation

Un message synthétique déposé dans MQ devient consultable en SQL, REST puis Angular. Une panne SQL ne l’envoie pas en backout. Un crash après commit SQL et avant commit MQ entraîne une redélivraison sans nouvelle ligne. Une erreur permanente est isolée et un échec d’envoi en backout conserve l’original. Les listes sont bornées et excluent le payload ; les erreurs HTTP ne révèlent pas les détails SQL. Les procédures de démarrage et de test sont reproductibles à partir des sources.

Les scénarios et leur état de validation figurent dans [TESTS.md](TESTS.md).

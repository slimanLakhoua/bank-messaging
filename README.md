# BANK Messaging

Application de réception et de consultation de messages : IBM MQ fournit les messages, le backend les enregistre dans PostgreSQL et expose une API REST, puis Angular permet de rechercher les métadonnées et d’afficher le contenu intégral.

Le traitement associe réception JMS transactionnelle et stockage SQL idempotent. Les erreurs de format sont isolées en file de backout ; les pannes de dépendances provoquent une reprise sans abandon du message valide.

## Documentation

La [documentation complète](docs/README.md) décrit le fonctionnement, l’architecture, les décisions techniques, les données, l’installation, l’API, les tests et l’exploitation.

| Besoin | Document |
|---|---|
| Installer et démarrer | [Environnement et configuration](docs/ENVIRONNEMENT.md) |
| Comprendre la structure et les dépendances | [Architecture](docs/ARCHITECTURE.md) |
| Comprendre les arbitrages | [Décisions techniques](docs/DECISIONS.md) |
| Comprendre les garanties de réception | [Ingestion et résilience](docs/INGESTION.md) |
| Intégrer un client HTTP | [API REST et Swagger](docs/API.md) |
| Utiliser l’application | [Guide utilisateur](docs/IHM.md) |
| Administrer et diagnostiquer | [Exploitation](docs/EXPLOITATION.md) |
| Tester ou livrer | [Tests](docs/TESTS.md) · [Livraison](docs/LIVRAISON.md) |

## Stack

Java 21, Spring Boot 4.1.2, Maven 3.9.9, Angular 22.2.4, PostgreSQL 17.6 et IBM MQ 10.0.1.0. Swagger UI est intégré avec Springdoc 3.1.1. Les versions et images sont précisées dans [l’environnement](docs/ENVIRONNEMENT.md).

## Démarrage en développement

Prérequis : JDK 21, Node 24.21.0 avec npm 11, Docker et Compose v2.

1. Si `.env` n’existe pas, copier `.env.example` vers `.env`, puis remplacer les trois mots de passe d’exemple. Conserver un fichier déjà configuré.
2. Démarrer MQ et PostgreSQL depuis la racine :

   ```sh
   docker compose up -d --wait --wait-timeout 240
   ```

3. Dans un terminal positionné dans `backend/` :

   ```sh
   ./mvnw spring-boot:run
   ```

4. Dans un autre terminal positionné dans `frontend/` :

   ```sh
   npm ci
   npm start
   ```

| Service | URL par défaut |
|---|---|
| Application Angular | [Messages](http://127.0.0.1:4200/messages) |
| Swagger UI | [Documentation interactive](http://127.0.0.1:8080/swagger-ui.html) |
| OpenAPI JSON | [Contrat](http://127.0.0.1:8080/openapi.json) |
| Santé backend | [Health](http://127.0.0.1:8080/actuator/health) |

Pour exécuter l’application complète dans Docker, construire d’abord le JAR et le frontend puis suivre le [guide de livraison](docs/LIVRAISON.md). Le point d’entrée web est alors [localhost:8088](http://127.0.0.1:8088/messages).

## Démonstration

Avec les services démarrés, depuis la racine :

```sh
printf 'paiement-synthetique\n\n' | docker compose exec -T mq /opt/mqm/samp/bin/amqsput DEV.BANK.IN QM1
curl --fail 'http://127.0.0.1:8080/api/messages?page=0&size=20'
```

Actualiser la liste Angular et ouvrir le détail du message. Les exemples supposent les noms de files et ports par défaut.

## Vérifications

Depuis `backend/`, avec Java 21 :

```sh
./mvnw -B -ntp verify
./mvnw -B -ntp -Pintegration clean verify
```

La première commande exécute les tests sans infrastructure. La seconde nécessite les vrais services IBM MQ/PostgreSQL et l’accès au moteur Docker ; ses tests de panne arrêtent temporairement ces services. Réserver l’environnement à cette suite.

Depuis `frontend/` :

```sh
npm ci
npm test -- --watch=false
npm run build
npm run e2e -- consultation.spec.ts
```

Les préconditions navigateur, le parcours réel et les preuves datées sont décrits dans [TESTS.md](docs/TESTS.md).

## Limites structurantes

Consultation seule, sans authentification ni autorisations applicatives. Un seul gestionnaire MQ logique est pris en charge par l’identité de transport actuelle. La livraison est au moins une fois et le stockage est idempotent ; aucun commit distribué MQ/SQL ni ordre global avec plusieurs consommateurs n’est garanti. Les mesures de débit locales ne constituent pas un SLA. La rétention, les sauvegardes et la haute disponibilité ne sont pas automatisées.

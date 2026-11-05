# Installation et configuration

## Versions de référence

| Composant | Version |
|---|---|
| Java cible | 21, compilation `release=21` |
| Maven / Wrapper | 3.9.9 / 3.3.4 |
| Spring Boot / Spring JMS | 4.1.2 / 7.0.10 |
| Starter IBM MQ / client Jakarta MQ | 4.1.2 / 10.0.1.0 |
| Jakarta JMS | 3.1.0 |
| Flyway / pilote PostgreSQL | 12.4.0 / 42.7.14, gérés par le BOM Boot |
| Springdoc / Swagger UI / Swagger Core Jakarta | 3.1.1 / 5.32.14 / 2.2.55 |
| PostgreSQL Docker | `postgres:17.6-alpine3.22` |
| IBM MQ Docker | `icr.io/ibm-messaging/mq:10.0.1.0-r1`, `linux/amd64` |
| Image backend | `eclipse-temurin:21.0.13_9-jre-noble` |
| Image frontend | `nginx:1.28.3-alpine` |
| Angular / CLI et builder | 22.2.4 / 22.2.5 |
| TypeScript / RxJS | 6.0.3 / 7.8.2 |
| Node / npm de référence | 24.21.0 / 11.19.0 |
| Vitest / Playwright | 5.1.0 / 1.64.1 |
| JUnit Jupiter | 6.0.3, géré par Boot |

Le parent Maven gère les dépendances Spring et les pilotes. Le starter IBM MQ et Springdoc sont explicitement versionnés. Les dépendances directes npm sont fixées et les transitives sont verrouillées par `package-lock.json`. Aucun tag Docker `latest` n’est utilisé.

Le projet nécessite un JDK complet pour compiler et exécuter le test architectural. Le frontend déclare Node `>=24.15.0 <25` et npm `>=11 <12` ; `.nvmrc` sélectionne 24.21.0. Python 3 est utilisé uniquement pour les scripts de charge et d’archive.

## Préparer la configuration locale

Les commandes suivantes sont exécutées depuis la racine. Si `.env` n’existe pas encore :

```sh
cp .env.example .env
chmod 600 .env
```

Remplacer `POSTGRES_PASSWORD`, `MQ_APP_PASSWORD` et `MQ_ADMIN_PASSWORD` par des valeurs locales. Ne pas écraser un `.env` existant. Les secrets réels ne doivent pas être versionnés. Le backend recherche le fichier dans son répertoire courant ou son parent, ce qui permet le lancement depuis la racine ou `backend/`. Les variables du processus ont priorité.

Avec macOS, sélectionner Java 21 dans le terminal :

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

Avec Windows, utiliser `mvnw.cmd` et sélectionner le JDK 21 dans l’environnement. Avec un gestionnaire Node compatible `.nvmrc`, sélectionner la version du projet avant `npm ci`.

## Démarrer les dépendances

Docker et Compose v2 doivent fonctionner. Depuis la racine :

```sh
docker compose config --quiet
docker compose up -d --wait --wait-timeout 240
docker compose ps
```

Attendre PostgreSQL et MQ `healthy`. Le premier téléchargement MQ peut être volumineux. L’image MQ est une édition de développement ; les détails de licence et de plateforme figurent dans [infra/README.md](../infra/README.md). L’exécution amd64 sur Apple Silicon peut nécessiter une émulation et ne fait pas partie des validations conservées.

## Démarrer le backend

Dans un terminal positionné dans `backend/` :

```sh
./mvnw -B -ntp verify
./mvnw spring-boot:run
```

`verify` sans profil construit le JAR et exécute les tests sans infrastructure. Les migrations sont appliquées lors du démarrage réel sur PostgreSQL. Pour la validation d’intégration complète, consulter [TESTS.md](TESTS.md).

Depuis un autre terminal :

```sh
curl --fail http://127.0.0.1:8080/actuator/health
```

Le backend écoute sur `127.0.0.1:8080`. Le JAR peut également démarrer depuis la racine :

```sh
java -jar backend/target/messaging-0.0.1-SNAPSHOT.jar
```

Arrêter une instance lancée depuis `target/` avant de remplacer son JAR, ou la lancer depuis une copie stable hors de ce répertoire. Le nettoyage Maven supprime aussi les rapports temporaires de ce dossier.

## Démarrer le frontend

Dans un terminal positionné dans `frontend/` :

```sh
npm ci
npm start
```

Ouvrir [la liste des messages](http://127.0.0.1:4200/messages). Le proxy de développement ne relaie que `/api` vers `http://127.0.0.1:8080`. Si le port backend change, adapter `frontend/proxy.conf.json`. Swagger s’ouvre directement sur le backend.

Le mode entièrement conteneurisé est décrit dans [LIVRAISON.md](LIVRAISON.md).

## Ports et accès

| Service | Accès local par défaut | Port conteneur | Remarque |
|---|---|---|---|
| Angular en développement | `127.0.0.1:4200` | — | Serveur CLI |
| Backend en développement | `127.0.0.1:8080` | 8080 en mode applicatif | Non publié par `compose.app.yaml` |
| Nginx applicatif | `127.0.0.1:8088` | 80 | Angular, API, Swagger et santé globale |
| PostgreSQL | `127.0.0.1:55432` | 5432 | Outils et tests locaux |
| IBM MQ | `127.0.0.1:1414` | 1414 | Canal `DEV.APP.SVRCONN`, gestionnaire `QM1` |
| Console IBM MQ | `https://127.0.0.1:9443/ibmmq/console/` | 9443 | Compte `admin`, certificat local autosigné |
| Playwright développement | `127.0.0.1:4300` | — | Serveur Angular démarré par le test |

## Variables de base de données et serveur

| Variable | Défaut | Utilisation |
|---|---|---|
| `POSTGRES_DB` | `bank` | Nom de la base |
| `POSTGRES_USER` | `bank` | Compte SQL |
| `POSTGRES_PASSWORD` | obligatoire | Secret SQL |
| `POSTGRES_HOST` | `localhost` | Hôte vu par Java ; `postgres` dans Compose applicatif |
| `POSTGRES_PORT` | `55432` | Port hôte ; Java utilise 5432 dans Compose applicatif |
| `SQL_POOL_SIZE` | `5` | Maximum du pool Hikari partagé par ingestion et REST |
| `SQL_CONNECTION_TIMEOUT_MS` | `5000` | Attente maximale d’une connexion du pool |
| `SERVER_PORT` | `8080` | Port Java ; fixé à 8080 dans Compose applicatif |
| `SERVER_ADDRESS` | `127.0.0.1` | Adresse Java ; fixée à `0.0.0.0` dans le conteneur |
| `WEB_PORT` | `8088` | Port publié par Nginx |

L’URL JDBC ajoute `connectTimeout=5` et `socketTimeout=15`, en secondes. Ces délais sont distincts de l’attente Hikari et du délai de transaction de 10 secondes.

## Variables MQ et ingestion

| Variable | Défaut | Utilisation |
|---|---|---|
| `MQ_LICENSE` | obligatoire dans Compose | Acceptation de la licence de l’image de développement |
| `MQ_APP_PASSWORD` | obligatoire | Secret du compte MQ `app` |
| `MQ_ADMIN_PASSWORD` | obligatoire pour Compose | Secret de la console MQ |
| `MQ_HOST` | `localhost` | Hôte vu par Java ; `mq` dans Compose applicatif |
| `MQ_PORT` | `1414` | Port hôte et défaut de connexion locale ; 1414 dans le conteneur |
| `MQ_WEB_PORT` | `9443` | Port hôte de la console MQ |
| `MQ_INPUT_QUEUE` | `DEV.BANK.IN` | File consommée |
| `MQ_BACKOUT_QUEUE` | `DEV.BANK.BACKOUT` | File d’isolement des erreurs permanentes |
| `INGESTION_ENABLED` | `true` | Démarrage automatique du listener |
| `INGESTION_CONCURRENCY` | `1` | Minimum de consommateurs |
| `INGESTION_MAX_CONCURRENCY` | minimum configuré | Maximum de consommateurs |
| `INGESTION_RETRY_DELAY_MS` | `1000` | Pause après rollback et intervalle de reconnexion JMS |
| `INGESTION_MAX_ATTEMPTS` | `3` | Seuil de livraison pour erreur permanente |
| `INGESTION_MAX_PAYLOAD_BYTES` | `1048576` | Taille UTF-8 maximale acceptée |

Les valeurs numériques d’ingestion doivent être positives, le maximum JMS au moins égal au minimum, et strictement inférieur à `SQL_POOL_SIZE`. Les files source/backout doivent être renseignées et différentes. La capacité supplémentaire du pool reste partagée ; ce n’est pas une réservation exclusive pour REST. Les files personnalisées doivent être créées et autorisées dans MQ avant consommation.

## Configuration technique complémentaire

| Paramètre | Valeur livrée | Effet |
|---|---|---|
| Gestionnaire / canal / utilisateur MQ | `QM1` / `DEV.APP.SVRCONN` / `app` | Connexion JMS locale |
| `spring.jpa.open-in-view` | `false` | Pas de session JPA ouverte pendant le rendu HTTP |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Vérification du schéma créé par Flyway |
| `springdoc.enable-default-api-docs` | `false` | Pas de document inféré concurrent |
| `springdoc.swagger-ui.url` | `/openapi.json` | Contrat maintenu utilisé par Swagger |
| Validateur externe Swagger | `none` | Pas d’envoi du contrat au validateur distant |
| Arrêt serveur | `graceful`, 30 s par phase | Fin des traitements en cours |
| Délai d’arrêt Compose backend | 45 s | Marge avant terminaison forcée |
| Actuator exposé | `health`, `info`, `metrics` | Santé et diagnostic locaux |

## Arrêt et données persistantes

Depuis la racine :

```sh
docker compose down
```

Les volumes `postgres-data` et `mq-data` sont conservés. `down -v` les supprimerait. Un changement de mot de passe dans `.env` ne migre pas un compte PostgreSQL déjà créé sur un volume existant. Les procédures de diagnostic et de sauvegarde sont dans [EXPLOITATION.md](EXPLOITATION.md).

Le projet Compose s’appelle `bank-messaging`. Une installation antérieure portant un autre nom conserve ses propres volumes ; ils ne sont ni renommés ni migrés automatiquement. Adapter les variables d’un `.env` existant aux noms de base et de files souhaités avant de démarrer cette version. Cette livraison ne migre pas les données d’une installation antérieure.

# Construction et livraison

## Artefacts

| Élément | Sortie |
|---|---|
| Backend | `backend/target/messaging-0.0.1-SNAPSHOT.jar` |
| Frontend | `frontend/dist/bank-messaging/browser/` |
| Image Java | `bank-messaging-backend:0.0.1` |
| Image web | `bank-messaging-frontend:0.0.1` |
| Archive locale | `release/bank-messaging-0.0.1-local.zip` |
| Empreinte d’archive | `release/SHA256SUMS` |

Les Dockerfiles copient des artefacts déjà compilés. Ils ne construisent ni Maven ni Angular. Le backend utilise un JRE Temurin 21 et tourne sous UID/GID `10001:10001`. Le frontend est servi par Nginx. Les images de base et dépendances doivent être disponibles localement ou téléchargeables ; un premier build entièrement hors ligne n’est pas garanti.

## Construire depuis les sources

Prérequis : `.env` configuré, JDK 21, Node 24.21.0/npm 11, Docker et Compose v2. Depuis la racine :

```sh
docker compose up -d --wait --wait-timeout 240
cd backend
./mvnw -B -ntp -Pintegration clean verify
cd ../frontend
npm ci
npm test -- --watch=false
npm run format:check
npm run build
cd ..
```

Les tests d’intégration doivent utiliser une infrastructure réservée : ils arrêtent/redémarrent MQ et SQL. Arrêter tout processus lancé depuis un JAR de `target/` avant de le remplacer. Les fichiers `.env` et les volumes existants ne doivent pas être effacés pour reconstruire.

## Lancer l’application complète dans Docker

Après compilation des deux artefacts, depuis la racine :

```sh
docker compose -f compose.yaml -f compose.app.yaml config --quiet
docker compose -f compose.yaml -f compose.app.yaml up -d --build --wait --wait-timeout 300
docker compose -f compose.yaml -f compose.app.yaml ps
```

| Service | Accès |
|---|---|
| Angular | [Messages](http://127.0.0.1:8088/messages) |
| Swagger UI | [Swagger](http://127.0.0.1:8088/swagger-ui.html) |
| Contrat | [OpenAPI JSON](http://127.0.0.1:8088/openapi.json) |
| Santé globale | [Health](http://127.0.0.1:8088/actuator/health) |

Nginx relaie `/api/`, `/openapi.json`, `/swagger-ui.html`, `/swagger-ui/`, `/v3/api-docs/swagger-config` et la santé globale. Il renvoie `index.html` pour les routes Angular profondes. Le port backend reste interne au réseau Compose ; SQL/MQ conservent leurs ports techniques loopback. `WEB_PORT` modifie le port web publié.

Éviter de laisser le backend de développement consommer la même source pendant une démonstration censée isoler le déploiement Docker. Utiliser des environnements à files, ports et volumes distincts si plusieurs instances doivent coexister.

## Recette minimale

Depuis la racine, valeurs par défaut :

```sh
curl --fail http://127.0.0.1:8088/actuator/health
curl --fail http://127.0.0.1:8088/openapi.json
printf 'paiement-demo-livraison\n\n' | docker compose exec -T mq /opt/mqm/samp/bin/amqsput DEV.BANK.IN QM1
curl --fail 'http://127.0.0.1:8088/api/messages?page=0&size=20'
```

Ouvrir Angular, actualiser et consulter le message. Exécuter ensuite le parcours de production décrit dans [TESTS.md](TESTS.md). Une profondeur d’entrée MQ nulle est attendue après stockage ; vérifier le contenu via REST plutôt que conclure à une perte.

## Créer une archive locale

Avec Python 3 et les artefacts présents, depuis la racine :

```sh
python3 scripts/package-release.py
```

Le script produit une archive de sources, configurations, guides applicatifs et artefacts, accompagnée d’un `MANIFEST.json` contenant les SHA-256 des fichiers. `release/SHA256SUMS` contient celle de l’archive. Les documents distribués sont sélectionnés explicitement. Les secrets `.env`, caches, `node_modules`, journaux et leurs rotations, volumes, configurations d’éditeur et fichiers de préparation locale ne sont pas incorporés. Les images de dépendances ne sont pas redistribuées dans l’archive.

Une archive existante ne se met pas à jour automatiquement : la régénérer après modification des sources, de la documentation ou des artefacts. Le script vérifie la présence des artefacts, pas leur fraîcheur ni la réussite préalable de tous les tests. La recette de livraison reste donc une étape explicite.

Pour vérifier l’empreinte depuis `release/` :

```sh
shasum -a 256 -c SHA256SUMS
```

Sur les systèmes fournissant `sha256sum`, utiliser `sha256sum -c SHA256SUMS`.

## Intégration continue

`.github/workflows/ci.yml` prévoit deux jobs sur Ubuntu 24.04 :

| Job | Étapes |
|---|---|
| Backend | JDK 21, Compose jetable, Maven avec profil integration, démarrage du JAR, parcours réel MQ vers navigateur, logs en échec et nettoyage des volumes du runner |
| Frontend | Node selon `.nvmrc`, `npm ci`, tests, build, installation Chromium et tests de consultation |

Les permissions du workflow sont en lecture du contenu. Il ne publie ni image ni release. Sa présence ne constitue pas une preuve d’exécution distante ; les validations locales et datées figurent dans [TESTS.md](TESTS.md).

## Mise à jour et exploitation

Construire et vérifier les nouveaux artefacts avant reconstruction des images. Les migrations Flyway s’exécutent au démarrage du backend ; sauvegarder et vérifier la compatibilité du schéma avant une évolution persistante. Aucune procédure de rollback SQL automatique n’est livrée.

Pour arrêter sans supprimer les données :

```sh
docker compose -f compose.yaml -f compose.app.yaml down
```

Les sauvegardes, incidents, limites de disponibilité et métriques sont décrits dans [EXPLOITATION.md](EXPLOITATION.md). L’édition MQ et la configuration livrées sont destinées au développement local ; un déploiement de production demande un environnement adapté.

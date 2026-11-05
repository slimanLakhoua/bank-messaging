# Tests et validation

## Stratégie

Les tests sans infrastructure contrôlent les règles applicatives et la composition. Les tests d’intégration utilisent PostgreSQL et IBM MQ réels pour les garanties de transaction, de concurrence et de redélivraison. Les tests frontend couvrent les états et la navigation ; un parcours bout en bout relie le vrai broker au navigateur.

| Niveau | Outils | Ce qu’il démontre |
|---|---|---|
| Backend sans infrastructure | JUnit Jupiter, AssertJ, doubles ciblés, MockMvc pour Swagger | Logique, options transactionnelles, indépendance du noyau et ressources HTTP de documentation |
| Intégration Java | Failsafe, Spring Boot, Compose SQL/MQ | Comportements durables, appels HTTP, pannes et crash |
| Frontend | Vitest, Angular TestBed et tests HTTP | États, critères, annulation, timeout et rendu sûr |
| Navigateur | Playwright | Parcours UI, clavier, erreurs et modes de couleurs |
| Bout en bout réel | Playwright et vraie ingestion JMS | Message synthétique retrouvé via SQL/REST puis Angular |
| Charge | Profil Maven `benchmark` | Mesures contextualisées et réconciliation des comptes |

## Archive de livraison

Depuis la racine, avec Python 3 :

```sh
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

Ce test construit une archive sur des fichiers synthétiques, contrôle l’exclusion des fichiers locaux et des journaux, la présence des composants nécessaires et les empreintes du manifeste et de l’archive. Il ne nécessite ni Docker ni artefacts réels.

## Backend sans infrastructure

Depuis `backend/`, avec un JDK 21 complet :

```sh
./mvnw -B -ntp clean verify
```

Surefire exécute les classes `*Test`. Les sources des classes `*IT` et du benchmark sont compilées, mais ces tests ne sont pas exécutés sans profil. Le JAR est produit après réussite.

| Test | Couverture |
|---|---|
| `MessageQueryServiceTest` | Bornes avant accès au port, calcul de pagination, détail et absence |
| `IngestionPropertiesTest` | Configuration d’ingestion valide et incohérente |
| `IngestionConfigurationTest` | Conteneur JMS et budget de connexions |
| `TransactionalMessageStorageTest` | Retour après commit, rollback et propagation, doublon, échec de commit, `REQUIRES_NEW` et délai |
| `UseCaseConfigurationTest` | Ports décorés exposés, services nus non enregistrés, transaction de lecture et ordre count/page/commit |
| `ApplicationBoundaryTest` | Compilation des packages applicatifs avec le seul JDK 21 |
| `SwaggerDocumentationTest` | Redirection, HTML/JS/CSS locaux, configuration Swagger et contrat existant |

Un double de gestionnaire transactionnel ne prouve pas un commit SQL réel. La compilation du noyau détecte ses dépendances directes, pas un accès réflexif construit à partir d’une chaîne.

## Backend avec IBM MQ et PostgreSQL

Depuis la racine, puis `backend/` :

```sh
docker compose up -d --wait --wait-timeout 240
cd backend
./mvnw -B -ntp -Pintegration clean verify
```

Failsafe exécute les classes `*IT`. Docker doit être accessible au processus Maven. Les tests de panne arrêtent temporairement les services du projet ; ne pas les lancer en parallèle ni sur une infrastructure partagée avec une application à conserver disponible.

| Scénario | Assertion principale |
|---|---|
| Texte Unicode, corrélation et taille | Message stocké avec ses métadonnées puis retiré de MQ |
| Doublons concurrents | Une seule insertion pour la même identité |
| Même corps, identités différentes | Deux messages distincts |
| Rollback de la transaction appelante | Stockage indépendant déjà durable |
| Erreur SQL et panne SQL répétée | Pas d’acquittement nominal ni de backout du message valide ; reprise après retour SQL |
| Arrêt puis redémarrage MQ | Reconnexion et stockage des messages suivants |
| Crash après commit SQL | JVM enfant terminée, redélivraison puis unicité en SQL |
| Type non accepté, texte NUL, taille excessive | Tentatives bornées et backout |
| MQSTR UTF-8 malformé | Erreur permanente isolée avec contenu d’origine conservé |
| Erreur transitoire d’extraction | Pas de quarantaine sur le seul nombre de livraisons |
| BOQ refusée | Original non supprimé, transfert possible après rétablissement |
| Poison puis message valide | Pas de blocage permanent du message suivant après isolement |
| API | Pagination, filtres/tri, payload réservé au détail, erreurs 400/404/503 |
| Santé | Dépendance arrêtée : readiness indisponible, liveness locale préservée |
| Confidentialité | Pas de contenu synthétique sensible dans les réponses d’erreur ni dans le log applicatif testé |

Les tests MQ utilisent des files dédiées par exécution et nettoient leurs lignes associées. Les tests API créent une base temporaire propre à la suite ; le compte SQL doit pouvoir créer une base. Les services du projet restent néanmoins partagés lors des scénarios d’arrêt. Le hook de crash n’existe que dans les sources de test.

Les rapports XML et texte sont générés dans `backend/target/surefire-reports/` et `backend/target/failsafe-reports/`. Les diagnostics et mesures temporaires sont dans `target/` ; les conserver avant un `clean` si nécessaire.

## Frontend et navigateur

Depuis `frontend/` :

```sh
npm ci
npm test -- --watch=false
npm run format:check
npm run build
npx playwright install chromium
npm run e2e -- consultation.spec.ts
```

Les tests navigateur de consultation contrôlent les réponses API et démarrent Angular sur le port 4300. `PW_CHANNEL=chrome` permet d’utiliser un Chrome installé au lieu du Chromium téléchargé.

Pour le parcours réel, démarrer le backend et les dépendances puis lancer :

```sh
RUN_LIVE=1 npm run e2e -- live.spec.ts
```

Pour tester l’application complète déjà démarrée derrière Nginx :

```sh
APP_URL=http://127.0.0.1:8088 RUN_LIVE=1 npm run e2e -- --config playwright.production.config.ts
```

Le parcours live injecte un message synthétique, attend sa présence REST puis compare son contenu dans le navigateur. Les tests contrôlés ne prouvent pas l’ingestion. Les scénarios de timeout attendent réellement le délai de 15 secondes. Les traces d’échec sont conservées dans `frontend/test-results/`.

## Charges et pannes ciblées

Le profil `benchmark` est volontairement séparé d’`integration` ; ses paramètres et sa méthode sont documentés dans [PERFORMANCE.md](PERFORMANCE.md). Les tests de panne ciblés peuvent être lancés depuis `backend/` :

```sh
./mvnw -B -ntp -Pintegration -Dit.test=IngestionIT#databaseOutageDoesNotQuarantineValidMessagesAndRecovers verify
./mvnw -B -ntp -Pintegration -Dit.test=IngestionIT#processCrashAfterSqlCommitRedeliversWithoutDuplicateStorage verify
./mvnw -B -ntp -Pintegration -Dit.test=IngestionIT#poisonDoesNotBlockFollowingValidMessageAndDurationsAreMeasured verify
```

## État des preuves conservées

| Date | Contrôle | Résultat et portée |
|---|---|---|
| 5 novembre 2026 | Build Java 21 avec Swagger et noyau indépendant | 19 tests sans infrastructure réussis, aucun échec/erreur/ignoré ; JAR construit |
| 5 novembre 2026 | Redéploiement Docker local | Images backend/frontend reconstruites, conteneurs applicatifs recréés, quatre services sains ; volumes MQ/SQL conservés |
| 5 novembre 2026 | Frontend et navigateur après redéploiement | 59 tests frontend et build réussis ; 13 parcours derrière Nginx réussis avec Chrome, dont MQ → PostgreSQL → REST → Angular |
| 5 novembre 2026 | HTTP derrière Nginx | Réponses 200 et contenu vérifié pour Angular, santé globale `UP`, liste REST, contrat OpenAPI, page Swagger, bundle JS et configuration Swagger |
| 4 novembre 2026 | Campagne d’intégration avant extraction des décorateurs | Bilan historique : 49 intégrations réelles réussies ; rapports d’exécution temporaires non distribués |
| 4 novembre 2026 | Frontend | Bilan historique : 59 tests, build et formatage réussis ; rapports d’exécution temporaires non distribués |
| 4 novembre 2026 | Navigateur | 12 scénarios développement et 13 production réussis, dont le parcours réel en production |
| 4 novembre 2026 | Charges | Six jeux mesurés conservés ; paramètres et limites dans [PERFORMANCE.md](PERFORMANCE.md) |

Les 49 intégrations historiques ne constituent pas à elles seules une validation des décorateurs transactionnels et de Swagger ajoutés ensuite. La suite réelle a été rejouée sur la livraison BANK comme indiqué ci-dessous. Le passage Swagger derrière Nginx a été validé en HTTP lors du redéploiement du 5 novembre. Les mesures de charge antérieures ne sont pas un résultat de charge du code courant. Les rapports temporaires peuvent disparaître ; les mesures de `docs/performance/` sont les traces historiques distribuées avec le projet.

### Validation de la livraison BANK du 5 novembre 2026

Une copie des seuls fichiers sélectionnés pour le commit a été extraite dans un répertoire temporaire, sans fichiers locaux de préparation ni artefacts préexistants. Un `.env` de test a été créé depuis `.env.example` avec les ports PostgreSQL 55433, MQ 1415, console MQ 9444 et web 8089. Les dépendances ont été démarrées sous le projet Compose `bank-messaging` avec des volumes neufs ; les caches Maven et npm existants ont été réutilisés.

| Contrôle | Résultat |
|---|---|
| `./mvnw -o -B -ntp -Pintegration clean verify`, avec JDK 21 | 19 tests sans infrastructure et 49 intégrations réelles réussis ; aucun échec, erreur ou test ignoré ; JAR construit |
| Installation npm hors ligne depuis le cache local | Réussie à partir du lockfile de la copie propre |
| `npm test -- --watch=false` | 59 tests réussis |
| `npm run format:check` et `npm run build` | Réussis |
| `PW_CHANNEL=chrome npm run e2e -- consultation.spec.ts` | 12 parcours navigateur réussis |
| Construction des images puis démarrage avec `compose.yaml` et `compose.app.yaml` | Images BANK construites ; quatre services sains |
| `PW_CHANNEL=chrome APP_URL=http://127.0.0.1:8089 RUN_LIVE=1 npm run e2e -- --config playwright.production.config.ts` | 13 parcours réussis, dont MQ → PostgreSQL → REST → Angular |
| Contrôles HTTP derrière Nginx | Sept routes en HTTP 200 : Angular, santé `UP`, liste REST, contrat OpenAPI, page Swagger, bundle JS et configuration Swagger ; identité BANK contrôlée |
| Test d’archive Python | Réussi ; exclusion des fichiers locaux et vérification des empreintes sur données synthétiques |
| Contrôle du contenu sélectionné et des liens Markdown locaux | Aucune ancienne marque, aucun fichier interne distribué et aucun lien local manquant |

Le build Angular a échoué deux fois dans le bac à sable avec un arrêt natif Node/esbuild (code 134). La même commande a réussi hors du bac à sable, puis dans la copie propre. Aucun changement de dépendance ni de configuration de compilation n’a été nécessaire. Cette validation ne comprend pas de nouvelle mesure de charge.

### Commandes du redéploiement du 5 novembre 2026

Avec Java 21 sélectionné, `./mvnw -B -ntp verify` a été exécuté depuis `backend/`. Depuis `frontend/` : `npm ci --cache /private/tmp/bank-npm-cache --no-audit --no-fund`, `npm test -- --watch=false` et `npm run build`. Le premier téléchargement npm était bloqué par le réseau du bac à sable ; la relance avec accès réseau a réussi.

Depuis la racine :

```sh
docker compose -f compose.yaml -f compose.app.yaml config --quiet
docker compose -f compose.yaml -f compose.app.yaml build backend
docker compose -f compose.yaml -f compose.app.yaml build frontend
docker compose -f compose.yaml -f compose.app.yaml up -d --no-deps --force-recreate --wait --wait-timeout 300 backend frontend
docker compose -f compose.yaml -f compose.app.yaml ps
```

MQ et PostgreSQL étaient déjà sains ; seuls les conteneurs applicatifs ont été remplacés. Un script Python `urllib.request` a vérifié les statuts HTTP et le contenu des sept routes suivantes : `/messages`, `/actuator/health`, `/api/messages?page=0&size=1`, `/openapi.json`, `/swagger-ui.html`, `/swagger-ui/swagger-ui-bundle.js` et `/v3/api-docs/swagger-config`.

Depuis `frontend/`, la première exécution Playwright a échoué avant les scénarios car Chromium était absent. La relance documentée avec Chrome installé a réussi :

```sh
PW_CHANNEL=chrome APP_URL=http://127.0.0.1:8088 RUN_LIVE=1 npm run e2e -- --config playwright.production.config.ts
```

Le scénario réel a ajouté un message synthétique. Aucune campagne d’intégration avec arrêt des dépendances, aucun benchmark et aucun contrôle de formatage n’ont été exécutés lors de ce redéploiement.

Une mise à jour documentaire demande une vérification des sources, liens, commandes et exemples ; elle ne nécessite pas de réexécuter les scénarios de panne sans changement de code. La couverture n’inclut pas panne disque, partitions réseau prolongées, haute disponibilité, certification d’accessibilité ou validation exhaustive multi-navigateurs.

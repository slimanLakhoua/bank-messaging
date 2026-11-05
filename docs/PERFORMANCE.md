# Performance et résilience mesurées

## Portée et environnement

Les mesures conservées datent du 4 novembre 2026 et utilisent IBM MQ/PostgreSQL réels avec des données synthétiques. Elles précèdent l’extraction des décorateurs transactionnels et l’ajout de Swagger ; aucune nouvelle campagne ne qualifie ces changements. Ces observations locales ne constituent ni un SLA, ni une capacité maximale de production.

Les noms de projet, conteneurs et autres identifiants des rapports historiques ont été neutralisés pour la livraison. Les dates et valeurs mesurées sont conservées ; cette neutralisation ne constitue pas une nouvelle exécution.

Machine : MacBookPro15,1 Intel, 8 cœurs physiques/16 logiques, 16 Gio de RAM. Docker Desktop dispose de 16 CPU et environ 7,65 Gio de RAM. Java 21, Spring Boot 4.1.2, IBM MQ 10.0.1.0 et PostgreSQL 17.6. La machine est partagée avec l’activité de développement ; aucune isolation matérielle ni répétition statistique n’est établie.

Sources : [environnement initial](performance/environment.json), [environnement ingestion/REST](performance/revue-environment.json).

## Protocole

Un producteur envoie des messages persistants avec un commit JMS par lot de 100. Chaque cas comporte 500 messages d’échauffement exclus des résultats. Le pool SQL est fixé à 5. Les scénarios utilisent des files et lignes dédiées, nettoyées après vérification. Le conteneur JMS est arrêté avant réconciliation finale.

| Mesure | Définition |
|---|---|
| Débit | Nombre de messages stockés / durée entre premier envoi et détection du dernier commit SQL, alimentation MQ incluse |
| Ingestion p95/p99 | Du temps monotone précédant l’envoi au retour du stockage après commit SQL ; attente et lot inclus, commit JMS final exclu |
| HTTP p95/p99 | Temps des réponses des lecteurs concurrents, connexion/attente/sérialisation incluses |
| `maxOutstandingSampled` | Soumis moins commits SQL observés ; estimation des messages en attente/en cours, pas une série de profondeur MQ exacte |
| Heap échantillonné | Maximum de mémoire heap utilisée, échantillonnée ; ni RSS ni mémoire réservée |
| CPU JVM | Temps CPU processus / temps écoulé, exprimé en équivalent de cœur |

Les lecteurs HTTP de la seconde campagne demandent des pages de 50 métadonnées filtrées sur la file du run, attendent 50 ms entre requêtes et utilisent un timeout de 15 secondes. La concurrence HTTP est de deux. Les latences de rafales incluent le temps d’attente dans le backlog ; elles ne représentent pas le coût unitaire de l’insertion.

## Campagne sans lecteurs REST

| Messages | Octets/message | Consommateurs | Durée s | msg/s | Ingestion p95 ms | Ingestion p99 ms |
|---:|---:|---:|---:|---:|---:|---:|
| 10000 | 1024 | 1 | 70.88 | 141.1 | 63445.1 | 65680.0 |
| 10000 | 1024 | 4 | 30.10 | 332.2 | 24064.1 | 24871.0 |
| 1000 | 65536 | 1 | 10.53 | 95.0 | 9071.3 | 9315.6 |
| 1000 | 65536 | 4 | 5.24 | 190.9 | 3909.2 | 4004.0 |

22 000 messages mesurés, chacun stocké une fois ; zéro erreur/doublon et profondeurs finales entrée/backout nulles dans les quatre cas. Les 2 000 échauffements sont exclus. Les instantanés Docker des rapports bruts incluent démarrage et échauffement et peuvent manquer des pics courts.

Sources : [10 000 × 1 Ko, c1](performance/benchmark-10000-1024-c1.json), [10 000 × 1 Ko, c4](performance/benchmark-10000-1024-c4.json), [1 000 × 64 Ko, c1](performance/benchmark-1000-65536-c1.json), [1 000 × 64 Ko, c4](performance/benchmark-1000-65536-c4.json).

## Campagne avec deux lecteurs REST

| Consommateurs | Durée s | msg/s | Ingestion p95 ms | Ingestion p99 ms | Requêtes HTTP | HTTP p95 ms | HTTP p99 ms |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 107.32 | 93.2 | 95665.2 | 99212.9 | 2843 | 42.98 | 94.21 |
| 4 | 38.81 | 257.7 | 30700.6 | 32202.5 | 965 | 41.23 | 62.05 |

Pour chaque cas : 10 000 messages de 1 024 octets envoyés et stockés, zéro erreur ingestion/HTTP, zéro doublon, profondeurs finales entrée/backout nulles. Les 500 échauffements par cas sont exclus. Cette campagne ne collecte pas de nouveaux échantillons `docker stats`.

Sources : [concurrence 1](performance/revue-10000-1024-c1.json), [concurrence 4](performance/revue-10000-1024-c4.json).

Le contexte matériel et le trafic diffèrent de la première campagne : aucun gain ou surcoût attribuable à une seule modification ne peut être isolé. Le résultat établi est la réconciliation des messages et les réponses HTTP observées sous ces charges.

## Reprises après panne

| Scénario | Durée observée | Contrôle |
|---|---:|---|
| Redémarrage MQ vers stockage suivant | 11,553 s | Une ligne pour l’identité suivie, BOQ vide |
| Redémarrage SQL vers stockage en attente | 6,051 s | Une ligne pour l’identité suivie, BOQ vide |
| Crash constaté vers reconnaissance du doublon | 0,172 s | Une ligne finale, BOQ vide |

Source : [resilience.json](performance/resilience.json). Les chronomètres SQL/MQ incluent démarrage Compose, attente healthcheck et polling. Le chronomètre de crash commence après terminaison constatée d’une JVM enfant et utilise un listener parent déjà initialisé ; il n’inclut pas un démarrage à froid complet. Les tests emploient une pause de reprise de 200 ms et une attente Hikari de 500 ms, différentes des défauts applicatifs. Ces valeurs ne sont pas des RTO.

## Exécuter une charge

Avec JDK 21, Docker accessible et dépendances saines, depuis `backend/` :

```sh
./mvnw -B -ntp -Pbenchmark -Dbench.messages=10000 -Dbench.bytes=1024 -Dbench.concurrency=1 -Dbench.httpConcurrency=2 verify
./mvnw -B -ntp -Pbenchmark -Dbench.messages=10000 -Dbench.bytes=1024 -Dbench.concurrency=4 -Dbench.httpConcurrency=2 verify
```

| Propriété JVM | Défaut | Borne |
|---|---:|---|
| `bench.messages` | 10000 | 1 à 50000 |
| `bench.bytes` | 1024 | 1 à 1048576 |
| `bench.concurrency` | 1 | 1 à 4 |
| `bench.warmup` | 500 | 0 à 5000 |
| `bench.httpConcurrency` | 2 | 0 à 8 |

Définir `bench.httpConcurrency=0` pour une variante sans lecteurs. Vérifier que `INGESTION_MAX_CONCURRENCY` n’impose pas un maximum différent de la concurrence fixe voulue et que le pool respecte les contraintes. Le délai de traitement du benchmark est limité à cinq minutes.

Depuis la racine, `python3 scripts/run-benchmarks.py` enchaîne quatre combinaisons taille/concurrence et conserve les mesures sous `docs/performance/`. Le script courant utilise le défaut de deux lecteurs HTTP et les noms de conteneurs du projet `bank-messaging`. Il peut remplacer les rapports portant les mêmes noms : préserver une copie des résultats historiques avant de relancer. Les rapports directs Maven restent dans `backend/target/` et sont supprimés par `clean`.

Ne pas exécuter la charge en parallèle avec les tests de panne. Ne pas remplacer le JAR d’une instance active lancée depuis `target/`. Le profil charge est distinct de la suite d’intégration et n’est pas déclenché par la CI courante.

## Interprétation et réglages

Sur les passages conservés, quatre consommateurs résorbent les rafales plus vite qu’un seul, avec davantage de CPU. Cela justifie une option à mesurer, sans modifier le défaut d’un consommateur. Le pool de cinq connexions reste partagé ; aucune connexion n’est réservée exclusivement à REST.

Une seule répétition par cas, une machine partagée et une volumétrie conservée limitée ne permettent pas d’extrapoler une capacité de production. Les charges de 50 000 messages ne sont pas exécutées dans ces données ; 100 000 dépasse la borne actuelle. Le coût du comptage sur des millions de lignes, les offsets profonds, les partitions réseau prolongées, la saturation disque et les crashs répétés sous forte charge ne sont pas qualifiés.

Avant d’introduire batching, pool plus grand, pagination par curseur, index supplémentaires ou partitionnement, définir le besoin puis mesurer sur l’historique et les tailles réels en préservant les garanties de stockage.

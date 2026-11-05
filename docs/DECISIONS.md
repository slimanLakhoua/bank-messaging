# Décisions techniques et compromis

## Organisation et frameworks

| Choix | Raisons | Compromis / évolution possible |
|---|---|---|
| Monolithe modulaire par fonctionnalité | Une seule unité Java pour un périmètre limité ; ingestion et consultation identifiables | Même processus et base partagés. Une séparation de services exige un besoin d’isolation ou de livraison indépendant |
| Ports et adaptateurs | Cas d’utilisation Java purs, adaptateurs remplaçables, tests ciblés | L’isolation est contrôlée par structure et tests, pas par plusieurs modules JVM |
| Décorateurs transactionnels | Frontière technique explicite sans Spring dans le noyau ; même port d’entrée | La composition doit toujours exposer le décorateur ; un service construit manuellement n’ouvre pas de transaction |
| Modèles simples sous forme de records | Données brutes et métadonnées, sans règles bancaires inventées | Un véritable besoin métier pourra justifier objets de valeur ou domaine enrichi |
| Java 21 et Boot 4 | Cible explicite, JDK stable du projet, intégration JMS/JPA/Actuator | Les mises à jour nécessitent de revalider le starter IBM MQ et les API Jakarta |
| Angular standalone | Pages différées, dépendances locales et templates stricts | Pas de SSR ni de magasin d’état partagé ; à ajouter uniquement pour un besoin identifié |

## Réception et transactions

| Choix | Raisons | Compromis / alternative |
|---|---|---|
| Livraison au moins une fois, stockage idempotent | SQL committe avant MQ ; la redélivraison couvre la fenêtre entre commits | Plusieurs livraisons restent possibles. XA imposerait une coordination et une récupération distribuées supplémentaires |
| `REQUIRES_NEW` pour SQL | Commit de stockage indépendant terminé avant retour au listener | Un appel dans une transaction SQL existante peut mobiliser une connexion supplémentaire |
| Identité file/JMSMessageID | Identité de transport disponible sans identifiant métier garanti | Scope limité à un gestionnaire logique. Plusieurs origines exigent un identifiant d’origine supplémentaire |
| Unicité SQL et `ON CONFLICT` ciblé | Déduplication atomique en concurrence, sans transaction rollback-only à réutiliser | Écriture spécifique à PostgreSQL ; changer de base nécessite un adaptateur équivalent |
| Traitement synchrone et concurrence bornée | Un message suivi jusqu’au stockage, retard conservé dans MQ, absence de file mémoire non bornée | Un commit SQL par message. Le batching nécessite une nouvelle preuve de corrélation entre écritures durables et acquittement MQ |
| Backout classifié par l’application | Distinguer message invalide et panne globale ; préserver les messages valides en panne SQL | Politique JMS explicite et droits BOQ à administrer ; pas de rejeu automatique |
| `BOTHRESH(0)` | Désactiver la mise en quarantaine automatique basée sur le seul nombre de rollbacks | Les mécanismes de reprise applicatifs et l’observabilité deviennent essentiels |

## Données et consultation

| Choix | Raisons | Compromis / alternative |
|---|---|---|
| PostgreSQL | Transactions, contrainte unique et insertion atomique native | Un nœud local sans haute disponibilité ni sauvegarde automatisée |
| Flyway et validation Hibernate | Schéma versionné et vérifié, sans génération implicite en exploitation | Migrations nouvelles et compatibilité applicative à gérer lors d’une livraison |
| Projections de métadonnées | Éviter de charger/sérialiser les payloads dans les listes | Un appel supplémentaire est nécessaire pour le détail |
| Criteria JPA dynamique | Combiner seulement les filtres actifs, sans SQL client interpolé | Plans à mesurer pour les volumes et combinaisons réels |
| Pagination offset avec total exact | Contrat simple pour l’IHM, nombre de pages disponible | Coût du `COUNT` et des offsets profonds ; curseur ou comptage différé si usage volumineux mesuré |
| `REPEATABLE_READ` pour une page | Cohérence du total et des lignes au sein du même appel | N’empêche pas le décalage entre deux requêtes de pages distinctes |
| Index ciblés | Identité, recherche par ID MQ et tri date/ID répondent aux accès présents | Chaque index accroît le coût d’écriture et l’espace. Pas d’index payload/source-date spéculatif |

## Interface et exploitation

| Choix | Raisons | Compromis / alternative |
|---|---|---|
| URL comme état de recherche | Partage, rechargement, retour du détail et historique navigateur | Les critères doivent rester normalisés et compatibles avec le contrat REST |
| Signaux, OnPush et RxJS | Rendu déclaratif, annulation par `switchMap`, timeout et états explicites | Un store global ne serait utile qu’avec un état partagé plus complexe |
| Recherche sur soumission | Pas de requête à chaque frappe, comportement prévisible | Actualisation manuelle ; pas de recherche instantanée |
| OpenAPI maintenu avec Swagger UI | Contrat sémantique précis malgré les conversions HTTP en chaînes ; une seule source | Contrat à synchroniser avec le code et les tests lors des évolutions |
| Probes distinctes | Readiness dépend de SQL/MQ ; liveness reflète l’état local | Docker utilise la santé globale ; `unhealthy` seul ne déclenche pas de redémarrage automatique |
| Métriques sans identité de message | Cardinalité bornée et absence de contenu métier | Compteurs non durables ; une réconciliation SQL/MQ reste nécessaire |
| Compose pour les tests réels | IBM MQ/PostgreSQL versionnés, pannes et crash reproductibles | Tests de panne séquentiels sur une infrastructure réservée. Isolation automatisée supplémentaire si plusieurs suites doivent coexister |
| Images avec artefacts précompilés | Runtime simple, JRE seul et Nginx pour Angular | Le JAR et le build web doivent être reconstruits avant les images ; les Dockerfiles ne compilent pas les sources |

## Évolutions conditionnées à un besoin

Définir les objectifs de débit, latence, rétention et disponibilité avant de modifier les pools, ajouter du batching ou partitionner. Prévoir un scope d’identité avant plusieurs gestionnaires MQ, un contrat de curseur avant de remplacer l’offset, et une politique de rejeu avant d’automatiser la BOQ. L’exposition hors démonstration locale nécessite de traiter l’accès, TLS, les secrets, les sauvegardes et les alertes au niveau approprié.

Ces évolutions ne sont pas des fonctionnalités actuellement implémentées. Les mesures disponibles et leurs limites figurent dans [PERFORMANCE.md](PERFORMANCE.md).

# Modèle de données

## Schéma et propriété des données

Les messages sont conservés dans `messaging.received_message`. Flyway crée et fait évoluer le schéma ; Hibernate contrôle sa correspondance avec l’entité JPA au démarrage. L’application ne propose aucune mise à jour ou suppression HTTP.

| Colonne | Type PostgreSQL | Null autorisé | Origine et signification |
|---|---|---|---|
| `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` | non | Clé primaire interne, utilisée par l’API de détail |
| `source_queue` | `TEXT` | non | Nom de la file consommée |
| `jms_message_id` | `TEXT` | non | Identité de transport reçue par JMS |
| `correlation_id` | `TEXT` | oui | Corrélation JMS facultative |
| `received_at` | `TIMESTAMPTZ` | non | Valeur SQL `CURRENT_TIMESTAMP` lors de la transaction d’insertion |
| `content_type` | `TEXT` | non | Valeur d’écriture `text/plain;charset=UTF-8` |
| `payload_size` | `INTEGER` | non | Taille du texte réencodé en UTF-8, avec contrainte `>= 0` |
| `payload` | `TEXT` | non | Texte extrait du `TextMessage`, sans interprétation bancaire |

`received_at` est un horodatage de la transaction SQL, pas la date d’émission du message ni une mesure de son temps passé dans MQ. Les dates API sont sérialisées en UTC. Le payload stocké est du texte décodé par JMS ; il ne représente pas une archive binaire de l’enveloppe MQ ou de tous ses en-têtes.

## Identité et doublons

La contrainte `received_message_transport_identity` est unique sur `(source_queue, jms_message_id)`. L’écriture utilise :

```sql
INSERT INTO messaging.received_message
    (source_queue, jms_message_id, correlation_id, content_type, payload_size, payload)
VALUES (:sourceQueue, :messageId, :correlationId,
        'text/plain;charset=UTF-8', :payloadSize, :payload)
ON CONFLICT (source_queue, jms_message_id) DO NOTHING;
```

Une insertion renvoie 1 ; un conflit sur cette identité renvoie 0 sans remplacer la ligne. Les autres erreurs SQL sont propagées. La contrainte reste la protection finale en cas de livraisons concurrentes ; aucune vérification préalable « existe puis insérer » n’est nécessaire.

| Cas | Résultat |
|---|---|
| Même file et même JMSMessageID | Une ligne, contenu initial conservé |
| Même texte et JMSMessageID différent | Deux lignes |
| Même JMSMessageID et file différente | Deux lignes |
| Même couple file/ID provenant de gestionnaires différents | Risque de collision dans le modèle actuel |

L’extension à plusieurs gestionnaires partageant une base nécessite d’ajouter un scope d’origine à l’identité. Un hash du contenu ne constitue pas un identifiant métier fiable.

## Migrations et index

Les scripts résident dans `backend/src/main/resources/db/migration/`. L’historique Flyway est dans `public.flyway_schema_history`.

| Migration | Effet |
|---|---|
| V1 — `create_messaging_schema` | Crée le schéma `messaging` |
| V2 — `create_received_message` | Crée la table, la clé primaire, l’unicité transport et l’index date/ID |
| V3 — `index_message_identifier` | Ajoute l’index de recherche par JMSMessageID seul |

| Index | Usage principal | Coût / limite |
|---|---|---|
| Clé primaire sur `id` | Détail d’un message | Coût d’écriture et espace |
| Unique `(source_queue, jms_message_id)` | Déduplication atomique, recherche identité/file, préfixe source | Le filtre source seul peut encore nécessiter un tri |
| `(received_at DESC, id DESC)` | Tri stable et filtres de date ; parcours inverse pour l’ascendant | Ne supprime pas le coût du comptage exact ou d’un offset profond |
| `(jms_message_id)` | Recherche exacte sans file source | Index supplémentaire maintenu à chaque insertion |

Les filtres combinés sont construits avec Criteria JPA et des valeurs paramétrées. La liste projette uniquement sept métadonnées ; seule la consultation de détail sélectionne le payload. Un index payload ou source/date supplémentaire n’est pas présent.

Ajouter une nouvelle migration versionnée pour faire évoluer le schéma. Ne pas réécrire une migration déjà appliquée : Flyway en vérifie les empreintes. Un retour arrière applicatif doit tenir compte de la compatibilité du schéma ; aucune migration descendante automatique n’est fournie.

## Vérifications SQL

Depuis la racine, avec les services démarrés et les noms par défaut :

```sh
docker compose exec -T postgres psql -U bank -d bank -c \
  'select version, description, success from public.flyway_schema_history order by installed_rank;'
docker compose exec -T postgres psql -U bank -d bank -c \
  'select id, source_queue, jms_message_id, payload_size, received_at from messaging.received_message order by received_at desc, id desc limit 10;'
```

Ces requêtes affichent les métadonnées, sans contenu du message. Adapter le compte et la base si `.env` les modifie.

## Cycle de vie et limites

Les volumes Docker assurent la persistance locale entre redémarrages ; ils ne constituent pas une sauvegarde. La purge, l’archivage, le partitionnement et la rétention ne sont pas automatisés. Définir ces politiques avant un usage durable, en tenant compte de l’espace occupé par le texte, les index et le journal SQL.

Une suppression future d’une ligne supprime aussi la mémoire de déduplication associée : la redélivraison d’un ancien message pourrait alors produire une nouvelle insertion. La rétention des données et celle des identités doivent donc être conçues ensemble. Les procédures d’exploitation figurent dans [EXPLOITATION.md](EXPLOITATION.md).

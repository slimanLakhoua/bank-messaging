# API REST et Swagger UI

## Accès

| Accès | Développement | Déploiement Compose applicatif |
|---|---|---|
| Base REST | `http://127.0.0.1:8080/api` | `http://127.0.0.1:8088/api` |
| Swagger UI | `http://127.0.0.1:8080/swagger-ui.html` | `http://127.0.0.1:8088/swagger-ui.html` |
| OpenAPI JSON | `http://127.0.0.1:8080/openapi.json` | `http://127.0.0.1:8088/openapi.json` |

Les deux opérations sont en lecture seule. Les succès sont des réponses `application/json` avec `Cache-Control: no-store`. Aucune authentification applicative n’est implémentée. Les ports peuvent être personnalisés selon [ENVIRONNEMENT.md](ENVIRONNEMENT.md).

## Liste paginée

`GET /api/messages`

| Paramètre | Défaut | Valeurs et sémantique |
|---|---|---|
| `page` | `0` | Entier de 0 à 10 000, index à partir de zéro |
| `size` | `20` | Entier de 1 à 100 |
| `messageId` | absent | JMSMessageID exact, 1–512 caractères, non blanc et sans NUL |
| `sourceQueue` | absent | Nom de file exact, 1–512 caractères, non blanc et sans NUL |
| `from` | absent | Instant ISO-8601 avec fuseau, borne inférieure incluse |
| `to` | absent | Instant ISO-8601 avec fuseau, borne supérieure exclue |
| `sort` | `receivedAt,desc` | `receivedAt,desc` ou `receivedAt,asc` |

Les filtres sont combinés par ET et s’appliquent au contenu comme au total. Si les deux dates existent, `from` doit être strictement antérieur à `to`. Les paramètres absents prennent leur défaut ; une valeur fournie vide ou mal formée n’est pas assimilée à une absence. Les paramètres inconnus sont ignorés. Aucune recherche partielle, plein texte ou recherche dans le payload n’est effectuée.

Le tri utilise `received_at` puis `id` dans le même sens pour départager les dates identiques. Les requêtes sont paramétrées ; le tri est choisi dans une liste fermée.

Exemple de réponse synthétique :

```json
{
  "content": [
    {
      "id": 42,
      "sourceQueue": "DEV.BANK.IN",
      "jmsMessageId": "ID:demo",
      "correlationId": null,
      "receivedAt": "2026-11-05T10:30:00Z",
      "contentType": "text/plain;charset=UTF-8",
      "payloadSize": 4
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

La liste ne contient pas le payload. Une base vide renvoie 200 avec `content=[]`, `totalElements=0` et `totalPages=0`. Une page située après la dernière est vide mais conserve le total. Une page supérieure à 10 000 est rejetée.

Comptage et contenu sont exécutés dans une transaction de lecture `REPEATABLE_READ`. Cela assure un instantané cohérent pour un appel HTTP ; les pages successives peuvent toutefois se décaler pendant l’ingestion. Il s’agit d’une pagination par offset, sans curseur.

## Détail

`GET /api/messages/{id}`

`id` est la clé SQL interne, entier signé 64 bits strictement positif, et non le JMSMessageID. La réponse contient les mêmes champs que la liste, plus `payload` :

```json
{
  "id": 42,
  "sourceQueue": "DEV.BANK.IN",
  "jmsMessageId": "ID:demo",
  "correlationId": null,
  "receivedAt": "2026-11-05T10:30:00Z",
  "contentType": "text/plain;charset=UTF-8",
  "payloadSize": 4,
  "payload": "test"
}
```

`correlationId` est nullable. Le texte peut contenir Unicode et retours à la ligne ; il doit être affiché comme donnée, sans interprétation HTML. Aucun endpoint d’écriture ou de suppression n’est disponible.

## Erreurs

Les erreurs applicatives de consultation et les erreurs de conversion HTTP sont présentées en `application/problem+json`, selon la forme Problem Details.

| Statut | Situation |
|---|---|
| 400 | Paramètre invalide, borne dépassée, intervalle incorrect ou identifiant mal formé/non positif |
| 404 | Identifiant positif valide mais absent |
| 503 | Erreur d’accès à la base ou de transaction |

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Message 42 introuvable.",
  "instance": "/api/messages/42"
}
```

Les titres standards peuvent être en anglais. Les clients doivent se baser sur le statut, pas sur le texte exact. Les réponses de panne ne révèlent ni SQL, ni payload, ni identifiants techniques. Les champs et erreurs sont décrits dans le [contrat OpenAPI](../backend/src/main/resources/static/openapi.json).

## Exemples curl

Avec le backend démarré :

```sh
curl --fail 'http://127.0.0.1:8080/api/messages?page=0&size=20'
curl --get --fail http://127.0.0.1:8080/api/messages \
  --data-urlencode 'sourceQueue=DEV.BANK.IN' \
  --data-urlencode 'from=2026-11-05T00:00:00Z' \
  --data-urlencode 'to=2026-11-06T00:00:00Z' \
  --data-urlencode 'sort=receivedAt,asc'
curl --get --fail http://127.0.0.1:8080/api/messages \
  --data-urlencode 'messageId=ID:demo'
curl --fail http://127.0.0.1:8080/openapi.json
curl -i 'http://127.0.0.1:8080/api/messages?size=101'
```

Pour le détail, remplacer `42` par une clé réellement obtenue dans la liste :

```sh
curl --fail http://127.0.0.1:8080/api/messages/42
```

## Utiliser Swagger UI

Ouvrir `/swagger-ui.html`, développer une opération, sélectionner **Try it out**, renseigner ses paramètres puis **Execute**. L’URL d’entrée redirige vers `/swagger-ui/index.html`.

Springdoc 3.1.1 sert Swagger UI et ses ressources localement, sans CDN. L’interface charge le fichier maintenu `/openapi.json` ; son serveur relatif `/` cible la même origine, y compris derrière Nginx ou sur un port différent. Le validateur externe et l’exemple Petstore sont désactivés.

`springdoc.enable-default-api-docs=false` empêche la publication d’un second document inféré : `/v3/api-docs` renvoie 404, tandis que `/v3/api-docs/swagger-config` reste disponible pour l’initialisation UI. Cette approche conserve les types et bornes sémantiques du contrat même si le contrôleur reçoit certains paramètres comme chaînes pour distinguer absence et valeur vide.

Toute évolution d’endpoint doit mettre à jour le fichier OpenAPI, la présente documentation et ses tests HTTP. Swagger UI n’ajoute aucun mécanisme d’authentification ou d’autorisation.

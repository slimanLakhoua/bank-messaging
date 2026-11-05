# Infrastructure locale

`compose.yaml` fournit PostgreSQL et IBM MQ Advanced for Developers. Les deux services possèdent des volumes nommés et des contrôles de santé. Le projet Compose se nomme `bank-messaging` ; les ports hôte sont liés à `127.0.0.1`.

| Service | Image | Volume | Accès local par défaut |
|---|---|---|---|
| PostgreSQL | `postgres:17.6-alpine3.22` | `postgres-data` | 55432 |
| IBM MQ | `icr.io/ibm-messaging/mq:10.0.1.0-r1` | `mq-data` | 1414, console 9443 |

La plateforme MQ est fixée à `linux/amd64`. Une émulation peut être nécessaire sur Apple Silicon ; cette variante n’est pas couverte par les validations conservées. Le démarrage utilise `MQ_LICENSE=accept` pour l’édition de développement. Consulter les [indications et conditions IBM](https://github.com/ibm-messaging/mq-container#license) avant usage ; les images tierces ne sont pas incluses dans l’archive applicative.

Les mots de passe MQ sont fournis par secrets Compose depuis les variables d’environnement. Le compte applicatif est `app`, le gestionnaire `QM1` et le canal `DEV.APP.SVRCONN`. Le compte de console est `admin`. Les identifiants d’exemple doivent être remplacés dans la configuration locale.

## Files

`mq/20-bank.mqsc` crée :

| File | Politique |
|---|---|
| `DEV.BANK.IN` | Persistance par défaut, `BOTHRESH(0)`, `BOQNAME(DEV.BANK.BACKOUT)` |
| `DEV.BANK.BACKOUT` | Persistance par défaut |

Le préfixe `DEV.` utilise les droits applicatifs fournis par l’image de développement. `BOTHRESH(0)` désactive le backout automatique du provider : l’application distingue les formats invalides des pannes SQL avant de décider un transfert. Envoi BOQ et réception source sont réalisés dans la même session transactionnelle.

Une modification du fichier MQSC ne garantit pas la mise à jour d’un gestionnaire déjà initialisé. Vérifier/appliquer la politique avec `runmqsc` selon [INGESTION.md](../docs/INGESTION.md), sans effacer les volumes.

## Cycle de vie

```sh
docker compose up -d --wait --wait-timeout 240
docker compose ps
docker compose down
```

L’arrêt conserve les volumes ; `down -v` les supprime. Modifier un secret PostgreSQL dans `.env` ne change pas un compte déjà créé sur un volume existant. Le [guide de configuration](../docs/ENVIRONNEMENT.md) décrit les variables ; le [guide d’exploitation](../docs/EXPLOITATION.md) décrit diagnostic et sauvegardes.

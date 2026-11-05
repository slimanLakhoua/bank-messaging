# Ingestion et résilience MQ vers PostgreSQL

## Chemin nominal

`DefaultMessageListenerContainer` consomme la file configurée avec `sessionTransacted=true`. `JmsMessageListener` extrait le texte et les métadonnées, contrôle le format, puis appelle `StoreMessageUseCase`. Le décorateur `TransactionalMessageStorage` termine la transaction SQL avant de rendre la main au listener. Le conteneur valide ensuite la réception MQ.

Le traitement est synchrone : un message reste dans la responsabilité de sa session jusqu’au retour du listener. Aucun executor de traitement ni tampon mémoire intermédiaire ne découple la réception de la persistance.

## Garanties et fenêtres de panne

| Moment de l’incident | Conséquence attendue |
|---|---|
| Avant le commit SQL | Pas de succès de stockage ; rollback de la réception et nouvelle livraison |
| Après commit SQL, avant commit MQ | Message déjà durable en SQL ; redélivraison possible, traitée comme doublon |
| Après commit MQ | Réception terminée et message consultable en SQL |
| Pendant l’envoi en backout | Réception source et envoi partagent la transaction MQ ; rollback si l’envoi échoue |

L’application offre une livraison au moins une fois et un stockage idempotent. Elle ne coordonne pas les commits SQL/MQ par XA et ne revendique pas de livraison exactly-once distribuée. La durabilité face à une panne disque, une corruption ou une perte des volumes relève de l’infrastructure et des sauvegardes.

La transaction SQL utilise `REQUIRES_NEW` avec un délai de 10 secondes. L’unicité `(source_queue, jms_message_id)` et l’insertion atomique ignorent uniquement le conflit de transport ciblé. Un doublon est un succès ; une erreur SQL ou de commit remonte au conteneur et empêche l’acquittement nominal.

## Formats acceptés et erreurs permanentes

Le format accepté est un `TextMessage` avec identité non vide et texte non null. Le texte vide est accepté. La taille maximale par défaut est 1 048 576 octets UTF-8 ; cette borne est vérifiée après extraction du texte en mémoire. Elle ne constitue donc pas une limite de taille MQ avant décodage.

| Raison de rejet | Condition |
|---|---|
| `MISSING_MESSAGE_ID` | JMSMessageID absent ou vide |
| `UNSUPPORTED_MESSAGE_TYPE` | Message autre qu’un `TextMessage` |
| `NULL_TEXT` | Corps texte null |
| `TEXT_CONTAINS_NUL` | Caractère NUL dans le texte, incompatible avec le stockage texte PostgreSQL |
| `PAYLOAD_TOO_LARGE` | Taille UTF-8 supérieure à la limite configurée |
| `MESSAGE_DECODING_FAILED` | Erreur permanente de format JMS ou de conversion de caractères |

Une erreur de décodage est permanente si la chaîne des causes ou des exceptions JMS liées contient `MessageFormatException` ou `CharacterCodingException`. Les autres erreurs JMS de lecture restent des erreurs transitoires et ne sont pas mises en quarantaine sur le seul nombre de livraisons.

## Backout et redélivraison

Pour une erreur permanente, le listener lit `JMSXDeliveryCount`. Tant que ce nombre est inférieur à `INGESTION_MAX_ATTEMPTS`, il provoque un rollback. À partir du seuil, il envoie le message dans la file de backout avec la session de réception, en mode persistant et sans expiration automatique. Le seuil par défaut est trois livraisons ; il inclut les livraisons déjà effectuées avant le démarrage courant.

Le transfert conserve le corps, la corrélation et les propriétés applicatives non préfixées `JMS`. Le provider régénère les propriétés réservées et l’identité MQ ; le listener ajoute :

| Propriété | Valeur |
|---|---|
| `OriginalMessageId` | Ancienne identité lorsqu’elle existe |
| `OriginalSourceQueue` | File d’origine |
| `FailureReason` | Code technique du rejet |
| `FailedDeliveryCount` | Nombre de livraisons observé |

Si le transfert échoue, l’exception empêche le commit de la réception source. La BOQ applicative est distincte d’une dead-letter queue du gestionnaire : elle isole les messages que l’application ne peut pas accepter. Aucun rejeu, purge ou écran de gestion BOQ n’est fourni.

Les erreurs SQL et de connexion ne sont pas classées comme permanentes, même après de nombreuses livraisons. Elles provoquent rollback et reprise temporisée, sans seuil d’abandon. Une panne durable peut donc faire croître le backlog ou empêcher la progression ; cette situation doit être surveillée.

## Configuration MQ requise

Les fichiers MQSC créent `DEV.BANK.IN` et `DEV.BANK.BACKOUT`, avec messages persistants par défaut. La source utilise `BOTHRESH(0)` pour désactiver le backout automatique du provider IBM JMS. Ce choix évite qu’une panne SQL répétée déplace un message valide en quarantaine. `BOQNAME` indique la destination, mais le transfert est réalisé explicitement par le listener.

Pour contrôler les files, depuis la racine :

```sh
printf "DISPLAY QLOCAL('DEV.BANK.*') BOTHRESH BOQNAME CURDEPTH\n" | docker compose exec -T mq runmqsc QM1
```

Sur un gestionnaire existant dont la source n’a pas cette politique, appliquer explicitement :

```sh
printf "ALTER QLOCAL('DEV.BANK.IN') BOTHRESH(0) BOQNAME('DEV.BANK.BACKOUT')\n" | docker compose exec -T mq runmqsc QM1
```

Ne pas réinitialiser les volumes pour une modification de configuration. Les noms personnalisés exigent des files existantes, des droits adaptés et la même politique de backout. Le producteur peut surcharger la persistance par défaut : l’application ne rend pas rétroactivement durable un message envoyé en mode non persistant.

## Concurrence et pression sur les ressources

Le minimum et le maximum JMS sont configurables. Le maximum doit être strictement inférieur à la taille Hikari, partagée avec la consultation. Le réglage par défaut est un consommateur et cinq connexions SQL. La capacité supplémentaire ne réserve pas une connexion à REST : les demandes concurrentes utilisent le même pool.

La session a un délai de réception de 1 seconde. La reprise après erreur utilise `INGESTION_RETRY_DELAY_MS`, 1 seconde par défaut. Le backlog reste dans MQ ; augmenter la concurrence doit être justifié par une mesure et par le budget SQL. Plusieurs consommateurs n’offrent pas d’ordre global de traitement.

## Injection de démonstration

Depuis la racine, valeurs par défaut :

```sh
printf 'paiement-synthetique\n\n' | docker compose exec -T mq /opt/mqm/samp/bin/amqsput DEV.BANK.IN QM1
curl --fail 'http://127.0.0.1:8080/api/messages?sourceQueue=DEV.BANK.IN&size=20'
```

L’outil sample termine l’entrée sur une ligne vide. Une profondeur MQ nulle après consommation est normale ; les messages sont ensuite consultés dans PostgreSQL via REST/Angular.

Les scénarios réels de panne, concurrence et crash sont décrits dans [TESTS.md](TESTS.md). Les compteurs, timers et consignes de reprise sont dans [EXPLOITATION.md](EXPLOITATION.md).

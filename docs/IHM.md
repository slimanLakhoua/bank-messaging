# Guide utilisateur et frontend Angular

## Ouvrir l’application

En développement : [localhost:4200/messages](http://127.0.0.1:4200/messages). En mode Compose applicatif : [localhost:8088/messages](http://127.0.0.1:8088/messages). Les procédures de démarrage figurent dans [ENVIRONNEMENT.md](ENVIRONNEMENT.md) et [LIVRAISON.md](LIVRAISON.md).

## Rechercher et consulter

La page `/messages` affiche les métadonnées enregistrées : clé interne, identité MQ, file source, date UTC et taille en octets. Le contenu complet n’est chargé qu’au détail.

1. Saisir, si nécessaire, l’identité MQ exacte, la file exacte ou une période UTC.
2. Choisir les messages les plus récents ou les plus anciens en premier.
3. Cliquer **Rechercher**. **Effacer les filtres** rétablit une recherche sans critères.
4. Choisir 20, 50 ou 100 messages par page et utiliser **Précédente** / **Suivante**.
5. Ouvrir **Voir le détail**, puis **Retour aux messages** pour retrouver les mêmes critères.

Les bornes temporelles sont début inclus et fin exclue. Une plage inversée est signalée sans appel HTTP. Les champs de date sont explicitement interprétés en UTC, même si le navigateur fonctionne dans un autre fuseau. Les dates d’URL avec fuseau sont normalisées en UTC ; la précision milliseconde des filtres est conservée. L’affichage tabulaire des dates est à la seconde.

La recherche, la pagination et le tri sont conservés dans l’URL et peuvent être partagés ou rechargés. Rechercher, effacer les filtres ou changer la taille revient à la première page. **Actualiser** recharge la page courante ; il n’existe pas de rafraîchissement continu ni de notification push.

## Détail d’un message

La route `/messages/{id}` présente les métadonnées, la corrélation lorsqu’elle existe et le texte intégral. Le payload est affiché dans un élément `pre` par interpolation Angular, sans `innerHTML`. Un texte ressemblant à du HTML ou du JavaScript reste du texte. Les longs contenus et tableaux disposent d’un défilement adapté.

La clé du détail est l’ID SQL, pas l’identité MQ. Les messages en backout ne sont pas affichés dans l’application.

## États et récupération

| État | Affichage / action |
|---|---|
| Chargement | Indication explicite pendant la requête |
| Liste vide | Message d’absence ; retour au début si la page courante dépasse les résultats |
| Erreur réseau ou serveur | Message générique et bouton **Réessayer** |
| Message absent, HTTP 404 | Écran **Message introuvable** et retour à la liste |
| Requête sans réponse | Annulation après 15 secondes puis état d’erreur |
| URL de pagination invalide | Normalisation vers page 0 et taille 20 selon les valeurs reçues |

L’interface limite les tailles à 20, 50 ou 100 ; l’API accepte tous les entiers de 1 à 100. La page API maximale est 10 000. Les détails techniques d’erreur ne sont pas exposés à l’utilisateur.

## Structure frontend

Les sources résident dans `frontend/src/app/features/messages/`.

| Élément | Responsabilité |
|---|---|
| `messages.routes.ts` | Chargement différé des pages liste/détail |
| `pages/message-list` | Formulaire, état de liste et navigation |
| `pages/message-detail` | Chargement et rendu du message complet |
| `data-access/messages-api.ts` | Appels HTTP typés, encodage de l’identifiant de détail |
| `data-access/message.models.ts` | Modèles TypeScript de lecture |
| `data-access/request-state.ts` | État discriminé, timeout et conversion des erreurs |
| `message-search.ts` | Lecture et normalisation des critères d’URL |
| `message-pagination.ts` | Tailles autorisées, défauts et borne de page |

Les composants sont standalone et utilisent `OnPush`. Les signaux alimentent les templates ; RxJS combine les paramètres de route et les demandes de rafraîchissement. `switchMap` annule une requête devenue obsolète ; `toSignal` gère la souscription liée au composant. Le formulaire déclenche une recherche explicite et n’émet pas de requête à chaque frappe. Aucun store global n’est nécessaire pour cet état local à la fonctionnalité.

Le frontend est une application rendue dans le navigateur ; aucun serveur SSR n’est fourni. Nginx sert les fichiers de production et renvoie `index.html` pour les routes Angular profondes.

## Lisibilité et accessibilité

La navigation fournit un lien d’évitement, des labels, un focus visible et des commandes clavier. Les états utilisent les rôles de statut/alerte. Les couleurs suivent les préférences système clair/sombre et les couleurs forcées. Le tableau reste défilable sans imposer un débordement global de la page.

Les tests ciblent navigation clavier, texte brut, états réseau et contrastes ; ils ne constituent pas une certification exhaustive d’accessibilité ou une validation sur tous les navigateurs. Les commandes frontend et Playwright sont documentées dans [TESTS.md](TESTS.md).

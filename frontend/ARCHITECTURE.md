# Architecture frontend

Le frontend utilise des composants Angular standalone, une organisation par fonctionnalité et des routes chargées à la demande. Le périmètre courant est la consultation de messages ; aucun store global ni logique d’ingestion ne réside dans le navigateur.

| Emplacement                                       | Responsabilité                                                    |
| ------------------------------------------------- | ----------------------------------------------------------------- |
| `src/app/app.config.ts`                           | Providers HTTP, routeur et gestion globale des erreurs navigateur |
| `src/app/app.routes.ts`                           | Redirection initiale, route de fonctionnalité et repli            |
| `src/app/features/messages/messages.routes.ts`    | Pages liste/détail différées                                      |
| `src/app/features/messages/pages/`                | Orchestration des pages et templates                              |
| `src/app/features/messages/data-access/`          | Client HTTP typé, modèles et cycle de requête                     |
| `src/app/features/messages/message-search.ts`     | Critères d’URL et normalisation des dates                         |
| `src/app/features/messages/message-pagination.ts` | Tailles, défauts et borne de page                                 |
| `e2e/`                                            | Scénarios navigateur contrôlés et parcours réel optionnel         |

## Dépendances et état

Les pages dépendent de la route et de `MessagesApi`. Le service HTTP retourne des observables sans souscrire et ne porte pas d’état d’interface. Les modèles sont readonly. Les composants `OnPush` exposent des signaux aux templates.

Les paramètres d’URL sont la source de vérité pour page, taille, filtres et tri. La page liste écrit ces paramètres et le lien retour du détail les observe. Les paramètres invalides de pagination sont normalisés pour les requêtes et l’affichage, sans réécriture automatique systématique de l’URL.

`switchMap` annule les appels obsolètes ; un `Subject` déclenche les actualisations manuelles. `toSignal` gère la souscription avec le cycle de vie du composant. L’état discriminé représente chargement, succès, erreur et absence ; le délai HTTP est de 15 secondes. Les erreurs techniques brutes ne sont pas affichées.

Le formulaire est soumis explicitement, sans requête à chaque frappe. Les dates sont traitées en UTC. La pagination et l’aller-retour détail conservent les critères. Le payload est interpolé en texte dans un `pre`, sans interprétation HTML.

## Construction et validation

TypeScript et les templates sont vérifiés en mode strict. Les styles partagés définissent les thèmes, contrastes et focus. Les tests Vitest vérifient les fonctions pures, le transport et les composants ; les tests routés et Playwright couvrent leur interaction.

Voir [IHM.md](../docs/IHM.md) pour le comportement utilisateur, [TESTS.md](../docs/TESTS.md) pour les commandes et [ARCHITECTURE.md](../docs/ARCHITECTURE.md) pour l’ensemble du système.

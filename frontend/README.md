# Frontend BANK Messaging

Application Angular 22.2.4 de consultation des messages enregistrés : recherche exacte par identité/file, période UTC, tri, pagination et détail du texte brut.

## Développement

Avec Node 24.21.0 et npm 11, depuis `frontend/` :

```sh
npm ci
npm start
```

Ouvrir [les messages](http://127.0.0.1:4200/messages). Le proxy `/api` cible Java sur `http://127.0.0.1:8080`. Les dépendances MQ/SQL et le backend doivent être démarrés selon [le guide d’installation](../docs/ENVIRONNEMENT.md).

## Vérification et construction

```sh
npm test -- --watch=false
npm run format:check
npm run build
npx playwright install chromium
npm run e2e -- consultation.spec.ts
```

Le build de production est dans `dist/bank-messaging/browser/`. `npm run format` applique le formatage. Les parcours réels, les préconditions et les limites de validation sont dans [TESTS.md](../docs/TESTS.md).

Voir [l’architecture frontend](ARCHITECTURE.md), [le guide utilisateur](../docs/IHM.md) et [la documentation complète](../docs/README.md).

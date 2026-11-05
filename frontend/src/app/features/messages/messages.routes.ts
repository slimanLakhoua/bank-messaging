import { Routes } from '@angular/router';

export const messageRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/message-list/message-list').then((module) => module.MessageList),
    title: 'Messages reçus · BANK Messaging',
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./pages/message-detail/message-detail').then((module) => module.MessageDetailView),
    title: 'Détail du message · BANK Messaging',
  },
];

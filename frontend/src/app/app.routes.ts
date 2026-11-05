import { Routes } from '@angular/router';
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'messages' },
  {
    path: 'messages',
    loadChildren: () =>
      import('./features/messages/messages.routes').then((module) => module.messageRoutes),
  },
  { path: '**', redirectTo: 'messages' },
];

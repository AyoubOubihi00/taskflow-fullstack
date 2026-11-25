import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/guards/auth.guard';
import { AuthPageComponent } from './features/auth/auth-page/auth-page.component';
import { WorkspaceComponent } from './features/tasks/workspace/workspace.component';

export const routes: Routes = [
  { path: 'login', component: AuthPageComponent, canActivate: [guestGuard], title: 'Connexion · TaskFlow' },
  {
    path: 'register', component: AuthPageComponent, canActivate: [guestGuard],
    data: { registration: true }, title: 'Inscription · TaskFlow',
  },
  {
    path: '', pathMatch: 'full', component: WorkspaceComponent, canActivate: [authGuard],
    data: { dashboard: true }, title: 'Tableau de bord · TaskFlow',
  },
  { path: 'tasks', component: WorkspaceComponent, canActivate: [authGuard], title: 'Toutes les tâches · TaskFlow' },
  { path: '**', redirectTo: '' },
];

import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { Component } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter, RouterOutlet, Routes } from '@angular/router';
import { AuthPageComponent } from './app/auth-page.component';
import { authGuard, guestGuard } from './app/auth.guard';
import { authInterceptor } from './app/auth.interceptor';
import { WorkspaceComponent } from './app/workspace.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
class AppComponent {}

const routes: Routes = [
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

bootstrapApplication(AppComponent, {
  providers: [provideHttpClient(withInterceptors([authInterceptor])), provideRouter(routes)],
}).catch((error: unknown) => console.error(error));

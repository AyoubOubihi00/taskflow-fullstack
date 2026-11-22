import { provideHttpClient } from '@angular/common/http';
import { Component } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter, RouterOutlet, Routes } from '@angular/router';
import { WorkspaceComponent } from './app/workspace.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
class AppComponent {}

const routes: Routes = [
  { path: '', pathMatch: 'full', component: WorkspaceComponent, data: { dashboard: true }, title: 'Tableau de bord · TaskFlow' },
  { path: 'tasks', component: WorkspaceComponent, title: 'Toutes les tâches · TaskFlow' },
  { path: '**', redirectTo: '' },
];

bootstrapApplication(AppComponent, {
  providers: [provideHttpClient(), provideRouter(routes)],
}).catch((error: unknown) => console.error(error));

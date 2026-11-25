import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { API_URL } from '../config/api.config';

export interface Credentials {
  email: string;
  password: string;
}

interface AuthResponse {
  accessToken: string;
  expiresAt: string;
  email: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<{ token: string; expiresAt: number } | null>(null);
  private readonly notice = signal('');
  private expiryTimer?: ReturnType<typeof setTimeout>;

  readonly sessionNotice = this.notice.asReadonly();

  login(credentials: Credentials) {
    return this.authenticate('login', credentials);
  }

  register(credentials: Credentials & { claimCode?: string }) {
    return this.authenticate('register', credentials);
  }

  accessToken(): string | null {
    const session = this.session();
    return session && session.expiresAt > Date.now() ? session.token : null;
  }

  rejectToken(token: string) {
    if (this.session()?.token === token) {
      this.expireSession();
    }
  }

  logout() {
    this.endSession('');
  }

  private authenticate(path: 'login' | 'register', credentials: Credentials) {
    return this.http.post<AuthResponse>(API_URL + '/auth/' + path, credentials).pipe(
      tap(response => {
        const expiresAt = Date.parse(response.expiresAt);
        const remaining = expiresAt - Date.now();
        if (!response.accessToken || !Number.isFinite(remaining) || remaining <= 0) {
          throw new Error('La session renvoyée est invalide.');
        }
        clearTimeout(this.expiryTimer);
        this.notice.set('');
        this.session.set({ token: response.accessToken, expiresAt });
        this.expiryTimer = setTimeout(() => this.expireSession(), remaining);
      }),
    );
  }

  private expireSession() {
    this.endSession('Ta session a expiré. Connecte-toi à nouveau pour retrouver tes tâches.');
  }

  private endSession(message: string) {
    clearTimeout(this.expiryTimer);
    this.session.set(null);
    this.notice.set(message);
    void this.router.navigateByUrl('/login', { replaceUrl: true });
  }
}

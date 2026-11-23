import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';
import { TASKS_URL } from './api.config';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (request.url !== TASKS_URL && !request.url.startsWith(TASKS_URL + '/')) {
    return next(request);
  }

  const auth = inject(AuthService);
  const token = auth.accessToken();
  if (!token) {
    return next(request);
  }

  return next(request.clone({ setHeaders: { Authorization: 'Bearer ' + token } })).pipe(
    tap({
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 401) {
          auth.rejectToken(token);
        }
      },
    }),
  );
};

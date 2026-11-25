import { HttpErrorResponse } from '@angular/common/http';

export interface ApiError<Field extends string> {
  message?: string;
  errors?: Partial<Record<Field, string[]>>;
}

export function readApiError<Field extends string>(error: unknown): ApiError<Field> | undefined {
  if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object') {
    return error.error as ApiError<Field>;
  }
  return undefined;
}

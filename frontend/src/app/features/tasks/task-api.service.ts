import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { TASKS_URL } from '../../core/config/api.config';
import { readApiError } from '../../core/http/api-error';
import { TaskRequestDTO, TaskResponseDTO } from './task.model';

@Injectable({ providedIn: 'root' })
export class TaskApiService {
  private readonly http = inject(HttpClient);
  private readonly url = TASKS_URL;

  getTasks() {
    return this.http.get<TaskResponseDTO[]>(this.url);
  }

  create(task: TaskRequestDTO) {
    return this.http.post<TaskResponseDTO>(this.url, task);
  }

  update(id: number, task: TaskRequestDTO) {
    return this.http.put<TaskResponseDTO>(this.taskUrl(id), task);
  }

  delete(id: number) {
    return this.http.delete<void>(this.taskUrl(id));
  }

  private taskUrl(id: number) {
    return this.url + '/' + id;
  }
}

export function taskErrorMessage(error: unknown, fallback: string) {
  if (error instanceof HttpErrorResponse && error.status === 0) {
    return 'Impossible de joindre l’API. Vérifiez que le serveur Spring Boot est démarré.';
  }
  return readApiError(error)?.message || (error instanceof HttpErrorResponse && error.status === 403
    ? 'Tu n’as pas accès à cette action.'
    : fallback);
}

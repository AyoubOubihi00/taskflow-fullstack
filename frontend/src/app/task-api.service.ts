import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { TASKS_URL } from './api.config';
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

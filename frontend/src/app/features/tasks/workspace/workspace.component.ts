import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { SavedTask, TaskDialogsComponent } from '../task-dialogs/task-dialogs.component';
import { TaskApiService, taskErrorMessage } from '../task-api.service';
import { StatusFilter, TASK_STATUSES, TaskResponseDTO } from '../task.model';

@Component({
  selector: 'app-workspace',
  imports: [RouterLink, RouterLinkActive, TaskDialogsComponent],
  templateUrl: './workspace.component.html',
  styleUrls: ['../task-controls.css', './workspace.component.css'],
})
export class WorkspaceComponent {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(TaskApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly queryParams = toSignal(this.route.queryParamMap);
  private readonly tasks = signal<TaskResponseDTO[]>([]);

  protected readonly dashboard = this.route.snapshot.data['dashboard'] === true;
  protected readonly statusOptions = TASK_STATUSES;
  protected readonly stats = computed(() => {
    const tasks = this.tasks();
    const counts = { TODO: 0, IN_PROGRESS: 0, DONE: 0 };
    for (const task of tasks) {
      if (task.status !== null) {
        counts[task.status]++;
      }
    }

    return {
      counts,
      total: tasks.length,
      completion: tasks.length === 0 ? 0 : Math.round(100 * counts.DONE / tasks.length),
    };
  });

  protected readonly filter = computed<StatusFilter>(() => {
    const status = this.queryParams()?.get('status');
    return TASK_STATUSES.find(option => option.value === status)?.value ?? 'ALL';
  });

  protected readonly search = signal('');
  protected readonly visibleTasks = computed(() => {
    const title = this.normalize(this.search().trim());
    const status = this.filter();
    return this.tasks().filter(task =>
      (status === 'ALL' || task.status === status) && this.normalize(task.title).includes(title),
    ).sort((first, second) => second.id - first.id);
  });

  protected readonly loading = signal(true);
  protected readonly loadError = signal('');
  protected readonly success = signal('');

  constructor() {
    this.loadTasks();
  }

  protected loadTasks() {
    this.loading.set(true);
    this.loadError.set('');
    this.api.getTasks().pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.loading.set(false)),
    ).subscribe({
      next: tasks => this.tasks.set(tasks),
      error: (error: unknown) => this.loadError.set(taskErrorMessage(error, 'Impossible de charger les tâches.')),
    });
  }

  protected selectFilter(status: StatusFilter) {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { status: status === 'ALL' ? null : status },
      replaceUrl: true,
    });
  }

  protected clearFilters() {
    this.search.set('');
    this.selectFilter('ALL');
  }

  protected statusInfo(status: TaskResponseDTO['status']) {
    return TASK_STATUSES.find(option => option.value === status) ??
      { label: 'Sans statut', icon: 'circle-dashed', tone: 'todo' };
  }

  protected onTaskSaved({ task, editingId }: SavedTask) {
    this.tasks.update(tasks => editingId === null
      ? [...tasks, task]
      : tasks.map(existing => existing.id === editingId ? task : existing));
    this.success.set(editingId === null ? 'Tâche créée.' : 'Tâche modifiée.');
  }

  protected onTaskDeleted(id: number) {
    this.tasks.update(tasks => tasks.filter(task => task.id !== id));
    this.success.set('Tâche supprimée.');
  }

  private normalize(value: string) {
    return value.toLocaleLowerCase('fr').normalize('NFD').replace(/\p{Diacritic}/gu, '');
  }
}

import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from './auth.service';
import { TaskApiService } from './task-api.service';
import { StatusFilter, TASK_STATUSES, TaskApiError, TaskRequestDTO, TaskResponseDTO } from './task.model';

@Component({
  selector: 'app-workspace',
  imports: [ReactiveFormsModule, RouterLink, RouterLinkActive],
  templateUrl: './workspace.component.html',
  styleUrl: './workspace.component.css',
})
export class WorkspaceComponent {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(TaskApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(FormBuilder).nonNullable;
  private readonly queryParams = toSignal(this.route.queryParamMap);
  private readonly tasks = signal<TaskResponseDTO[]>([]);
  private readonly taskDialog = viewChild.required<ElementRef<HTMLDialogElement>>('taskDialog');
  private readonly deleteDialog = viewChild.required<ElementRef<HTMLDialogElement>>('deleteDialog');

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
  protected readonly pending = signal(false);
  protected readonly editingId = signal<number | null>(null);
  protected readonly deletingTask = signal<TaskResponseDTO | null>(null);
  protected readonly dialogError = signal('');
  protected readonly serverFieldErrors = signal<TaskApiError['errors']>({});
  protected readonly titleLimit = 120;
  protected readonly descriptionLimit = 2000;
  protected readonly taskForm = this.formBuilder.group({
    title: ['', [Validators.required, Validators.pattern(/\S/u), Validators.maxLength(this.titleLimit)]],
    description: ['', Validators.maxLength(this.descriptionLimit)],
    status: this.formBuilder.control<TaskRequestDTO['status']>('TODO', [
      Validators.required,
      Validators.pattern('(?:' + TASK_STATUSES.map(option => option.value).join('|') + ')'),
    ]),
  });

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
      error: (error: unknown) => this.loadError.set(this.apiMessage(error, 'Impossible de charger les tâches.')),
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

  protected openForm(task?: TaskResponseDTO) {
    this.editingId.set(task?.id ?? null);
    this.dialogError.set('');
    this.serverFieldErrors.set({});
    this.taskForm.reset({
      title: task?.title ?? '',
      description: task?.description ?? '',
      status: task?.status ?? 'TODO',
    });
    this.taskDialog().nativeElement.showModal();
  }

  protected saveTask() {
    if (this.pending()) {
      return;
    }
    if (this.taskForm.invalid) {
      this.taskForm.markAllAsTouched();
      this.taskDialog().nativeElement
        .querySelector<HTMLElement>('input.ng-invalid, textarea.ng-invalid, select.ng-invalid')
        ?.focus();
      return;
    }

    const { title, description, status } = this.taskForm.getRawValue();
    const task = { title, description: description || null, status };
    const id = this.editingId();
    this.pending.set(true);
    this.dialogError.set('');
    this.serverFieldErrors.set({});
    this.success.set('');

    const request = id === null ? this.api.create(task) : this.api.update(id, task);
    request.pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.pending.set(false)),
    ).subscribe({
      next: saved => {
        this.tasks.update(tasks => id === null
          ? [...tasks, saved]
          : tasks.map(existing => existing.id === id ? saved : existing));
        this.taskDialog().nativeElement.close();
        this.success.set(id === null ? 'Tâche créée.' : 'Tâche modifiée.');
      },
      error: (error: unknown) => {
        this.dialogError.set(this.apiMessage(error, 'Impossible d’enregistrer la tâche.'));
        this.serverFieldErrors.set(this.apiError(error)?.errors ?? {});
      },
    });
  }

  protected clearFieldError(field: keyof TaskRequestDTO) {
    this.serverFieldErrors.update(errors => ({ ...errors, [field]: undefined }));
  }

  protected openDeletion(task: TaskResponseDTO) {
    this.deletingTask.set(task);
    this.dialogError.set('');
    this.deleteDialog().nativeElement.showModal();
  }

  protected deleteTask() {
    const task = this.deletingTask();
    if (!task || this.pending()) {
      return;
    }

    this.pending.set(true);
    this.dialogError.set('');
    this.success.set('');
    this.api.delete(task.id).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.pending.set(false)),
    ).subscribe({
      next: () => {
        this.tasks.update(tasks => tasks.filter(existing => existing.id !== task.id));
        this.deleteDialog().nativeElement.close();
        this.deletingTask.set(null);
        this.success.set('Tâche supprimée.');
      },
      error: (error: unknown) => this.dialogError.set(this.apiMessage(error, 'Impossible de supprimer la tâche.')),
    });
  }

  protected closeDialog(dialog: HTMLDialogElement) {
    if (!this.pending()) {
      dialog.close();
    }
  }

  protected onDialogCancel(event: Event) {
    if (this.pending()) {
      event.preventDefault();
    }
  }

  private normalize(value: string) {
    return value.toLocaleLowerCase('fr').normalize('NFD').replace(/\p{Diacritic}/gu, '');
  }

  private apiError(error: unknown): Partial<TaskApiError> | undefined {
    return error instanceof HttpErrorResponse && error.error && typeof error.error === 'object'
      ? error.error as Partial<TaskApiError>
      : undefined;
  }

  private apiMessage(error: unknown, fallback: string) {
    if (error instanceof HttpErrorResponse && error.status === 0) {
      return 'Impossible de joindre l’API. Vérifiez que le serveur Spring Boot est démarré.';
    }
    return this.apiError(error)?.message || (error instanceof HttpErrorResponse && error.status === 403
      ? 'Tu n’as pas accès à cette action.'
      : fallback);
  }
}

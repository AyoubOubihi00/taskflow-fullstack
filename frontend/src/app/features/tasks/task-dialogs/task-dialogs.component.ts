import { Component, DestroyRef, ElementRef, inject, output, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { readApiError } from '../../../core/http/api-error';
import { TaskApiService, taskErrorMessage } from '../task-api.service';
import { TASK_STATUSES, TaskRequestDTO, TaskResponseDTO } from '../task.model';

export interface SavedTask {
  task: TaskResponseDTO;
  editingId: number | null;
}

@Component({
  selector: 'app-task-dialogs',
  imports: [ReactiveFormsModule],
  templateUrl: './task-dialogs.component.html',
  styleUrls: ['../task-controls.css', './task-dialogs.component.css'],
})
export class TaskDialogsComponent {
  private readonly api = inject(TaskApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(FormBuilder).nonNullable;
  private readonly taskDialog = viewChild.required<ElementRef<HTMLDialogElement>>('taskDialog');
  private readonly deleteDialog = viewChild.required<ElementRef<HTMLDialogElement>>('deleteDialog');

  readonly actionStarted = output<void>();
  readonly saved = output<SavedTask>();
  readonly deleted = output<number>();
  protected readonly statusOptions = TASK_STATUSES;
  protected readonly pending = signal(false);
  protected readonly editingId = signal<number | null>(null);
  protected readonly deletingTask = signal<TaskResponseDTO | null>(null);
  protected readonly dialogError = signal('');
  protected readonly serverFieldErrors = signal<Partial<Record<keyof TaskRequestDTO, string[]>>>({});
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

  openForm(task?: TaskResponseDTO) {
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
    this.actionStarted.emit();

    const request = id === null ? this.api.create(task) : this.api.update(id, task);
    request.pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.pending.set(false)),
    ).subscribe({
      next: saved => {
        this.taskDialog().nativeElement.close();
        this.saved.emit({ task: saved, editingId: id });
      },
      error: (error: unknown) => {
        this.dialogError.set(taskErrorMessage(error, 'Impossible d’enregistrer la tâche.'));
        this.serverFieldErrors.set(readApiError<keyof TaskRequestDTO>(error)?.errors ?? {});
      },
    });
  }

  protected clearFieldError(field: keyof TaskRequestDTO) {
    this.serverFieldErrors.update(errors => ({ ...errors, [field]: undefined }));
  }

  openDeletion(task: TaskResponseDTO) {
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
    this.actionStarted.emit();
    this.api.delete(task.id).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.pending.set(false)),
    ).subscribe({
      next: () => {
        this.deleteDialog().nativeElement.close();
        this.deletingTask.set(null);
        this.deleted.emit(task.id);
      },
      error: (error: unknown) => this.dialogError.set(taskErrorMessage(error, 'Impossible de supprimer la tâche.')),
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
}

export const TASK_STATUSES = [
  { value: 'TODO', label: 'À faire', tone: 'todo', icon: 'circle-dashed' },
  { value: 'IN_PROGRESS', label: 'En cours', tone: 'progress', icon: 'hourglass-medium' },
  { value: 'DONE', label: 'Terminée', tone: 'done', icon: 'check-circle' },
] as const;

export type TaskStatus = (typeof TASK_STATUSES)[number]['value'];
export type StatusFilter = TaskStatus | 'ALL';

export interface TaskRequestDTO {
  title: string;
  description: string | null;
  status: TaskStatus;
}

export interface TaskResponseDTO extends Omit<TaskRequestDTO, 'status'> {
  id: number;
  status: TaskStatus | null;
}

export interface TaskApiError {
  status: number;
  message: string;
  errors: Partial<Record<keyof TaskRequestDTO, string[]>>;
}

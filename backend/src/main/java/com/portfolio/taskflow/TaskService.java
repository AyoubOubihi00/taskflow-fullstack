
package com.portfolio.taskflow;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public List<TaskResponseDTO> getAllTasks() {
        return taskRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public TaskResponseDTO getTaskById(Long id) {
        return toResponse(findTask(id));
    }

    public TaskResponseDTO createTask(TaskRequestDTO request) {
        TaskItem task = new TaskItem();
        applyRequest(task, request);
        return toResponse(taskRepository.save(task));
    }

    public TaskResponseDTO updateTask(Long id, TaskRequestDTO request) {
        TaskItem task = findTask(id);
        applyRequest(task, request);
        return toResponse(taskRepository.save(task));
    }

    public void deleteTask(Long id) {
        taskRepository.delete(findTask(id));
    }

    private TaskItem findTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    private void applyRequest(TaskItem task, TaskRequestDTO request) {
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setStatus(TaskItem.Status.valueOf(request.getStatus()));
    }

    private TaskResponseDTO toResponse(TaskItem task) {
        return new TaskResponseDTO(task.getId(), task.getTitle(), task.getDescription(), task.getStatus());
    }
}


package com.portfolio.taskflow.service;

import com.portfolio.taskflow.dto.TaskRequestDTO;
import com.portfolio.taskflow.dto.TaskResponseDTO;
import com.portfolio.taskflow.entity.TaskItem;
import com.portfolio.taskflow.exception.TaskNotFoundException;
import com.portfolio.taskflow.repository.TaskRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public List<TaskResponseDTO> getAllTasks(long ownerId) {
        return taskRepository.findAllByOwnerId(ownerId).stream()
                .map(this::toResponse)
                .toList();
    }

    public TaskResponseDTO getTaskById(long ownerId, Long id) {
        return toResponse(findTask(ownerId, id));
    }

    public TaskResponseDTO createTask(long ownerId, TaskRequestDTO request) {
        TaskItem task = new TaskItem();
        task.setOwnerId(ownerId);
        applyRequest(task, request);
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponseDTO updateTask(long ownerId, Long id, TaskRequestDTO request) {
        TaskItem task = findTask(ownerId, id);
        applyRequest(task, request);
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public void deleteTask(long ownerId, Long id) {
        taskRepository.delete(findTask(ownerId, id));
    }

    private TaskItem findTask(long ownerId, Long id) {
        return taskRepository.findByIdAndOwnerId(id, ownerId)
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

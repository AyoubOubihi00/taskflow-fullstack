
package com.portfolio.taskflow.controller;

import com.portfolio.taskflow.dto.TaskRequestDTO;
import com.portfolio.taskflow.dto.TaskResponseDTO;
import com.portfolio.taskflow.service.TaskService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public List<TaskResponseDTO> getAllTasks(@AuthenticationPrincipal Jwt principal) {
        return taskService.getAllTasks(ownerId(principal));
    }

    @GetMapping("/{id}")
    public TaskResponseDTO getTaskById(@AuthenticationPrincipal Jwt principal, @PathVariable Long id) {
        return taskService.getTaskById(ownerId(principal), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponseDTO createTask(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody TaskRequestDTO request) {
        return taskService.createTask(ownerId(principal), request);
    }

    @PutMapping("/{id}")
    public TaskResponseDTO updateTask(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable Long id,
            @Valid @RequestBody TaskRequestDTO request) {
        return taskService.updateTask(ownerId(principal), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@AuthenticationPrincipal Jwt principal, @PathVariable Long id) {
        taskService.deleteTask(ownerId(principal), id);
    }

    private static long ownerId(Jwt principal) {
        return Long.parseLong(principal.getSubject());
    }
}


package com.portfolio.taskflow;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "http://localhost:4200")
public class TaskController {

    private final TaskRepository taskRepository;

    public TaskController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @GetMapping
    public List<TaskItem> getAllTasks() {
        return taskRepository.findAll();
    }

    @GetMapping("/{id}")
    public TaskItem getTaskById(@PathVariable Long id) {
        return findTaskById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskItem createTask(@Valid @RequestBody TaskItem task) {
        task.setId(null);
        return taskRepository.save(task);
    }

    @PutMapping("/{id}")
    public TaskItem updateTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskItem updatedTask) {

        TaskItem existingTask = findTaskById(id);

        existingTask.setTitle(updatedTask.getTitle());
        existingTask.setDescription(updatedTask.getDescription());
        existingTask.setStatus(updatedTask.getStatus());

        return taskRepository.save(existingTask);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable Long id) {
        TaskItem task = findTaskById(id);
        taskRepository.delete(task);
    }

    private TaskItem findTaskById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Task not found with id: " + id
                ));
    }
}

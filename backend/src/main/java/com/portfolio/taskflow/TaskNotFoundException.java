package com.portfolio.taskflow;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(Long id) {
        super("La tâche " + id + " est introuvable.");
    }
}

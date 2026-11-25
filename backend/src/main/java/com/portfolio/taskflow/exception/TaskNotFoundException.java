package com.portfolio.taskflow.exception;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(Long id) {
        super("La tâche " + id + " est introuvable.");
    }
}

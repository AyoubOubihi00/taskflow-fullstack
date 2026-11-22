package com.portfolio.taskflow;

public record TaskResponseDTO(Long id, String title, String description, TaskItem.Status status) {
}

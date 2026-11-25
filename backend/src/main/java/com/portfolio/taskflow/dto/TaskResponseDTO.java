package com.portfolio.taskflow.dto;

import com.portfolio.taskflow.entity.TaskItem;

public record TaskResponseDTO(Long id, String title, String description, TaskItem.Status status) {
}

package com.portfolio.taskflow.dto;

import com.portfolio.taskflow.entity.TaskItem;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class TaskRequestDTO {

    @NotBlank(message = "Le titre est obligatoire.")
    @Size(max = 120, message = "Le titre ne doit pas dépasser 120 caractères.")
    private String title;

    @Size(max = 2000, message = "La description ne doit pas dépasser 2000 caractères.")
    private String description;

    @NotNull(message = "Le statut ne peut pas être nul.")
    @Pattern(regexp = "TODO|IN_PROGRESS|DONE", message = "Le statut doit être TODO, IN_PROGRESS ou DONE.")
    private String status = TaskItem.Status.TODO.name();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

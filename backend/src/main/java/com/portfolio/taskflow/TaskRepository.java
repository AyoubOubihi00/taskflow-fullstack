package com.portfolio.taskflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<TaskItem, Long> {
    List<TaskItem> findAllByOwnerId(long ownerId);

    Optional<TaskItem> findByIdAndOwnerId(long id, long ownerId);
}

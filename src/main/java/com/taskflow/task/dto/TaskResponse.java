package com.taskflow.task.dto;

import com.taskflow.task.model.Task;
import com.taskflow.task.model.TaskPriority;
import com.taskflow.task.model.TaskStatus;

import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        UUID projectId,
        String projectName,
        UUID createdById,
        String createdByName,
        UUID assigneeId,
        String assigneeName,
        String assigneeEmail,
        Instant createdAt,
        Instant updatedAt
) {
    public TaskResponse(UUID id, String title, String description, TaskStatus status, TaskPriority priority,
                        UUID projectId, String projectName, UUID createdById, String createdByName,
                        Instant createdAt, Instant updatedAt) {
        this(id, title, description, status, priority, projectId, projectName, createdById, createdByName,
                null, null, null, createdAt, updatedAt);
    }

    public static TaskResponse from(Task task) {
        UserInfo assignee = task.getAssignee() == null
                ? null
                : new UserInfo(task.getAssignee().getId(), task.getAssignee().getName(), task.getAssignee().getEmail());
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getStatus(),
                task.getPriority(), task.getProject().getId(), task.getProject().getName(),
                task.getCreatedBy().getId(), task.getCreatedBy().getName(),
                assignee == null ? null : assignee.id(),
                assignee == null ? null : assignee.name(),
                assignee == null ? null : assignee.email(),
                task.getCreatedAt(), task.getUpdatedAt());
    }

    private record UserInfo(UUID id, String name, String email) { }
}
package com.taskflow.task.repository;

import com.taskflow.task.model.Task;
import com.taskflow.task.model.TaskPriority;
import com.taskflow.task.model.TaskStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> textContains(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String pattern = "%" + query.trim().toLowerCase(java.util.Locale.ROOT) + "%";
        return (root, criteriaQuery, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), pattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern)
        );
    }

    public static Specification<Task> belongsToProject(UUID projectId) {
        return projectId == null ? null : (root, query, builder) ->
                builder.equal(root.get("project").get("id"), projectId);
    }

    public static Specification<Task> hasStatus(TaskStatus status) {
        return status == null ? null : (root, query, builder) ->
                builder.equal(root.get("status"), status);
    }

    public static Specification<Task> hasPriority(TaskPriority priority) {
        return priority == null ? null : (root, query, builder) ->
                builder.equal(root.get("priority"), priority);
    }

    public static Specification<Task> assignedTo(UUID assigneeId) {
        return assigneeId == null ? null : (root, query, builder) ->
                builder.equal(root.get("assignee").get("id"), assigneeId);
    }
}
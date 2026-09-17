package com.taskflow.task.controller;

import com.taskflow.task.dto.CreateTaskRequest;
import com.taskflow.task.dto.AssignTaskRequest;
import com.taskflow.task.dto.TaskResponse;
import com.taskflow.task.dto.UpdateTaskRequest;
import com.taskflow.task.dto.UpdateTaskStatusRequest;
import com.taskflow.task.model.TaskPriority;
import com.taskflow.task.model.TaskStatus;
import com.taskflow.task.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/api/projects/{projectId}/tasks")
    public ResponseEntity<TaskResponse> create(
            Authentication authentication,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateTaskRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.createTask(projectId, authentication.getName(), request));
    }

    @GetMapping("/api/projects/{projectId}/tasks")
    public ResponseEntity<List<TaskResponse>> getByProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(taskService.findAllByProject(projectId));
    }

    @GetMapping("/api/tasks/{id}")
    public ResponseEntity<TaskResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(taskService.findById(id));
    }

    @GetMapping("/api/tasks/search")
    public ResponseEntity<Page<TaskResponse>> search(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String q,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String projectId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String status,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String priority,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String assigneeId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "createdAt") String sortBy,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "desc") String direction,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("Page must be at least 0");
        }
        if (size < 1 || size > 50) {
            throw new IllegalArgumentException("Size must be between 1 and 50");
        }
        UUID parsedProjectId = parseUuid(projectId, "projectId");
        UUID parsedAssigneeId = parseUuid(assigneeId, "assigneeId");
        TaskStatus parsedStatus = parseStatus(status);
        TaskPriority parsedPriority = parsePriority(priority);
        Sort sort = Sort.by(parseDirection(direction), parseSortProperty(sortBy));
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(taskService.searchTasks(q, parsedProjectId, parsedStatus,
                parsedPriority, parsedAssigneeId, pageable));
    }

    @PatchMapping("/api/tasks/{id}")
    public ResponseEntity<TaskResponse> update(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskRequest request
    ) {
        return ResponseEntity.ok(taskService.updateTask(id, authentication.getName(), request));
    }

    @PatchMapping("/api/tasks/{id}/assignee")
    public ResponseEntity<TaskResponse> assign(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody AssignTaskRequest request
    ) {
        return ResponseEntity.ok(taskService.assignTask(id, request, authentication.getName()));
    }

    @PatchMapping("/api/tasks/{id}/status")
    public ResponseEntity<TaskResponse> updateStatus(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskStatusRequest request
    ) {
        return ResponseEntity.ok(taskService.updateTaskStatus(id, authentication.getName(), request));
    }

    @DeleteMapping("/api/tasks/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID id) {
        taskService.deleteTask(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    private UUID parseUuid(String value, String parameter) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid " + parameter);
        }
    }

    private TaskStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return TaskStatus.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid status");
        }
    }

    private TaskPriority parsePriority(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return TaskPriority.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid priority");
        }
    }

    private String parseSortProperty(String value) {
        return switch (value) {
            case "createdAt", "updatedAt", "title", "priority", "status" -> value;
            default -> throw new IllegalArgumentException("Invalid sortBy");
        };
    }

    private Sort.Direction parseDirection(String value) {
        try {
            return Sort.Direction.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid direction");
        }
    }
}
package com.taskflow.task.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class AssignTaskRequest {

    @NotNull(message = "Worker ID must be provided")
    private UUID workerId;

    public AssignTaskRequest() {
    }

    public AssignTaskRequest(UUID workerId) {
        this.workerId = workerId;
    }

    public UUID getWorkerId() { return workerId; }
    public void setWorkerId(UUID workerId) { this.workerId = workerId; }
}
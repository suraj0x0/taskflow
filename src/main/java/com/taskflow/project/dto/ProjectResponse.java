package com.taskflow.project.dto;

import com.taskflow.project.model.Project;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String description,
        UUID createdById,
        String createdByName,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getCreatedBy().getId(),
                project.getCreatedBy().getName(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
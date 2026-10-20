package com.taskflow.activity.dto;

import com.taskflow.activity.model.Activity;
import com.taskflow.activity.model.ActivityType;

import java.time.Instant;
import java.util.UUID;

public record ActivityResponse(UUID id, UUID taskId, UUID actorId, String actorName,
                               ActivityType type, String description, Instant createdAt) {
    public static ActivityResponse from(Activity activity) {
        return new ActivityResponse(activity.getId(), activity.getTaskId(), activity.getActorId(),
                activity.getActorName(), activity.getType(), activity.getDescription(), activity.getCreatedAt());
    }
}
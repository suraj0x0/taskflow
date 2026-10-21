package com.taskflow.activity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Index;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "activities",
    indexes = @Index(
        name = "idx_activities_task_created_at",
        columnList = "task_id, created_at"
    )
)
public class Activity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "task_id", nullable = false, updatable = false) private UUID taskId;
    @Column(name = "actor_id", nullable = false, updatable = false) private UUID actorId;
    @Column(name = "actor_name", nullable = false, updatable = false) private String actorName;
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 30) private ActivityType type;
    @Column(name = "description", nullable = false, updatable = false, length = 500) private String description;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public Activity() { }
    public Activity(UUID taskId, UUID actorId, String actorName, ActivityType type, String description) {
        this.taskId = taskId; this.actorId = actorId; this.actorName = actorName;
        this.type = type; this.description = description;
    }
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }
    public UUID getActorId() { return actorId; }
    public void setActorId(UUID actorId) { this.actorId = actorId; }
    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public ActivityType getType() { return type; }
    public void setType(ActivityType type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
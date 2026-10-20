package com.taskflow.activity.service;

import com.taskflow.activity.dto.ActivityResponse;
import com.taskflow.activity.model.Activity;
import com.taskflow.activity.model.ActivityType;
import com.taskflow.activity.repository.ActivityRepository;
import com.taskflow.task.exception.TaskNotFoundException;
import com.taskflow.task.repository.TaskRepository;
import com.taskflow.user.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ActivityService {
    private final ActivityRepository activityRepository;
    private final TaskRepository taskRepository;

    public ActivityService(ActivityRepository activityRepository, TaskRepository taskRepository) {
        this.activityRepository = activityRepository; this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> getByTask(UUID taskId) {
        if (!taskRepository.existsById(taskId)) throw new TaskNotFoundException("Task not found: " + taskId);
        return activityRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(ActivityResponse::from).toList();
    }

    public void record(UUID taskId, User actor, ActivityType type, String description) {
        activityRepository.save(new Activity(taskId, actor.getId(), actor.getName(), type, description));
    }
}
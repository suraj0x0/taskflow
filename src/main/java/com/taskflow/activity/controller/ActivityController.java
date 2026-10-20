package com.taskflow.activity.controller;

import com.taskflow.activity.dto.ActivityResponse;
import com.taskflow.activity.service.ActivityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ActivityController {
    private final ActivityService activityService;
    public ActivityController(ActivityService activityService) { this.activityService = activityService; }
    @GetMapping("/api/tasks/{taskId}/activities")
    public ResponseEntity<List<ActivityResponse>> getByTask(@PathVariable UUID taskId) {
        return ResponseEntity.ok(activityService.getByTask(taskId));
    }
}
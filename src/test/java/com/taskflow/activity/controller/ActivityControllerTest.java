package com.taskflow.activity.controller;

import com.taskflow.activity.dto.ActivityResponse;
import com.taskflow.activity.model.ActivityType;
import com.taskflow.activity.service.ActivityService;
import com.taskflow.security.JwtAuthenticationFilter;
import com.taskflow.security.JwtService;
import com.taskflow.security.config.SecurityConfig;
import com.taskflow.task.exception.TaskNotFoundException;
import com.taskflow.user.repository.UserRepository;
import com.taskflow.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityController.class)
@AutoConfigureMockMvc
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class})
class ActivityControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ActivityService activityService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserRepository userRepository;

    @Test
    @WithMockUser(username = "user@example.com", roles = "WORKER")
    void authenticatedUserCanGetActivitiesForTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ActivityResponse activityResponse = new ActivityResponse(
                activityId, taskId, actorId, "Alice",
                ActivityType.TASK_CREATED, "Task created", Instant.now());

        when(activityService.getByTask(taskId)).thenReturn(List.of(activityResponse));

        mockMvc.perform(get("/api/tasks/{taskId}/activities", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(activityId.toString()))
                .andExpect(jsonPath("$[0].taskId").value(taskId.toString()))
                .andExpect(jsonPath("$[0].actorId").value(actorId.toString()))
                .andExpect(jsonPath("$[0].actorName").value("Alice"))
                .andExpect(jsonPath("$[0].type").value("TASK_CREATED"))
                .andExpect(jsonPath("$[0].description").value("Task created"));
    }

    @Test
    void unauthenticatedUserCannotGetActivities() throws Exception {
        UUID taskId = UUID.randomUUID();
        mockMvc.perform(get("/api/tasks/{taskId}/activities", taskId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "WORKER")
    void missingTaskReturnsNotFound() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(activityService.getByTask(taskId)).thenThrow(new TaskNotFoundException("Task not found: " + taskId));

        mockMvc.perform(get("/api/tasks/{taskId}/activities", taskId))
                .andExpect(status().isNotFound());
    }
}

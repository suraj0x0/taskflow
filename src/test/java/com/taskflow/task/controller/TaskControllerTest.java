package com.taskflow.task.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.security.JwtAuthenticationFilter;
import com.taskflow.security.JwtService;
import com.taskflow.security.config.SecurityConfig;
import com.taskflow.task.dto.AssignTaskRequest;
import com.taskflow.task.dto.CreateTaskRequest;
import com.taskflow.task.dto.TaskResponse;
import com.taskflow.task.dto.UpdateTaskRequest;
import com.taskflow.task.dto.UpdateTaskStatusRequest;
import com.taskflow.task.exception.TaskNotFoundException;
import com.taskflow.task.model.TaskPriority;
import com.taskflow.task.model.TaskStatus;
import com.taskflow.task.service.TaskService;
import com.taskflow.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@AutoConfigureMockMvc
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class})
class TaskControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private TaskService taskService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private com.taskflow.user.repository.UserRepository userRepository;

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void adminCanCreateUpdateAndDeleteTask() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(taskService.createTask(any(UUID.class), any(String.class), any(CreateTaskRequest.class)))
                .thenReturn(response(taskId, projectId));
        when(taskService.updateTask(any(UUID.class), any(String.class), any(UpdateTaskRequest.class)))
                .thenReturn(response(taskId, projectId));

        mockMvc.perform(post("/api/projects/{projectId}/tasks", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTaskRequest("Task", null, TaskPriority.HIGH))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mockMvc.perform(patch("/api/tasks/{id}", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTaskRequest("Updated", null, TaskStatus.DONE, null))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/tasks/{id}", taskId)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "manager@example.com", roles = "MANAGER")
    void managerCanCreateUpdateAndDeleteTask() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(taskService.createTask(any(UUID.class), any(String.class), any(CreateTaskRequest.class)))
                .thenReturn(response(taskId, projectId));
        when(taskService.updateTask(any(UUID.class), any(String.class), any(UpdateTaskRequest.class)))
                .thenReturn(response(taskId, projectId));

        mockMvc.perform(post("/api/projects/{projectId}/tasks", projectId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTaskRequest("Task", null, TaskPriority.LOW))))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/api/tasks/{id}", taskId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTaskRequest("Updated", null, null, null))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/tasks/{id}", taskId)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "worker@example.com", roles = "WORKER")
    void workerCannotCreateUpdateOrDeleteTask() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        mockMvc.perform(post("/api/projects/{projectId}/tasks", projectId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTaskRequest("Task", null, TaskPriority.LOW))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/tasks/{id}", taskId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTaskRequest("Updated", null, null, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/tasks/{id}", taskId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WORKER")
    void authenticatedUserCanListAndGetTask() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(taskService.findAllByProject(projectId)).thenReturn(List.of(response(taskId, projectId)));
        when(taskService.findById(taskId)).thenReturn(response(taskId, projectId));

        mockMvc.perform(get("/api/projects/{projectId}/tasks", projectId)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        mockMvc.perform(get("/api/tasks/{id}", taskId)).andExpect(status().isOk());
    }

    @Test
    void unauthenticatedUserCannotAccessTasks() throws Exception {
        mockMvc.perform(get("/api/tasks/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/projects/{projectId}/tasks", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidTitleAndMissingTaskReturnExpectedErrors() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(taskService.findById(taskId)).thenThrow(new TaskNotFoundException("Task not found"));

        mockMvc.perform(post("/api/projects/{projectId}/tasks", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTaskRequest("", null, TaskPriority.LOW))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tasks/{id}", taskId)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void adminCanAssignTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        when(taskService.assignTask(any(UUID.class), any(AssignTaskRequest.class), any(String.class)))
                .thenReturn(new TaskResponse(taskId, "Task", "Description", TaskStatus.TODO, TaskPriority.MEDIUM,
                        UUID.randomUUID(), "Project", UUID.randomUUID(), "Admin", workerId, "Worker",
                        "worker@example.com", Instant.now(), Instant.now()));

        mockMvc.perform(patch("/api/tasks/{id}/assignee", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignTaskRequest(workerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(workerId.toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(username = "manager@example.com", roles = "MANAGER")
    void managerCanAssignTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(taskService.assignTask(any(UUID.class), any(AssignTaskRequest.class), any(String.class)))
                .thenReturn(response(taskId, UUID.randomUUID()));

        mockMvc.perform(patch("/api/tasks/{id}/assignee", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignTaskRequest(UUID.randomUUID()))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "worker@example.com", roles = "WORKER")
    void workerCannotAssignTask() throws Exception {
        mockMvc.perform(patch("/api/tasks/{id}/assignee", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignTaskRequest(UUID.randomUUID()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "worker@example.com", roles = "WORKER")
    void assignedWorkerCanUpdateStatus() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(taskService.updateTaskStatus(any(UUID.class), any(String.class), any(UpdateTaskStatusRequest.class)))
                .thenReturn(response(taskId, UUID.randomUUID()));

        mockMvc.perform(patch("/api/tasks/{id}/status", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTaskStatusRequest(TaskStatus.IN_PROGRESS))))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedWorkerStatusUpdateIsRejected() throws Exception {
        mockMvc.perform(patch("/api/tasks/{id}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTaskStatusRequest(TaskStatus.IN_PROGRESS))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void assignmentValidationReturnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/tasks/{id}/assignee", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.workerId").exists());
    }

    private TaskResponse response(UUID taskId, UUID projectId) {
        return new TaskResponse(taskId, "Task", "Description", TaskStatus.TODO, TaskPriority.MEDIUM,
                projectId, "Project", UUID.randomUUID(), "Admin", Instant.now(), Instant.now());
    }
}

package com.taskflow.task.controller;

import com.taskflow.security.JwtAuthenticationFilter;
import com.taskflow.security.JwtService;
import com.taskflow.security.config.SecurityConfig;
import com.taskflow.task.dto.TaskResponse;
import com.taskflow.task.model.TaskPriority;
import com.taskflow.task.model.TaskStatus;
import com.taskflow.task.service.TaskService;
import com.taskflow.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@AutoConfigureMockMvc
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class})
class TaskSearchControllerTest {

        @Autowired private MockMvc mockMvc;
    @MockitoBean private TaskService taskService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private com.taskflow.user.repository.UserRepository userRepository;

    @Test
    @WithMockUser(roles = "WORKER")
    void authenticatedUserCanSearchWithFiltersAndPagination() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        TaskResponse response = new TaskResponse(UUID.randomUUID(), "Login API", "Login flow",
                TaskStatus.IN_PROGRESS, TaskPriority.HIGH, projectId, "Project", UUID.randomUUID(),
                "Creator", assigneeId, "Worker", "worker@example.com", Instant.now(), Instant.now());
        when(taskService.searchTasks(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 10,
                        Sort.by(Sort.Direction.DESC, "createdAt")), 1));

        mockMvc.perform(get("/api/tasks/search")
                        .param("q", "login")
                        .param("projectId", projectId.toString())
                        .param("status", "IN_PROGRESS")
                        .param("priority", "HIGH")
                        .param("assigneeId", assigneeId.toString())
                        .param("sortBy", "createdAt")
                        .param("direction", "desc")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Login API"))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "WORKER")
    void searchUsesDefaultsWhenOptionalParametersAreMissing() throws Exception {
        when(taskService.searchTasks(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/tasks/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void unauthenticatedUserCannotSearch() throws Exception {
        mockMvc.perform(get("/api/tasks/search")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "WORKER")
    void invalidSearchParametersReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/tasks/search").param("projectId", "invalid"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tasks/search").param("status", "INVALID"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tasks/search").param("priority", "INVALID"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tasks/search").param("sortBy", "secretField"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tasks/search").param("direction", "sideways"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tasks/search").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tasks/search").param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tasks/search").param("size", "51"))
                .andExpect(status().isBadRequest());
    }
}

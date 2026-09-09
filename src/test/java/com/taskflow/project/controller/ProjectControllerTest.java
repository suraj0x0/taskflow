package com.taskflow.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.project.dto.CreateProjectRequest;
import com.taskflow.project.dto.ProjectResponse;
import com.taskflow.project.dto.UpdateProjectRequest;
import com.taskflow.project.exception.ProjectNotFoundException;
import com.taskflow.project.service.ProjectService;
import com.taskflow.security.JwtAuthenticationFilter;
import com.taskflow.security.JwtService;
import com.taskflow.security.config.SecurityConfig;
import com.taskflow.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
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

@WebMvcTest(ProjectController.class)
@AutoConfigureMockMvc
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class})
class ProjectControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private ProjectService projectService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private com.taskflow.user.repository.UserRepository userRepository;

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void adminCanCreateUpdateAndDeleteProject() throws Exception {
        UUID id = UUID.randomUUID();
        ProjectResponse response = response(id);
        when(projectService.createProject(any(String.class), any(CreateProjectRequest.class))).thenReturn(response);
        when(projectService.updateProject(any(UUID.class), any(String.class), any(UpdateProjectRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(
                        objectMapper.writeValueAsString(new CreateProjectRequest("Project", "Description"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdById").value(response.createdById().toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mockMvc.perform(patch("/api/projects/{id}", id).contentType(MediaType.APPLICATION_JSON).content(
                        objectMapper.writeValueAsString(new UpdateProjectRequest("Updated", null))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/projects/{id}", id)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "manager@example.com", roles = "MANAGER")
    void managerCanCreateUpdateAndDeleteProject() throws Exception {
        UUID id = UUID.randomUUID();
        ProjectResponse response = response(id);
        when(projectService.createProject(any(String.class), any(CreateProjectRequest.class))).thenReturn(response);
        when(projectService.updateProject(any(UUID.class), any(String.class), any(UpdateProjectRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(
                        objectMapper.writeValueAsString(new CreateProjectRequest("Project", null))))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/api/projects/{id}", id).contentType(MediaType.APPLICATION_JSON).content(
                        objectMapper.writeValueAsString(new UpdateProjectRequest("Updated", null))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/projects/{id}", id)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "worker@example.com", roles = "WORKER")
    void workerCannotCreateUpdateOrDeleteProject() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(
                        objectMapper.writeValueAsString(new CreateProjectRequest("Project", null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/projects/{id}", id).contentType(MediaType.APPLICATION_JSON).content(
                        objectMapper.writeValueAsString(new UpdateProjectRequest("Updated", null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/projects/{id}", id)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WORKER")
    void authenticatedUserCanListAndGetProject() throws Exception {
        UUID id = UUID.randomUUID();
        when(projectService.findAll()).thenReturn(List.of(response(id)));
        when(projectService.findById(id)).thenReturn(response(id));

        mockMvc.perform(get("/api/projects")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        mockMvc.perform(get("/api/projects/{id}", id)).andExpect(status().isOk());
    }

    @Test
    void unauthenticatedUserCannotAccessProjects() throws Exception {
        mockMvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(
                        objectMapper.writeValueAsString(new CreateProjectRequest("Project", null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidNameAndMissingProjectAreHandled() throws Exception {
        UUID id = UUID.randomUUID();
        when(projectService.findById(id)).thenThrow(new ProjectNotFoundException("Project not found"));

        mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(
                        objectMapper.writeValueAsString(new CreateProjectRequest("", null))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/projects/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "WORKER")
    void serviceAuthorizationFailuresBecomeForbidden() throws Exception {
        UUID id = UUID.randomUUID();
        when(projectService.findAll()).thenReturn(List.of());
        when(projectService.findById(id)).thenReturn(response(id));
        when(projectService.updateProject(any(UUID.class), any(String.class), any(UpdateProjectRequest.class)))
                .thenThrow(new AccessDeniedException("Forbidden"));

        mockMvc.perform(patch("/api/projects/{id}", id).contentType(MediaType.APPLICATION_JSON).content(
                        objectMapper.writeValueAsString(new UpdateProjectRequest("Updated", null))))
                .andExpect(status().isForbidden());
    }

    private ProjectResponse response(UUID id) {
        return new ProjectResponse(id, "Project", "Description", UUID.randomUUID(), "Admin",
                Instant.now(), Instant.now());
    }
}
package com.taskflow.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.auth.dto.UserResponse;
import com.taskflow.auth.exception.DuplicateEmailException;
import com.taskflow.security.JwtAuthenticationFilter;
import com.taskflow.security.JwtService;
import com.taskflow.security.config.SecurityConfig;
import com.taskflow.user.dto.ChangePasswordRequest;
import com.taskflow.user.dto.CreateUserRequest;
import com.taskflow.user.dto.UpdateProfileRequest;
import com.taskflow.user.dto.UpdateRoleRequest;
import com.taskflow.user.dto.UpdateUserRequest;
import com.taskflow.user.exception.InvalidCurrentPasswordException;
import com.taskflow.user.exception.ProtectedUserOperationException;
import com.taskflow.user.exception.UserNotFoundException;
import com.taskflow.user.model.Role;
import com.taskflow.user.repository.UserRepository;
import com.taskflow.user.service.UserService;
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

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class})
class UserControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private UserService userService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserRepository userRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanListAndCreateUsers() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.findAll()).thenReturn(List.of(new UserResponse(id, "Worker", "worker@example.com", Role.WORKER)));
        when(userService.create(any(CreateUserRequest.class)))
                .thenReturn(new UserResponse(id, "Worker", "worker@example.com", Role.WORKER));

        mockMvc.perform(get("/api/users")).andExpect(status().isOk()).andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                        new CreateUserRequest("Worker", "worker@example.com", "Password123!", Role.WORKER))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void managerCanListAndGetUserById() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.findAll()).thenReturn(List.of());
        when(userService.findById(id)).thenReturn(new UserResponse(id, "Worker", "worker@example.com", Role.WORKER));
        mockMvc.perform(get("/api/users")).andExpect(status().isOk());
        mockMvc.perform(get("/api/users/{id}", id)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WORKER")
    void workerCannotListOrGetArbitraryUser() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/users")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/{id}", id)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "worker@example.com", roles = "WORKER")
    void authenticatedUserCanGetAndUpdateOwnProfile() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.findCurrentUser("worker@example.com"))
                .thenReturn(new UserResponse(id, "Worker", "worker@example.com", Role.WORKER));
        when(userService.updateCurrentUser(any(String.class), any(UpdateProfileRequest.class)))
                .thenReturn(new UserResponse(id, "Updated", "updated@example.com", Role.WORKER));

        mockMvc.perform(get("/api/users/me")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/users/me").contentType(MediaType.APPLICATION_JSON).content(
                        objectMapper.writeValueAsString(new UpdateProfileRequest("Updated", "updated@example.com"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(username = "worker@example.com", roles = "WORKER")
    void userCanChangePasswordButPasswordIsNeverReturned() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.changePassword(any(String.class), any(ChangePasswordRequest.class)))
                .thenReturn(new UserResponse(id, "Worker", "worker@example.com", Role.WORKER));
        mockMvc.perform(patch("/api/users/me/password").contentType(MediaType.APPLICATION_JSON).content(
                        objectMapper.writeValueAsString(new ChangePasswordRequest("old", "new-password"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void adminCanUpdateRoleAndDeleteAnotherUser() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.update(any(UUID.class), any(UpdateUserRequest.class)))
                .thenReturn(new UserResponse(id, "Updated", "updated@example.com", Role.WORKER));
        when(userService.changeRole(any(String.class), any(UUID.class), any(UpdateRoleRequest.class)))
                .thenReturn(new UserResponse(id, "Updated", "updated@example.com", Role.MANAGER));

        mockMvc.perform(patch("/api/users/{id}", id).contentType(MediaType.APPLICATION_JSON).content(
                        objectMapper.writeValueAsString(new UpdateUserRequest("Updated", "updated@example.com"))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/users/{id}/role", id).contentType(MediaType.APPLICATION_JSON).content(
                        objectMapper.writeValueAsString(new UpdateRoleRequest(Role.MANAGER))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/users/{id}", id)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void managerCannotMutateUsers() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(patch("/api/users/{id}/role", id).contentType(MediaType.APPLICATION_JSON).content(
                        objectMapper.writeValueAsString(new UpdateRoleRequest(Role.WORKER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserIsRejected() throws Exception {
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void mapsUserErrorsToExpectedStatuses() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.findById(id)).thenThrow(new UserNotFoundException("User not found"));
        when(userService.create(any(CreateUserRequest.class)))
                .thenThrow(new DuplicateEmailException("Email is already registered"));
        when(userService.changePassword(any(String.class), any(ChangePasswordRequest.class)))
                .thenThrow(new InvalidCurrentPasswordException("Current password is invalid"));
        when(userService.changeRole(any(String.class), any(UUID.class), any(UpdateRoleRequest.class)))
                .thenThrow(new ProtectedUserOperationException("Protected operation"));

        mockMvc.perform(get("/api/users/{id}", id)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                        new CreateUserRequest("Name", "email@example.com", "Password123!", Role.WORKER))))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/users/me/password").contentType(MediaType.APPLICATION_JSON).content(
                        objectMapper.writeValueAsString(new ChangePasswordRequest("old", "new-password"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/users/{id}/role", id).contentType(MediaType.APPLICATION_JSON).content(
                        objectMapper.writeValueAsString(new UpdateRoleRequest(Role.WORKER))))
                .andExpect(status().isForbidden());
    }
}

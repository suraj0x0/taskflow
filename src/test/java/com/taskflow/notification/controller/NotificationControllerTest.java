package com.taskflow.notification.controller;

import com.taskflow.notification.dto.NotificationResponse;
import com.taskflow.notification.exception.NotificationNotFoundException;
import com.taskflow.notification.model.NotificationType;
import com.taskflow.notification.service.NotificationService;
import com.taskflow.security.JwtAuthenticationFilter;
import com.taskflow.security.JwtService;
import com.taskflow.security.config.SecurityConfig;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class})
class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserRepository userRepository;

    @Test
    @WithMockUser(username = "worker@example.com", roles = "WORKER")
    void authenticatedUserCanListNotificationsAndUnreadCount() throws Exception {
        UUID notificationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(notificationService.getNotifications("worker@example.com"))
                .thenReturn(List.of(response(notificationId, taskId, false)));
        when(notificationService.getUnreadCount("worker@example.com")).thenReturn(3L);

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$[0].message").value("Task assigned"))
                .andExpect(jsonPath("$[0].type").value("TASK_ASSIGNED"))
                .andExpect(jsonPath("$[0].taskId").value(taskId.toString()))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].recipient").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist());

        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3));
    }

    @Test
    @WithMockUser(username = "worker@example.com", roles = "WORKER")
    void listingNotificationsReturnsEmptyArrayWhenNoneExist() throws Exception {
        when(notificationService.getNotifications("worker@example.com")).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(username = "worker@example.com", roles = "WORKER")
    void authenticatedUserCanMarkNotificationRead() throws Exception {
        UUID notificationId = UUID.randomUUID();
        NotificationResponse response = response(notificationId, UUID.randomUUID(), true);
        when(notificationService.markAsRead(notificationId, "worker@example.com")).thenReturn(response);

        mockMvc.perform(patch("/api/notifications/{id}/read", notificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificationId.toString()))
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    @WithMockUser(username = "worker@example.com", roles = "WORKER")
    void authenticatedUserCanMarkAllNotificationsRead() throws Exception {
        mockMvc.perform(patch("/api/notifications/read-all"))
                .andExpect(status().isNoContent())
                .andExpect(content -> org.junit.jupiter.api.Assertions.assertEquals("", content.getResponse().getContentAsString()));

        verify(notificationService).markAllAsRead("worker@example.com");
    }

    @Test
    void allNotificationEndpointsRequireAuthentication() throws Exception {
        UUID notificationId = UUID.randomUUID();
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/notifications/{id}/read", notificationId)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/notifications/read-all")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "worker@example.com", roles = "WORKER")
    void foreignOwnedNotificationCannotBeModified() throws Exception {
        UUID notificationId = UUID.randomUUID();
        when(notificationService.markAsRead(notificationId, "worker@example.com"))
                .thenThrow(new NotificationNotFoundException("Notification not found"));

        mockMvc.perform(patch("/api/notifications/{id}/read", notificationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Notification not found"));
    }

    private NotificationResponse response(UUID id, UUID taskId, boolean read) {
        return new NotificationResponse(id, "Task assigned", NotificationType.TASK_ASSIGNED,
                taskId, read, Instant.parse("2026-01-01T10:00:00Z"));
    }
}

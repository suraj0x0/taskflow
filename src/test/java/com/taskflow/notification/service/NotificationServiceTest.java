package com.taskflow.notification.service;

import com.taskflow.notification.dto.NotificationResponse;
import com.taskflow.notification.exception.NotificationNotFoundException;
import com.taskflow.notification.model.Notification;
import com.taskflow.notification.model.NotificationType;
import com.taskflow.notification.repository.NotificationRepository;
import com.taskflow.user.exception.UserNotFoundException;
import com.taskflow.user.model.Role;
import com.taskflow.user.model.User;
import com.taskflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private NotificationService notificationService;

    @Test
    void listsNotificationsForAuthenticatedUserOnly() {
        User recipient = user("recipient@example.com");
        Notification notification = notification(recipient, "Assigned", NotificationType.TASK_ASSIGNED);
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipient));
        when(notificationRepository.findByRecipient_IdOrderByCreatedAtDesc(recipient.getId()))
                .thenReturn(List.of(notification));

        List<NotificationResponse> response = notificationService.getNotifications(" RECIPIENT@EXAMPLE.COM ");

        assertEquals(1, response.size());
        assertEquals(notification.getId(), response.get(0).id());
        verify(notificationRepository).findByRecipient_IdOrderByCreatedAtDesc(recipient.getId());
    }

    @Test
    void returnsUnreadCountForAuthenticatedUser() {
        User recipient = user("recipient@example.com");
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipient));
        when(notificationRepository.countByRecipient_IdAndReadFalse(recipient.getId())).thenReturn(3L);

        assertEquals(3L, notificationService.getUnreadCount("recipient@example.com"));
        verify(notificationRepository).countByRecipient_IdAndReadFalse(recipient.getId());
    }

    @Test
    void marksOwnedNotificationAsRead() {
        User recipient = user("recipient@example.com");
        Notification notification = notification(recipient, "Assigned", NotificationType.TASK_ASSIGNED);
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipient));
        when(notificationRepository.findByIdAndRecipient_Id(notification.getId(), recipient.getId()))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        NotificationResponse response = notificationService.markAsRead(notification.getId(), "recipient@example.com");

        assertTrue(response.read());
        assertTrue(notification.isRead());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markingAlreadyReadNotificationIsIdempotent() {
        User recipient = user("recipient@example.com");
        Notification notification = notification(recipient, "Assigned", NotificationType.TASK_ASSIGNED);
        notification.setRead(true);
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipient));
        when(notificationRepository.findByIdAndRecipient_Id(notification.getId(), recipient.getId()))
                .thenReturn(Optional.of(notification));

        NotificationResponse response = notificationService.markAsRead(notification.getId(), "recipient@example.com");

        assertTrue(response.read());
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void marksAllCurrentUsersUnreadNotificationsAsRead() {
        User recipient = user("recipient@example.com");
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipient));

        notificationService.markAllAsRead("recipient@example.com");

        verify(notificationRepository).markUnreadAsReadByRecipientId(recipient.getId());
    }

    @Test
    void markingAllAsReadSucceedsWhenThereAreNoUnreadNotifications() {
        User recipient = user("recipient@example.com");
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipient));
        when(notificationRepository.markUnreadAsReadByRecipientId(recipient.getId())).thenReturn(0);

        notificationService.markAllAsRead("recipient@example.com");

        verify(notificationRepository).markUnreadAsReadByRecipientId(recipient.getId());
    }

    @Test
    void foreignOwnedNotificationIsNotFound() {
        User recipient = user("recipient@example.com");
        UUID notificationId = UUID.randomUUID();
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipient));
        when(notificationRepository.findByIdAndRecipient_Id(notificationId, recipient.getId()))
                .thenReturn(Optional.empty());

        assertThrows(NotificationNotFoundException.class,
                () -> notificationService.markAsRead(notificationId, "recipient@example.com"));
        verify(notificationRepository, never()).findById(notificationId);
    }

    @Test
    void unknownNotificationIsNotFound() {
        User recipient = user("recipient@example.com");
        UUID notificationId = UUID.randomUUID();
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipient));
        when(notificationRepository.findByIdAndRecipient_Id(notificationId, recipient.getId()))
                .thenReturn(Optional.empty());

        assertThrows(NotificationNotFoundException.class,
                () -> notificationService.markAsRead(notificationId, "recipient@example.com"));
    }

    @Test
    void createsNotificationWithExpectedRecipientAndDetails() {
        User recipient = user("recipient@example.com");
        UUID taskId = UUID.randomUUID();
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(UUID.randomUUID());
            notification.setCreatedAt(Instant.parse("2026-01-01T10:00:00Z"));
            return notification;
        });

        NotificationResponse response = notificationService.createNotification(
                recipient, taskId, NotificationType.TASK_ASSIGNED, "You have been assigned a task: Task");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertEquals(recipient, saved.getRecipient());
        assertEquals("You have been assigned a task: Task", saved.getMessage());
        assertEquals(NotificationType.TASK_ASSIGNED, saved.getType());
        assertEquals(taskId, saved.getTaskId());
        assertFalse(saved.isRead());
        assertEquals(saved.getId(), response.id());
        assertEquals(taskId, response.taskId());
    }

    @Test
    void missingAuthenticatedUserFailsClearly() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> notificationService.getUnreadCount("missing@example.com"));
        verify(notificationRepository, never()).countByRecipient_IdAndReadFalse(any());
    }

    private User user(String email) {
        User user = new User("Recipient", email, "hash", Role.WORKER);
        user.setId(UUID.randomUUID());
        return user;
    }

    private Notification notification(User recipient, String message, NotificationType type) {
        Notification notification = new Notification(recipient, message, type, UUID.randomUUID());
        notification.setId(UUID.randomUUID());
        notification.setCreatedAt(Instant.parse("2026-01-01T10:00:00Z"));
        return notification;
    }
}

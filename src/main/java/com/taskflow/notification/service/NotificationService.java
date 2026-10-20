package com.taskflow.notification.service;

import com.taskflow.notification.dto.NotificationResponse;
import com.taskflow.notification.exception.NotificationNotFoundException;
import com.taskflow.notification.model.Notification;
import com.taskflow.notification.model.NotificationType;
import com.taskflow.notification.repository.NotificationRepository;
import com.taskflow.user.exception.UserNotFoundException;
import com.taskflow.user.model.User;
import com.taskflow.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(String authenticatedEmail) {
        User recipient = findUser(authenticatedEmail);
        return notificationRepository.findByRecipient_IdOrderByCreatedAtDesc(recipient.getId())
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String authenticatedEmail) {
        User recipient = findUser(authenticatedEmail);
        return notificationRepository.countByRecipient_IdAndReadFalse(recipient.getId());
    }

    @Transactional
    public NotificationResponse markAsRead(UUID notificationId, String authenticatedEmail) {
        User recipient = findUser(authenticatedEmail);
        Notification notification = notificationRepository
                .findByIdAndRecipient_Id(notificationId, recipient.getId())
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found"));
        if (!notification.isRead()) {
            notification.setRead(true);
            notificationRepository.save(notification);
        }
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllAsRead(String authenticatedEmail) {
        User recipient = findUser(authenticatedEmail);
        notificationRepository.markUnreadAsReadByRecipientId(recipient.getId());
    }

    @Transactional
    public NotificationResponse createNotification(User recipient, UUID taskId,
                                                   NotificationType type, String message) {
        Notification notification = notificationRepository.save(
                new Notification(recipient, message, type, taskId));
        return NotificationResponse.from(notification);
    }

    private User findUser(String authenticatedEmail) {
        return userRepository.findByEmail(authenticatedEmail.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }
}

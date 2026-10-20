package com.taskflow.notification.repository;

import com.taskflow.notification.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByRecipient_IdOrderByCreatedAtDesc(UUID recipientId);

    long countByRecipient_IdAndReadFalse(UUID recipientId);

    Optional<Notification> findByIdAndRecipient_Id(UUID id, UUID recipientId);

    List<Notification> findByRecipient_IdAndReadFalseOrderByCreatedAtDesc(UUID recipientId);

    @Modifying
    @Query("update Notification notification set notification.read = true "
            + "where notification.recipient.id = :recipientId and notification.read = false")
    int markUnreadAsReadByRecipientId(@Param("recipientId") UUID recipientId);
}

package com.aicampus.delivery.service;

import com.aicampus.common.dto.NotificationMessage;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class NotificationCenterService {
    private final ConcurrentMap<String, NotificationMessage> notifications = new ConcurrentHashMap<>();
    private final DeliveryEventPublisher eventPublisher;

    public NotificationCenterService(DeliveryEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDefaultNotifications() {
        seed(new NotificationMessage(
                "N-DEMO-STUDENT-001",
                "STUDENT",
                "S001",
                "Interview invitation",
                "C001 invited you to a Java backend interview. Confirm the schedule before the deadline.",
                "INTERVIEW",
                "D003",
                false,
                LocalDateTime.now().minusHours(5)));
        seed(new NotificationMessage(
                "N-DEMO-COMPANY-001",
                "COMPANY",
                "C001",
                "New candidate delivery",
                "A new resume was delivered to J001 and is waiting for screening.",
                "DELIVERY",
                "D001",
                false,
                LocalDateTime.now().minusHours(3)));
    }

    public NotificationMessage create(
            String targetRole,
            String targetUserId,
            String title,
            String content,
            String sourceType,
            String sourceId) {
        NotificationMessage message = new NotificationMessage(
                "N-" + UUID.randomUUID().toString().substring(0, 8),
                valueOr(targetRole, "STUDENT").toUpperCase(),
                valueOr(targetUserId, ""),
                valueOr(title, "Notification"),
                valueOr(content, ""),
                valueOr(sourceType, "SYSTEM"),
                valueOr(sourceId, ""),
                false,
                LocalDateTime.now());
        notifications.put(message.notificationId(), message);
        eventPublisher.publishLifecycleEvent(
                "NOTIFICATION_CREATED",
                message.sourceId(),
                "STUDENT".equals(message.targetRole()) ? message.targetUserId() : "",
                "",
                "",
                "COMPANY".equals(message.targetRole()) ? message.targetUserId() : "",
                null);
        return message;
    }

    public List<NotificationMessage> listFor(String targetRole, String targetUserId) {
        String role = valueOr(targetRole, "").toUpperCase();
        String userId = valueOr(targetUserId, "");
        return notifications.values().stream()
                .filter(message -> role.isBlank() || role.equalsIgnoreCase(message.targetRole()))
                .filter(message -> userId.isBlank() || userId.equals(message.targetUserId()))
                .sorted(Comparator.comparing(NotificationMessage::createdAt).reversed())
                .toList();
    }

    public NotificationMessage markRead(String notificationId, String targetRole, String targetUserId) {
        NotificationMessage current = notifications.get(notificationId);
        if (current == null || !canAccess(current, targetRole, targetUserId)) {
            return null;
        }
        NotificationMessage updated = new NotificationMessage(
                current.notificationId(),
                current.targetRole(),
                current.targetUserId(),
                current.title(),
                current.content(),
                current.sourceType(),
                current.sourceId(),
                true,
                current.createdAt());
        notifications.put(updated.notificationId(), updated);
        return updated;
    }

    private void seed(NotificationMessage message) {
        notifications.putIfAbsent(message.notificationId(), message);
    }

    private boolean canAccess(NotificationMessage message, String targetRole, String targetUserId) {
        String role = valueOr(targetRole, "");
        String userId = valueOr(targetUserId, "");
        if (role.isBlank() && userId.isBlank()) {
            return true;
        }
        if (!role.isBlank() && !role.equalsIgnoreCase(message.targetRole())) {
            return false;
        }
        return userId.isBlank() || userId.equals(message.targetUserId());
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

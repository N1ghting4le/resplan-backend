package com.resplan.api.dto;

import com.resplan.domain.Notification;

import java.time.Instant;

public record NotificationDto(long id, String message, Instant createdAt, boolean read) {

    public static NotificationDto of(Notification n) {
        return new NotificationDto(n.getId(), n.getMessage(), n.getCreatedAt(), n.isRead());
    }
}

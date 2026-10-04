package com.resplan.service;

import com.resplan.domain.AppUser;
import com.resplan.domain.Notification;
import com.resplan.error.NotFoundException;
import com.resplan.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notifications;

    @Transactional(readOnly = true)
    public List<Notification> list(AppUser user, boolean unreadOnly) {
        return unreadOnly
                ? notifications.findByRecipientAndReadFalseOrderByIdDesc(user)
                : notifications.findByRecipientOrderByIdDesc(user);
    }

    /** Чужое уведомление для пользователя не существует (404), чтобы не раскрывать идентификаторы */
    public Notification markRead(long notificationId, AppUser user) {
        Notification n = notifications.findById(notificationId)
                .filter(x -> x.isAddressedTo(user))
                .orElseThrow(() -> new NotFoundException("Уведомление", notificationId));
        n.markRead();
        return n;
    }
}

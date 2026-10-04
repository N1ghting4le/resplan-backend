package com.resplan.repository;

import com.resplan.domain.AppUser;
import com.resplan.domain.Notification;

import java.util.List;

public interface NotificationRepository extends BaseRepository<Notification, Long> {

    List<Notification> findByRecipientOrderByIdDesc(AppUser recipient);

    List<Notification> findByRecipientAndReadFalseOrderByIdDesc(AppUser recipient);
}

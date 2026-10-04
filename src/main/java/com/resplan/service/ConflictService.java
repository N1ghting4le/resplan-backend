package com.resplan.service;

import com.resplan.domain.AppUser;
import com.resplan.domain.Booking;
import com.resplan.domain.ConflictStatus;
import com.resplan.domain.ResourceConflict;
import com.resplan.event.ConflictEscalatedEvent;
import com.resplan.event.ConflictResolvedEvent;
import com.resplan.repository.BookingRepository;
import com.resplan.repository.ResourceConflictRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

/** UC-6: разрешение ресурсных конфликтов (Double Booking) ресурсным менеджером */
@Service
@RequiredArgsConstructor
@Transactional
public class ConflictService {

    private final ResourceConflictRepository conflicts;
    private final BookingRepository bookings;
    private final BookingLifecycle lifecycle;
    private final ApplicationEventPublisher events;

    /** FR6-1: панель конфликтов; по умолчанию – открытые и эскалированные */
    @Transactional(readOnly = true)
    public List<ResourceConflict> list(Collection<ConflictStatus> statuses) {
        Collection<ConflictStatus> filter = statuses == null || statuses.isEmpty()
                ? EnumSet.of(ConflictStatus.OPEN, ConflictStatus.ESCALATED) : statuses;
        return conflicts.findByStatusInOrderById(filter);
    }

    @Transactional(readOnly = true)
    public ResourceConflict get(int conflictId) {
        return conflicts.require(conflictId, "Конфликт");
    }

    /** FR6-2, FR6-3: одна бронь сохраняется, вторая снимается, PM обоих проектов получают уведомление */
    public ResourceConflict resolve(int conflictId, long keepBookingId, String note, AppUser rm) {
        ResourceConflict conflict = conflicts.require(conflictId, "Конфликт");
        Booking kept = bookings.require(keepBookingId, "Бронирование");
        Booking released = conflict.other(kept);
        String resolution = note == null || note.isBlank() ? "Сохранена бронь " + kept.title() : note.trim();
        conflict.resolve(resolution, rm);
        lifecycle.release(released, rm, resolution);
        events.publishEvent(new ConflictResolvedEvent(conflict, kept, released));
        return conflict;
    }

    /** FR6-4: эскалация неразрешимого конфликта на уровень операционного директора */
    public ResourceConflict escalate(int conflictId, String note) {
        ResourceConflict conflict = conflicts.require(conflictId, "Конфликт");
        conflict.escalate(note);
        events.publishEvent(new ConflictEscalatedEvent(conflict));
        return conflict;
    }
}

package com.resplan.service;

import com.resplan.domain.AppUser;
import com.resplan.domain.Booking;
import com.resplan.domain.ProjectBooking;
import com.resplan.domain.ResourceConflict;
import com.resplan.domain.ResourceRequest;
import com.resplan.event.BookingReleasedEvent;
import com.resplan.event.ConflictDetectedEvent;
import com.resplan.repository.ResourceConflictRepository;
import com.resplan.repository.ResourceRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Побочные эффекты жизненного цикла брони, общие для UC-3, UC-6, UC-7 и UC-9:
 * регистрация ресурсных конфликтов и снятие брони. Логика описана в одном месте (DRY).
 */
@Component
@RequiredArgsConstructor
public class BookingLifecycle {

    private final ResourceConflictRepository conflicts;
    private final ResourceRequestRepository requests;
    private final ApplicationEventPublisher events;

    /** Регистрирует конфликт для каждой перегрузки (BR-08); уже известные пары пропускаются */
    public List<ResourceConflict> registerConflicts(Booking booking, List<Booking> overloads) {
        List<ResourceConflict> registered = new ArrayList<>();
        for (Booking other : overloads) {
            ResourceConflict conflict = new ResourceConflict(booking, other);
            if (conflicts.existsByBookingAAndBookingB(conflict.getBookingA(), conflict.getBookingB())) continue;
            registered.add(conflicts.save(conflict));
            events.publishEvent(new ConflictDetectedEvent(conflict));
        }
        return registered;
    }

    /**
     * Снимает бронь: связанный активный запрос возвращается на подбор,
     * открытые конфликты с участием брони закрываются, заинтересованные лица уведомляются.
     */
    public ResourceRequest release(Booking booking, AppUser by, String reason) {
        booking.release(by);
        ResourceRequest request = null;
        if (booking instanceof ProjectBooking pb) {
            request = requests.findByBooking(pb).filter(ResourceRequest::isActive).orElse(null);
            if (request != null) request.bookingReleased();
        }
        conflicts.findOpenInvolving(booking).forEach(c -> c.resolve(reason, by));
        events.publishEvent(new BookingReleasedEvent(booking, request, reason));
        return request;
    }
}

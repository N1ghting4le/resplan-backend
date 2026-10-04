package com.resplan.service;

import com.resplan.booking.BookingCheckResult;
import com.resplan.booking.BookingValidator;
import com.resplan.domain.*;
import com.resplan.error.BusinessRuleException;
import com.resplan.event.BookingAssignedEvent;
import com.resplan.repository.BookingRepository;
import com.resplan.repository.EmployeeRepository;
import com.resplan.repository.ProjectRepository;
import com.resplan.repository.ResourceRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** UC-7: управление проектным бронированием сотрудников (мягкая и жесткая бронь, снятие брони) */
@Service
@RequiredArgsConstructor
@Transactional
public class BookingService {

    private final BookingRepository bookings;
    private final EmployeeRepository employees;
    private final ProjectRepository projects;
    private final ResourceRequestRepository requests;
    private final BookingValidator validator;
    private final BookingLifecycle lifecycle;
    private final ApplicationEventPublisher events;

    /** Действующие брони в периоде; employeeId = null – по всем сотрудникам (матрица распределения) */
    @Transactional(readOnly = true)
    public List<Booking> list(Integer employeeId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) throw new BusinessRuleException("PERIOD", "Дата окончания периода раньше даты начала");
        return employeeId == null ? bookings.findOverlapping(from, to) : bookings.findOverlapping(employeeId, from, to);
    }

    @Transactional(readOnly = true)
    public Booking get(long bookingId) {
        return bookings.require(bookingId, "Бронирование");
    }

    /** FR7-2, FR7-3: новая проектная бронь с проверкой правил и регистрацией конфликтов */
    public BookingResult create(BookingCommand cmd, AppUser rm) {
        if (cmd.kind() == BookingKind.TRAINING) {
            throw new BusinessRuleException("KIND", "Обучение бронирует сотрудник L&D (UC-9)");
        }
        Employee employee = employees.require(cmd.employeeId(), "Сотрудник");
        Project project = projects.require(cmd.projectId(), "Проект");
        ProjectBooking booking = new ProjectBooking(employee, project, cmd.kind(),
                cmd.startDate(), cmd.endDate(), cmd.loadPercent(), rm);
        BookingCheckResult check = validator.validate(booking).throwIfRejected();
        bookings.save(booking);
        events.publishEvent(new BookingAssignedEvent(booking));
        return withConflicts(booking, check);
    }

    /** Изменение периода, загрузки или перевод мягкой брони в жесткую */
    public BookingResult update(long bookingId, BookingCommand cmd) {
        ProjectBooking booking = requireEditable(bookingId);
        booking.reschedule(
                cmd.startDate() != null ? cmd.startDate() : booking.getStartDate(),
                cmd.endDate() != null ? cmd.endDate() : booking.getEndDate(),
                cmd.loadPercent() != null ? cmd.loadPercent() : booking.getLoadPercent());
        if (cmd.kind() == BookingKind.HARD && booking.getKind() == BookingKind.SOFT) {
            booking.harden();
        } else if (cmd.kind() != null && cmd.kind() != booking.getKind()) {
            throw new BusinessRuleException("KIND", "Жесткую бронь нельзя сделать мягкой: снимите ее и создайте новую");
        }
        BookingCheckResult check = validator.validate(booking).throwIfRejected();
        return withConflicts(booking, check);
    }

    /** FR7-4: снятие брони, сотрудник возвращается в пул доступных ресурсов */
    public ResourceRequest release(long bookingId, AppUser rm) {
        Booking booking = bookings.require(bookingId, "Бронирование");
        if (!(booking instanceof ProjectBooking)) {
            throw new BusinessRuleException("KIND", "Бронь обучения снимает сотрудник L&D (UC-9)");
        }
        requireActive(booking);
        return lifecycle.release(booking, rm, "Бронь снята ресурсным менеджером");
    }

    private ProjectBooking requireEditable(long bookingId) {
        Booking booking = bookings.require(bookingId, "Бронирование");
        if (!(booking instanceof ProjectBooking pb)) {
            throw new BusinessRuleException("KIND", "Бронь обучения изменяет сотрудник L&D (UC-9)");
        }
        requireActive(pb);
        requests.findByBooking(pb).filter(ResourceRequest::isActive).ifPresent(r -> {
            throw new BusinessRuleException("REQUEST_LINKED", "Бронь закреплена за запросом №" + r.getId()
                    + ": решение по ней принимает PM (UC-3)");
        });
        return pb;
    }

    private static void requireActive(Booking booking) {
        if (!booking.isActive()) throw new BusinessRuleException("RELEASED", "Бронь " + booking.getId() + " уже снята");
    }

    private BookingResult withConflicts(Booking booking, BookingCheckResult check) {
        List<ResourceConflict> conflicts = booking.getKind() == BookingKind.SOFT
                ? List.of() : lifecycle.registerConflicts(booking, check.getOverloads());
        return new BookingResult(booking, check.getOverloads(), conflicts);
    }
}

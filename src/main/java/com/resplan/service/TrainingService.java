package com.resplan.service;

import com.resplan.booking.BookingCheckResult;
import com.resplan.booking.BookingValidator;
import com.resplan.domain.*;
import com.resplan.error.BusinessRuleException;
import com.resplan.event.BookingAssignedEvent;
import com.resplan.repository.BookingRepository;
import com.resplan.repository.EmployeeRepository;
import com.resplan.repository.TrainingCourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** UC-9: резервирование защищенного времени на обучение сотрудником L&D */
@Service
@RequiredArgsConstructor
@Transactional
public class TrainingService {

    private static final String DEFAULT_PROVIDER = "EPAM University";

    private final TrainingCourseRepository courses;
    private final EmployeeRepository employees;
    private final BookingRepository bookings;
    private final BookingValidator validator;
    private final BookingLifecycle lifecycle;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<TrainingCourse> courses() {
        return courses.findAllByOrderByTitle();
    }

    /**
     * FR9-1 – FR9-4: блок обучения добавляется в календарь сотрудника. Пересечение защищенного
     * обучения с жесткой бронью запрещено, сотрудник исключается из подбора на эти даты.
     */
    public BookingResult reserve(TrainingCommand cmd, AppUser ld) {
        Employee employee = employees.require(cmd.employeeId(), "Сотрудник");
        TrainingCourse course = courses.findByTitleIgnoreCase(cmd.courseTitle().trim())
                .orElseGet(() -> courses.save(new TrainingCourse(cmd.courseTitle().trim(),
                        cmd.provider() == null || cmd.provider().isBlank() ? DEFAULT_PROVIDER : cmd.provider().trim())));
        TrainingBooking booking = new TrainingBooking(employee, course, cmd.startDate(), cmd.endDate(),
                cmd.loadPercent(), cmd.protectedTime(), ld);
        BookingCheckResult check = validator.validate(booking).throwIfRejected();
        bookings.save(booking);
        List<ResourceConflict> conflicts = lifecycle.registerConflicts(booking, check.getOverloads());
        events.publishEvent(new BookingAssignedEvent(booking));
        return new BookingResult(booking, check.getOverloads(), conflicts);
    }

    public void cancel(long bookingId, AppUser ld) {
        Booking booking = bookings.require(bookingId, "Бронирование");
        if (!(booking instanceof TrainingBooking)) {
            throw new BusinessRuleException("KIND", "Бронирование " + bookingId + " не является обучением");
        }
        if (!booking.isActive()) throw new BusinessRuleException("RELEASED", "Обучение уже отменено");
        lifecycle.release(booking, ld, "Обучение отменено сотрудником L&D");
    }
}

package com.resplan.booking;

import com.resplan.domain.Booking;
import com.resplan.repository.BookingRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** Собирает цепочку правил и проверяет бронирование относительно существующих броней сотрудника */
@Component
public class BookingValidator {

    private final BookingRepository bookings;
    private final BookingRule chain;

    public BookingValidator(BookingRepository bookings) {
        this.bookings = bookings;
        BookingRule head = new PeriodRule();
        head.linkWith(new ProtectedTrainingRule())
            .linkWith(new OverloadRule());
        this.chain = head;
    }

    public BookingCheckResult validate(Booking candidate) {
        List<Booking> existing = bookings
                .findOverlapping(candidate.getEmployee().getId(), candidate.getStartDate(), candidate.getEndDate())
                .stream()
                .filter(b -> !Objects.equals(b.getId(), candidate.getId()))
                .toList();
        BookingCheckResult result = new BookingCheckResult();
        chain.check(candidate, existing, result);
        return result;
    }
}

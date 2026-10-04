package com.resplan.booking;

import com.resplan.domain.Booking;

import java.util.List;

/**
 * Звено цепочки проверок бронирования (паттерн «Цепочка обязанностей»).
 * Проверка передается следующему звену, пока бронирование не отклонено.
 */
public abstract class BookingRule {

    private BookingRule next;

    public BookingRule linkWith(BookingRule next) {
        this.next = next;
        return next;
    }

    public final void check(Booking candidate, List<Booking> existing, BookingCheckResult result) {
        apply(candidate, existing, result);
        if (!result.isRejected() && next != null) {
            next.check(candidate, existing, result);
        }
    }

    protected abstract void apply(Booking candidate, List<Booking> existing, BookingCheckResult result);
}

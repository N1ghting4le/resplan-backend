package com.resplan.booking;

import com.resplan.domain.Booking;

import java.util.List;

/** Корректность периода и процента загрузки */
public class PeriodRule extends BookingRule {

    @Override
    protected void apply(Booking candidate, List<Booking> existing, BookingCheckResult result) {
        if (candidate.getEndDate().isBefore(candidate.getStartDate())) {
            result.reject("PERIOD", "Дата окончания бронирования раньше даты начала");
        } else if (candidate.getLoadPercent() < 1 || candidate.getLoadPercent() > 100) {
            result.reject("LOAD", "Загрузка должна быть от 1 до 100 %");
        }
    }
}

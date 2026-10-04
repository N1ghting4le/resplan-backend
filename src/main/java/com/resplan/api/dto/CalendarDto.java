package com.resplan.api.dto;

import com.resplan.service.CalendarService.CalendarView;

import java.time.LocalDate;
import java.util.List;

public record CalendarDto(int employeeId, String employee, String period, LocalDate from, LocalDate to,
                          int workdays, double averageLoad, int peakLoad, boolean bench,
                          List<BookingDto> items, List<String> hints) {

    /** hints – ссылки на образовательные порталы для сотрудника в резерве (FR8-3) */
    public static CalendarDto of(CalendarView v, List<String> learningPortals) {
        var e = v.employee();
        return new CalendarDto(e.getId(), e.fullName(), v.period().name(), v.from(), v.to(),
                v.load().workdays(), Math.round(v.load().average() * 10) / 10.0, v.load().peak(), v.bench(),
                v.bookings().stream().map(BookingDto::of).toList(),
                v.bench() ? learningPortals : List.of());
    }
}

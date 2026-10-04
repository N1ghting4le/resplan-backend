package com.resplan.api.dto;

import com.resplan.service.BookingResult;

import java.util.List;

/** Ответ на бронирование: бронь, предупреждения о перегрузке (BR-08) и зарегистрированные конфликты */
public record BookingResultDto(BookingDto booking, List<String> warnings, List<ConflictDto> conflicts) {

    public static BookingResultDto of(BookingResult r) {
        List<String> warnings = r.overloads().stream()
                .map(b -> "Перегрузка с бронью " + b.title() + " (" + b.getStartDate() + " – " + b.getEndDate()
                        + ", " + b.getLoadPercent() + " %)")
                .toList();
        return new BookingResultDto(BookingDto.of(r.booking()), warnings,
                r.conflicts().stream().map(ConflictDto::of).toList());
    }
}

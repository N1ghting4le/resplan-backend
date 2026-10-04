package com.resplan.api.dto;

import com.resplan.domain.BookingKind;
import com.resplan.service.BookingCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;

/** Частичное изменение брони: передаются только изменяемые поля */
public record BookingPatch(BookingKind kind, LocalDate startDate, LocalDate endDate,
                           @Min(1) @Max(100) Integer loadPercent) implements DateRange {

    public BookingCommand toCommand() {
        return new BookingCommand(null, null, kind, startDate, endDate, loadPercent);
    }
}

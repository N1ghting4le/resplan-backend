package com.resplan.api.dto;

import com.resplan.domain.BookingKind;
import com.resplan.service.BookingCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record BookingRequest(
        @NotNull Integer employeeId,
        @NotNull Integer projectId,
        @NotNull BookingKind kind,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @Min(1) @Max(100) Integer loadPercent) implements DateRange {

    public BookingCommand toCommand() {
        return new BookingCommand(employeeId, projectId, kind, startDate, endDate, loadPercent);
    }
}

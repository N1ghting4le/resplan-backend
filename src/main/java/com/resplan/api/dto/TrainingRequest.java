package com.resplan.api.dto;

import com.resplan.service.TrainingCommand;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record TrainingRequest(
        @NotNull Integer employeeId,
        @NotBlank @Size(max = 150) String courseTitle,
        @Size(max = 100) String provider,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @Min(1) @Max(100) Integer loadPercent,
        @NotNull Boolean protectedTime) implements DateRange {

    public TrainingCommand toCommand() {
        return new TrainingCommand(employeeId, courseTitle, provider, startDate, endDate, loadPercent, protectedTime);
    }
}

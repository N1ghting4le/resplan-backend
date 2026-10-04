package com.resplan.api.dto;

import com.resplan.service.NewRequestCommand;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.Map;

public record NewRequestDto(
        @NotNull Integer taskId,
        @NotBlank @Size(max = 80) String role,
        @NotBlank String grade,
        @NotNull @Min(1) @Max(100) Integer loadPercent,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotEmpty Map<@NotBlank String, @NotNull @Min(1) @Max(5) Integer> skills) implements DateRange {

    public NewRequestCommand toCommand() {
        return new NewRequestCommand(taskId, role.trim(), grade, loadPercent, startDate, endDate, skills);
    }
}

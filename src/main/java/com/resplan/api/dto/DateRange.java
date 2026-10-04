package com.resplan.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;

import java.time.LocalDate;

/**
 * Общая проверка периода для всех входных DTO с датами (принцип DRY):
 * дата окончания не может быть раньше даты начала (FR1-5, FR2-4).
 */
public interface DateRange {

    LocalDate startDate();

    LocalDate endDate();

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Дата окончания раньше даты начала")
    default boolean isPeriodValid() {
        return startDate() == null || endDate() == null || !endDate().isBefore(startDate());
    }
}

package com.resplan.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Решение по конфликту: какая бронь сохраняется, и комментарий для PM */
public record ResolutionDto(@NotNull Long keepBookingId, @Size(max = 500) String note) {
}

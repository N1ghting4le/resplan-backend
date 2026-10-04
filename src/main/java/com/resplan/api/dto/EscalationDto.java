package com.resplan.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EscalationDto(@NotBlank @Size(max = 500) String note) {
}

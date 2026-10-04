package com.resplan.api.dto;

import jakarta.validation.constraints.NotNull;

public record ProposalDto(@NotNull Integer employeeId) {
}

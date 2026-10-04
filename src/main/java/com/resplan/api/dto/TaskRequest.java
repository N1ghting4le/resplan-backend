package com.resplan.api.dto;

import com.resplan.service.TaskCommand;
import jakarta.validation.constraints.*;

import java.util.List;

public record TaskRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull @Min(0) @Max(500) Integer durationDays,
        List<@NotNull Integer> predecessorIds) {

    public TaskCommand toCommand() {
        return new TaskCommand(name.trim(), durationDays, predecessorIds == null ? List.of() : predecessorIds);
    }
}

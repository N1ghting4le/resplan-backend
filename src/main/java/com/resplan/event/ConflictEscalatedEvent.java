package com.resplan.event;

import com.resplan.domain.ResourceConflict;

public record ConflictEscalatedEvent(ResourceConflict conflict) {
}

package com.resplan.event;

import com.resplan.domain.ResourceConflict;

public record ConflictDetectedEvent(ResourceConflict conflict) {
}

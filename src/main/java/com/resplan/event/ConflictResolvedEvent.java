package com.resplan.event;

import com.resplan.domain.Booking;
import com.resplan.domain.ResourceConflict;

public record ConflictResolvedEvent(ResourceConflict conflict, Booking kept, Booking released) {
}

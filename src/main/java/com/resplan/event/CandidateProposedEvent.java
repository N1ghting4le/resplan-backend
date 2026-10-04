package com.resplan.event;

import com.resplan.domain.Booking;
import com.resplan.domain.ResourceRequest;

import java.util.List;

public record CandidateProposedEvent(ResourceRequest request, List<Booking> overloads) {
}

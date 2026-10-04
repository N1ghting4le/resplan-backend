package com.resplan.service;

import com.resplan.domain.Booking;
import com.resplan.domain.ResourceRequest;

import java.util.List;

public record ProposalResult(ResourceRequest request, List<Booking> overloads) {
}

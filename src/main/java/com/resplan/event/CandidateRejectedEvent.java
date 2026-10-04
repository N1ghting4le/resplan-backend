package com.resplan.event;

import com.resplan.domain.Employee;
import com.resplan.domain.ResourceRequest;

public record CandidateRejectedEvent(ResourceRequest request, Employee employee, String reason) {
}

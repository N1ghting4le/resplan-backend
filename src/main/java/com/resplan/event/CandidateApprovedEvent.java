package com.resplan.event;

import com.resplan.domain.ResourceRequest;

public record CandidateApprovedEvent(ResourceRequest request) {
}

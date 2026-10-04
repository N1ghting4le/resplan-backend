package com.resplan.event;

import com.resplan.domain.ResourceRequest;

public record RequestSubmittedEvent(ResourceRequest request) {
}

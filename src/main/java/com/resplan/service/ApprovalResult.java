package com.resplan.service;

import com.resplan.domain.ResourceConflict;
import com.resplan.domain.ResourceRequest;

import java.util.List;

public record ApprovalResult(ResourceRequest request, List<ResourceConflict> conflicts) {
}

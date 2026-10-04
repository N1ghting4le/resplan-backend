package com.resplan.api.dto;

import com.resplan.service.ApprovalResult;

import java.util.List;

public record ApprovalDto(RequestDto request, List<ConflictDto> conflicts) {

    public static ApprovalDto of(ApprovalResult r) {
        return new ApprovalDto(RequestDto.of(r.request()), r.conflicts().stream().map(ConflictDto::of).toList());
    }
}

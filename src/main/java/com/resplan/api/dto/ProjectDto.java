package com.resplan.api.dto;

import com.resplan.domain.Project;

import java.time.LocalDate;

public record ProjectDto(int id, String code, String name, String pm, String location,
                         int priority, LocalDate startDate, String status) {

    public static ProjectDto of(Project p) {
        return new ProjectDto(p.getId(), p.getCode(), p.getName(), p.getPm().getEmployee().fullName(),
                p.getLocation().getCity(), p.getPriority(), p.getStartDate(), p.getStatus().name());
    }
}

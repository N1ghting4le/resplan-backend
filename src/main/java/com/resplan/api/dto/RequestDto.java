package com.resplan.api.dto;

import com.resplan.domain.ResourceRequest;
import com.resplan.domain.SkillLevel;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RequestDto(int id, String project, int taskId, String task, String role, String grade,
                         Map<String, Integer> skills, int loadPercent, LocalDate startDate, LocalDate endDate,
                         String status, Integer candidateId, String candidate, Long bookingId, String bookingKind,
                         List<String> warnings) {

    public static RequestDto of(ResourceRequest r) {
        return of(r, List.of());
    }

    public static RequestDto of(ResourceRequest r, List<String> warnings) {
        var b = r.getBooking();
        Map<String, Integer> skills = new LinkedHashMap<>();
        for (SkillLevel s : r.getRequiredSkills()) skills.put(s.getSkill().getName(), (int) s.getLevel());
        return new RequestDto(r.getId(), r.project().getCode(), r.getTask().getId(), r.getTask().getName(),
                r.getRole(), r.getGrade().getName(), skills, r.getLoadPercent(), r.getStartDate(), r.getEndDate(),
                r.getStatus().name(),
                b == null ? null : b.getEmployee().getId(), b == null ? null : b.getEmployee().fullName(),
                b == null ? null : b.getId(), b == null ? null : b.getKind().name(), warnings);
    }
}

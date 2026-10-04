package com.resplan.api.dto;

import com.resplan.matching.Candidate;

import java.util.Map;

public record CandidateDto(int employeeId, String name, String grade, String location,
                           int match, Map<String, Double> scores) {

    public static CandidateDto of(Candidate c) {
        var e = c.employee();
        return new CandidateDto(e.getId(), e.fullName(), e.getGrade().getName(), e.getLocation().getCity(),
                c.match(), c.scores());
    }
}

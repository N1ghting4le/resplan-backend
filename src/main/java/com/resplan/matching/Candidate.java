package com.resplan.matching;

import com.resplan.domain.Employee;

import java.util.Map;

/** Кандидат на запрос: итоговый процент соответствия и оценки по критериям */
public record Candidate(Employee employee, int match, Map<String, Double> scores) {
}

package com.resplan.matching;

import com.resplan.domain.Booking;
import com.resplan.domain.Employee;
import com.resplan.domain.ResourceRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Интеллектуальный подбор кандидатов (Smart Matching, UC-5) – контекст паттерна «Стратегия».
 * Набор критериев внедряется Spring и может расширяться без изменения алгоритма.
 */
@Component
public class CandidateMatcher {

    private final List<ScoringCriterion> criteria;

    public CandidateMatcher(List<ScoringCriterion> criteria) {
        double sum = criteria.stream().mapToDouble(ScoringCriterion::weight).sum();
        if (Math.abs(sum - 1.0) > 1e-9) {
            throw new IllegalStateException("Сумма весов критериев подбора должна быть равна 1, получено " + sum);
        }
        this.criteria = List.copyOf(criteria);
    }

    public List<Candidate> rank(ResourceRequest request, Collection<Employee> employees,
                                Map<Integer, List<Booking>> bookingsByEmployee) {
        Set<Integer> rejected = request.rejectedEmployeeIds();
        List<Candidate> result = new ArrayList<>();
        for (Employee employee : employees) {
            if (rejected.contains(employee.getId())) continue;
            List<Booking> busy = bookingsByEmployee.getOrDefault(employee.getId(), List.of());
            boolean protectedTraining = busy.stream().anyMatch(b ->
                    b.isProtectedTime() && b.overlaps(request.getStartDate(), request.getEndDate()));
            if (protectedTraining) continue;

            MatchContext ctx = new MatchContext(request, employee, busy);
            Map<String, Double> scores = new LinkedHashMap<>();
            double total = 0;
            boolean passed = true;
            for (ScoringCriterion criterion : criteria) {
                double s = criterion.score(ctx);
                if (s < criterion.threshold()) {
                    passed = false;
                    break;
                }
                scores.put(criterion.code(), s);
                total += criterion.weight() * s;
            }
            if (passed) result.add(new Candidate(employee, (int) Math.round(total * 100), scores));
        }
        result.sort(Comparator.comparingInt(Candidate::match).reversed()
                .thenComparing(c -> c.employee().getHourlyRate()));
        return result;
    }
}

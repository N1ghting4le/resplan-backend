package com.resplan.matching;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Соответствие грейда: точное совпадение – 1, выше требуемого – 0,7, ниже на одну ступень – 0,4 */
@Component
@Order(2)
public class GradeCriterion implements ScoringCriterion {

    @Override
    public String code() {
        return "grade";
    }

    @Override
    public double weight() {
        return 0.20;
    }

    @Override
    public double score(MatchContext ctx) {
        int diff = ctx.employee().getGrade().getRankOrder() - ctx.request().getGrade().getRankOrder();
        if (diff == 0) return 1.0;
        if (diff > 0) return 0.7;
        return diff == -1 ? 0.4 : 0.0;
    }
}

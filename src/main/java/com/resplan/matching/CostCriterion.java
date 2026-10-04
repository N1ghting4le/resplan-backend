package com.resplan.matching;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Стоимость: чем ниже внутренняя ставка, тем выше оценка (ставка 10 р./ч – 1, 40 р./ч – 0) */
@Component
@Order(4)
public class CostCriterion implements ScoringCriterion {

    @Override
    public String code() {
        return "cost";
    }

    @Override
    public double weight() {
        return 0.05;
    }

    @Override
    public double score(MatchContext ctx) {
        double rate = ctx.employee().getHourlyRate().doubleValue();
        return Math.max(0, Math.min(1, 1 - (rate - 10) / 30));
    }
}

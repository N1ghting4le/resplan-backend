package com.resplan.matching;

import com.resplan.domain.LoadProfile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Доступность: доля требуемой загрузки, которую сотрудник может принять.
 * Средняя занятость считается по рабочим дням календаря локации сотрудника.
 */
@Component
@Order(3)
public class AvailabilityCriterion implements ScoringCriterion {

    @Override
    public String code() {
        return "availability";
    }

    @Override
    public double weight() {
        return 0.25;
    }

    @Override
    public double score(MatchContext ctx) {
        LoadProfile profile = LoadProfile.of(ctx.employee().getLocation().getCalendar(), ctx.bookings(),
                ctx.request().getStartDate(), ctx.request().getEndDate());
        if (profile.workdays() == 0) return 0;
        double free = (100 - profile.average()) / ctx.request().getLoadPercent();
        return Math.max(0, Math.min(1, free));
    }
}

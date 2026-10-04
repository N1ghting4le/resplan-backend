package com.resplan.matching;

import com.resplan.domain.SkillLevel;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/** Соответствие навыков: среднее по требуемым навыкам отношение уровня к минимальному */
@Component
@Order(1)
public class SkillCriterion implements ScoringCriterion {

    @Override
    public String code() {
        return "skills";
    }

    @Override
    public double weight() {
        return 0.50;
    }

    @Override
    public double threshold() {
        return 0.30;
    }

    @Override
    public double score(MatchContext ctx) {
        List<SkillLevel> required = ctx.request().getRequiredSkills();
        if (required.isEmpty()) return 1;
        double sum = 0;
        for (SkillLevel need : required) {
            int level = ctx.employee().levelOf(need.getSkill());
            sum += Math.min((double) level / need.getLevel(), 1.0);
        }
        return sum / required.size();
    }
}

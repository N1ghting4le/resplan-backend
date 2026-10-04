package com.resplan.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Уровень владения навыком (1–5): строка таблиц employee_skill и request_skill */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkillLevel {

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "skill_id")
    private Skill skill;

    @Column(name = "level", nullable = false)
    private short level;

    public SkillLevel(Skill skill, int level) {
        if (level < 1 || level > 5) throw new IllegalArgumentException("Уровень навыка должен быть от 1 до 5");
        this.skill = skill;
        this.level = (short) level;
    }
}

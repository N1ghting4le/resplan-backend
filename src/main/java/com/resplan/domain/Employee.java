package com.resplan.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "employee")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_id")
    private Integer id;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "grade_id")
    private Grade grade;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(name = "hourly_rate", nullable = false, precision = 8, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ElementCollection
    @CollectionTable(name = "employee_skill", joinColumns = @JoinColumn(name = "employee_id"))
    private List<SkillLevel> skills = new ArrayList<>();

    public Employee(String lastName, String firstName, Grade grade, Location location, BigDecimal hourlyRate) {
        this.lastName = lastName;
        this.firstName = firstName;
        this.grade = grade;
        this.location = location;
        this.hourlyRate = hourlyRate;
    }

    public Employee addSkill(Skill skill, int level) {
        skills.add(new SkillLevel(skill, level));
        return this;
    }

    /** Уровень владения навыком; 0, если навык отсутствует */
    public int levelOf(Skill skill) {
        return skills.stream()
                .filter(s -> s.getSkill().getId().equals(skill.getId()))
                .mapToInt(SkillLevel::getLevel)
                .findFirst().orElse(0);
    }

    public String fullName() {
        return lastName + " " + firstName;
    }
}

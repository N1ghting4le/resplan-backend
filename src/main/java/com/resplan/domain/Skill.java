package com.resplan.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "skill")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "skill_id")
    private Integer id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(name = "is_ai", nullable = false)
    private boolean ai;

    public Skill(String name, boolean ai) {
        this.name = name;
        this.ai = ai;
    }
}

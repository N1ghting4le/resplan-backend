package com.resplan.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Грейд сотрудника; rankOrder задает порядок Junior < Middle < Senior < Lead */
@Entity
@Table(name = "grade")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Grade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "grade_id")
    private Short id;

    @Column(nullable = false, unique = true, length = 20)
    private String name;

    @Column(name = "rank_order", nullable = false, unique = true)
    private short rankOrder;

    public Grade(String name, int rankOrder) {
        this.name = name;
        this.rankOrder = (short) rankOrder;
    }
}

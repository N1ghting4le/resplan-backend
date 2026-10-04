package com.resplan.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Задача сетевого графика; зависимости – связи «финиш-старт» (таблица task_dependency) */
@Entity
@Table(name = "task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id")
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "duration_days", nullable = false)
    private short durationDays;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private Employee assignee;

    @ManyToMany
    @JoinTable(name = "task_dependency",
            joinColumns = @JoinColumn(name = "successor_id"),
            inverseJoinColumns = @JoinColumn(name = "predecessor_id"))
    private Set<Task> predecessors = new LinkedHashSet<>();

    public Task(Project project, String name, int durationDays, Task... predecessors) {
        this.project = project;
        this.name = name;
        this.durationDays = (short) durationDays;
        this.predecessors.addAll(List.of(predecessors));
    }

    /** UC-1: изменение задачи сетевого графика; связи заменяются целиком */
    public void update(String name, int durationDays, List<Task> predecessors) {
        this.name = name;
        this.durationDays = (short) durationDays;
        this.predecessors.clear();
        this.predecessors.addAll(predecessors);
    }

    public void removePredecessor(Task task) {
        predecessors.remove(task);
    }

    public void assign(Employee employee) {
        this.assignee = employee;
    }
}

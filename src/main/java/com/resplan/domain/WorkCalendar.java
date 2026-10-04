package com.resplan.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** Производственный календарь локации (правило BR-03) */
@Entity
@Table(name = "work_calendar")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkCalendar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "calendar_id")
    private Short id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "weekend_day", joinColumns = @JoinColumn(name = "calendar_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 9)
    private Set<DayOfWeek> weekend = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "holiday", joinColumns = @JoinColumn(name = "calendar_id"))
    @Column(name = "holiday_date")
    private Set<LocalDate> holidays = new HashSet<>();

    public WorkCalendar(String name, Set<DayOfWeek> weekend, Set<LocalDate> holidays) {
        this.name = name;
        this.weekend.addAll(weekend);
        this.holidays.addAll(holidays);
    }

    public boolean isWorkday(LocalDate date) {
        return !weekend.contains(date.getDayOfWeek()) && !holidays.contains(date);
    }

    /** Дата n-го рабочего дня начиная с start (n = 0 – первый рабочий день) */
    public LocalDate workdayToDate(LocalDate start, int n) {
        LocalDate d = start;
        while (!isWorkday(d)) d = d.plusDays(1);
        for (int left = n; left > 0; ) {
            d = d.plusDays(1);
            if (isWorkday(d)) left--;
        }
        return d;
    }
}

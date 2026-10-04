package com.resplan.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "location")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "location_id")
    private Short id;

    @Column(nullable = false, unique = true, length = 60)
    private String city;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "calendar_id")
    private WorkCalendar calendar;

    public Location(String city, WorkCalendar calendar) {
        this.city = city;
        this.calendar = calendar;
    }
}

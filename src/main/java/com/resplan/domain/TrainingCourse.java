package com.resplan.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "training_course")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainingCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_id")
    private Integer id;

    @Column(nullable = false, unique = true, length = 150)
    private String title;

    @Column(nullable = false, length = 100)
    private String provider;

    public TrainingCourse(String title, String provider) {
        this.title = title;
        this.provider = provider;
    }
}

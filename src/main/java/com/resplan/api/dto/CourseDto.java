package com.resplan.api.dto;

import com.resplan.domain.TrainingCourse;

public record CourseDto(int id, String title, String provider) {

    public static CourseDto of(TrainingCourse c) {
        return new CourseDto(c.getId(), c.getTitle(), c.getProvider());
    }
}

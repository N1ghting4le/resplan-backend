package com.resplan.repository;

import com.resplan.domain.TrainingCourse;

import java.util.List;
import java.util.Optional;

public interface TrainingCourseRepository extends BaseRepository<TrainingCourse, Integer> {

    Optional<TrainingCourse> findByTitleIgnoreCase(String title);

    List<TrainingCourse> findAllByOrderByTitle();
}

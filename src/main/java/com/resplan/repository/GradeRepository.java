package com.resplan.repository;

import com.resplan.domain.Grade;

import java.util.Optional;

public interface GradeRepository extends BaseRepository<Grade, Short> {

    Optional<Grade> findByName(String name);
}

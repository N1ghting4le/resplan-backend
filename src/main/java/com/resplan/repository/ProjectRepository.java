package com.resplan.repository;

import com.resplan.domain.AppUser;
import com.resplan.domain.Project;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends BaseRepository<Project, Integer> {

    Optional<Project> findByCode(String code);

    List<Project> findAllByOrderByPriorityAscCodeAsc();

    List<Project> findByPmOrderByPriorityAscCodeAsc(AppUser pm);
}

package com.resplan.repository;

import com.resplan.domain.Skill;

import java.util.Optional;

public interface SkillRepository extends BaseRepository<Skill, Integer> {

    Optional<Skill> findByNameIgnoreCase(String name);
}

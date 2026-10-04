package com.resplan.repository;

import com.resplan.domain.AppUser;
import com.resplan.domain.Employee;
import com.resplan.domain.UserRole;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends BaseRepository<AppUser, Integer> {

    Optional<AppUser> findByLogin(String login);

    List<AppUser> findByRole(UserRole role);

    Optional<AppUser> findByEmployee(Employee employee);
}

package com.resplan.repository;

import com.resplan.domain.Employee;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends BaseRepository<Employee, Integer> {

    @Query("select distinct e from Employee e left join fetch e.skills where e.active = true order by e.lastName")
    List<Employee> findActiveWithSkills();

    Optional<Employee> findByLastName(String lastName);
}

package com.resplan.repository;

import com.resplan.domain.AppUser;
import com.resplan.domain.ProjectBooking;
import com.resplan.domain.RequestStatus;
import com.resplan.domain.ResourceRequest;
import com.resplan.domain.Task;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ResourceRequestRepository extends BaseRepository<ResourceRequest, Integer> {

    /** Запросы в заданных статусах; pm = null – запросы всех проектов (для RM) */
    @Query("""
            select r from ResourceRequest r
             where r.status in :statuses and (:pm is null or r.task.project.pm = :pm)
             order by r.id""")
    List<ResourceRequest> search(@Param("statuses") Collection<RequestStatus> statuses, @Param("pm") AppUser pm);

    Optional<ResourceRequest> findByBooking(ProjectBooking booking);

    boolean existsByTask(Task task);
}

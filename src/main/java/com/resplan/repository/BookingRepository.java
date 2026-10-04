package com.resplan.repository;

import com.resplan.domain.Booking;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/** Действующие (не снятые) бронирования; снятые остаются в базе для истории конфликтов */
public interface BookingRepository extends BaseRepository<Booking, Long> {

    /** Бронирования всех сотрудников, пересекающиеся с периодом [from; to] */
    @Query("""
            select b from Booking b where b.releasedAt is null
               and b.startDate <= :to and b.endDate >= :from""")
    List<Booking> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Бронирования сотрудника, пересекающиеся с периодом [from; to], в хронологическом порядке */
    @Query("""
            select b from Booking b where b.releasedAt is null and b.employee.id = :employeeId
               and b.startDate <= :to and b.endDate >= :from
             order by b.startDate, b.id""")
    List<Booking> findOverlapping(@Param("employeeId") Integer employeeId,
                                  @Param("from") LocalDate from, @Param("to") LocalDate to);
}

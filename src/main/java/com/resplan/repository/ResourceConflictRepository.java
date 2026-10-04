package com.resplan.repository;

import com.resplan.domain.Booking;
import com.resplan.domain.ConflictStatus;
import com.resplan.domain.ResourceConflict;

import java.util.Collection;
import java.util.List;

public interface ResourceConflictRepository extends BaseRepository<ResourceConflict, Integer> {

    boolean existsByBookingAAndBookingB(Booking bookingA, Booking bookingB);

    List<ResourceConflict> findByStatusInOrderById(Collection<ConflictStatus> statuses);

    /** Открытые конфликты, в которых участвует бронь (закрываются при ее снятии) */
    List<ResourceConflict> findByStatusAndBookingAOrStatusAndBookingB(ConflictStatus s1, Booking a,
                                                                      ConflictStatus s2, Booking b);

    default List<ResourceConflict> findOpenInvolving(Booking booking) {
        return findByStatusAndBookingAOrStatusAndBookingB(ConflictStatus.OPEN, booking, ConflictStatus.OPEN, booking);
    }
}

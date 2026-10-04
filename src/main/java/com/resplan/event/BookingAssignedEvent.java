package com.resplan.event;

import com.resplan.domain.Booking;

/** Ресурсный менеджер или сотрудник L&D забронировал время сотрудника (UC-7, UC-9) */
public record BookingAssignedEvent(Booking booking) {
}

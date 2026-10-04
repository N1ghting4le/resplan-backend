package com.resplan.service;

import com.resplan.domain.Booking;
import com.resplan.domain.ResourceConflict;

import java.util.List;

/** Результат бронирования: бронь, пересекающиеся брони с перегрузкой и зарегистрированные конфликты */
public record BookingResult(Booking booking, List<Booking> overloads, List<ResourceConflict> conflicts) {
}

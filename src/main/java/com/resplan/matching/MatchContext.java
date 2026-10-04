package com.resplan.matching;

import com.resplan.domain.Booking;
import com.resplan.domain.Employee;
import com.resplan.domain.ResourceRequest;

import java.util.List;

/** Данные для оценки одного кандидата: запрос, сотрудник и его брони в периоде запроса */
public record MatchContext(ResourceRequest request, Employee employee, List<Booking> bookings) {
}

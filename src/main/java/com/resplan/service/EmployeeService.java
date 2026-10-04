package com.resplan.service;

import com.resplan.domain.Booking;
import com.resplan.domain.Employee;
import com.resplan.repository.BookingRepository;
import com.resplan.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** FR7-1: пул сотрудников с текущей загрузкой; Bench – сотрудники без броней на дату */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employees;
    private final BookingRepository bookings;

    public record EmployeeLoad(Employee employee, int loadPercent, List<Booking> bookings) {

        public boolean bench() {
            return loadPercent == 0;
        }
    }

    /** Сотрудники с загрузкой на дату; skill – фильтр по навыку, benchOnly – только свободные */
    public List<EmployeeLoad> list(LocalDate date, String skill, boolean benchOnly) {
        Map<Integer, List<Booking>> busy = bookings.findOverlapping(date, date).stream()
                .collect(Collectors.groupingBy(b -> b.getEmployee().getId()));
        return employees.findActiveWithSkills().stream()
                .filter(e -> skill == null || e.getSkills().stream()
                        .anyMatch(s -> s.getSkill().getName().equalsIgnoreCase(skill)))
                .map(e -> load(e, busy.getOrDefault(e.getId(), List.of())))
                .filter(l -> !benchOnly || l.bench())
                .toList();
    }

    public EmployeeLoad get(int employeeId, LocalDate date) {
        Employee employee = employees.require(employeeId, "Сотрудник");
        return load(employee, bookings.findOverlapping(employeeId, date, date));
    }

    private static EmployeeLoad load(Employee employee, List<Booking> bookings) {
        return new EmployeeLoad(employee, bookings.stream().mapToInt(Booking::getLoadPercent).sum(), bookings);
    }
}

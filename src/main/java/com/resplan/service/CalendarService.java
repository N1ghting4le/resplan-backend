package com.resplan.service;

import com.resplan.domain.Booking;
import com.resplan.domain.CalendarPeriod;
import com.resplan.domain.Employee;
import com.resplan.domain.LoadProfile;
import com.resplan.repository.BookingRepository;
import com.resplan.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** UC-8: персональный график загрузки сотрудника за неделю, месяц или квартал */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarService {

    private final EmployeeRepository employees;
    private final BookingRepository bookings;

    public record CalendarView(Employee employee, CalendarPeriod period, LocalDate from, LocalDate to,
                               List<Booking> bookings, LoadProfile load) {

        /** FR8-3: в периоде нет ни проектов, ни обучения – сотрудник в резерве (Bench) */
        public boolean bench() {
            return bookings.isEmpty();
        }
    }

    public CalendarView view(int employeeId, CalendarPeriod period, LocalDate date) {
        Employee employee = employees.require(employeeId, "Сотрудник");
        LocalDate from = period.start(date);
        LocalDate to = period.end(date);
        List<Booking> items = bookings.findOverlapping(employeeId, from, to);
        LoadProfile load = LoadProfile.of(employee.getLocation().getCalendar(), items, from, to);
        return new CalendarView(employee, period, from, to, items, load);
    }
}

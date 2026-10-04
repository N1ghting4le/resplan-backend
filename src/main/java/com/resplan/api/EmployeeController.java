package com.resplan.api;

import com.resplan.api.dto.CalendarDto;
import com.resplan.api.dto.EmployeeDto;
import com.resplan.config.LearningProperties;
import com.resplan.domain.AppUser;
import com.resplan.domain.CalendarPeriod;
import com.resplan.service.CalendarService;
import com.resplan.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "UC-7, UC-8 Сотрудники и персональный график загрузки")
public class EmployeeController {

    private final EmployeeService employees;
    private final CalendarService calendars;
    private final LearningProperties learning;
    private final Clock clock;

    @GetMapping("/employees")
    @PreAuthorize("hasAnyRole('RM', 'PM', 'LD')")
    @Operation(summary = "Пул сотрудников с загрузкой на дату; bench=true – только свободные")
    public List<EmployeeDto> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                  @RequestParam(required = false) String skill,
                                  @RequestParam(defaultValue = "false") boolean bench) {
        return employees.list(orToday(date), skill, bench).stream().map(EmployeeDto::of).toList();
    }

    @GetMapping("/employees/{id}")
    @PreAuthorize("hasAnyRole('RM', 'PM', 'LD')")
    @Operation(summary = "Профиль сотрудника: навыки и брони на дату")
    public EmployeeDto get(@PathVariable int id,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return EmployeeDto.profile(employees.get(id, orToday(date)));
    }

    @GetMapping("/employees/{id}/calendar")
    @PreAuthorize("hasAnyRole('RM', 'PM', 'LD')")
    @Operation(summary = "UC-8, FR9-1: календарь выбранного сотрудника")
    public CalendarDto calendar(@PathVariable int id,
                                @RequestParam(defaultValue = "MONTH") CalendarPeriod period,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return CalendarDto.of(calendars.view(id, period, orToday(date)), learning.portals());
    }

    @GetMapping("/me/calendar")
    @Operation(summary = "UC-8: «Мой календарь» – персональный график загрузки текущего пользователя")
    public CalendarDto myCalendar(@CurrentUser AppUser user,
                                  @RequestParam(defaultValue = "MONTH") CalendarPeriod period,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return CalendarDto.of(calendars.view(user.getEmployee().getId(), period, orToday(date)), learning.portals());
    }

    private LocalDate orToday(LocalDate date) {
        return date != null ? date : LocalDate.now(clock);
    }
}

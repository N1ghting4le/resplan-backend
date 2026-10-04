package com.resplan.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.resplan.domain.SkillLevel;
import com.resplan.service.EmployeeService.EmployeeLoad;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Сотрудник с матрицей навыков и загрузкой на дату; bookings заполняется только в профиле */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmployeeDto(int id, String name, String grade, String location, BigDecimal hourlyRate,
                          Map<String, Integer> skills, int loadPercent, boolean bench, List<BookingDto> bookings) {

    public static EmployeeDto of(EmployeeLoad l) {
        return of(l, false);
    }

    public static EmployeeDto profile(EmployeeLoad l) {
        return of(l, true);
    }

    private static EmployeeDto of(EmployeeLoad l, boolean withBookings) {
        var e = l.employee();
        Map<String, Integer> skills = new LinkedHashMap<>();
        for (SkillLevel s : e.getSkills()) skills.put(s.getSkill().getName(), (int) s.getLevel());
        return new EmployeeDto(e.getId(), e.fullName(), e.getGrade().getName(), e.getLocation().getCity(),
                e.getHourlyRate(), skills, l.loadPercent(), l.bench(),
                withBookings ? l.bookings().stream().map(BookingDto::of).toList() : null);
    }
}

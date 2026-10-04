package com.resplan.service;

import com.resplan.domain.BookingKind;

import java.time.LocalDate;

/** Параметры проектной брони (UC-7); при изменении брони null означает «оставить без изменений» */
public record BookingCommand(Integer employeeId, Integer projectId, BookingKind kind,
                             LocalDate startDate, LocalDate endDate, Integer loadPercent) {
}

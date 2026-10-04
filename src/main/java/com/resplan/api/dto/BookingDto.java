package com.resplan.api.dto;

import com.resplan.domain.Booking;
import com.resplan.domain.ProjectBooking;

import java.time.LocalDate;

/** Бронирование в ответах API: проектная бронь (PROJECT) или обучение (TRAINING) */
public record BookingDto(long id, int employeeId, String employee, String type, String kind, String title,
                         Integer projectId, Integer projectPriority, LocalDate startDate, LocalDate endDate,
                         int loadPercent, boolean protectedTime, boolean active) {

    public static BookingDto of(Booking b) {
        Integer projectId = null, priority = null;
        if (b instanceof ProjectBooking pb) {
            projectId = pb.getProject().getId();
            priority = (int) pb.getProject().getPriority();
        }
        return new BookingDto(b.getId(), b.getEmployee().getId(), b.getEmployee().fullName(),
                b.isProjectWork() ? "PROJECT" : "TRAINING", b.getKind().name(), b.title(), projectId, priority,
                b.getStartDate(), b.getEndDate(), b.getLoadPercent(), b.isProtectedTime(), b.isActive());
    }
}

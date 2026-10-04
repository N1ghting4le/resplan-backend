package com.resplan.api.dto;

import com.resplan.domain.ResourceConflict;

import java.time.Instant;
import java.time.LocalDate;

public record ConflictDto(int id, String employee, String bookingA, String bookingB,
                          LocalDate from, LocalDate to, int totalLoad, String status,
                          BookingDto first, BookingDto second, Instant detectedAt, String resolutionNote) {

    public static ConflictDto of(ResourceConflict c) {
        var a = c.getBookingA();
        var b = c.getBookingB();
        return new ConflictDto(c.getId(), a.getEmployee().fullName(), a.title(), b.title(),
                a.overlapStart(b), a.overlapEnd(b), c.totalLoad(), c.getStatus().name(),
                BookingDto.of(a), BookingDto.of(b), c.getDetectedAt(), c.getResolutionNote());
    }
}

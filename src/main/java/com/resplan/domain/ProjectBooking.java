package com.resplan.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "project_booking")
@PrimaryKeyJoinColumn(name = "booking_id")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectBooking extends Booking {

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "project_id")
    private Project project;

    public ProjectBooking(Employee employee, Project project, BookingKind kind, LocalDate startDate,
                          LocalDate endDate, int loadPercent, AppUser createdBy) {
        super(employee, kind, startDate, endDate, loadPercent, createdBy);
        if (kind == BookingKind.TRAINING) throw new IllegalArgumentException("Проектная бронь не может иметь вид TRAINING");
        this.project = project;
    }

    /** Перевод мягкой брони в жесткую при утверждении кандидата */
    public void harden() {
        if (getKind() != BookingKind.SOFT) throw new IllegalStateException("Жесткой может стать только мягкая бронь");
        changeKind(BookingKind.HARD);
    }

    @Override
    public String title() {
        return project.getCode();
    }
}

package com.resplan.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "training_booking")
@PrimaryKeyJoinColumn(name = "booking_id")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainingBooking extends Booking {

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id")
    private TrainingCourse course;

    @Column(name = "is_protected", nullable = false)
    private boolean protectedTime;

    public TrainingBooking(Employee employee, TrainingCourse course, LocalDate startDate, LocalDate endDate,
                           int loadPercent, boolean protectedTime, AppUser createdBy) {
        super(employee, BookingKind.TRAINING, startDate, endDate, loadPercent, createdBy);
        this.course = course;
        this.protectedTime = protectedTime;
    }

    @Override
    public boolean isProtectedTime() {
        return protectedTime;
    }

    @Override
    public String title() {
        return course.getTitle();
    }
}

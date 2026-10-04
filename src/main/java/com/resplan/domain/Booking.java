package com.resplan.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Бронирование рабочего времени сотрудника – супертип иерархии
 * (стратегия JOINED: таблица booking и таблицы подтипов project_booking, training_booking).
 */
@Entity
@Table(name = "booking")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private BookingKind kind;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "load_percent", nullable = false)
    private short loadPercent;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Момент снятия брони; снятая бронь не учитывается в загрузке, но остается в истории конфликтов */
    @Column(name = "released_at")
    private Instant releasedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "released_by")
    private AppUser releasedBy;

    protected Booking(Employee employee, BookingKind kind, LocalDate startDate, LocalDate endDate,
                      int loadPercent, AppUser createdBy) {
        this.employee = employee;
        this.kind = kind;
        this.startDate = startDate;
        this.endDate = endDate;
        this.loadPercent = (short) loadPercent;
        this.createdBy = createdBy;
    }

    protected void changeKind(BookingKind kind) {
        this.kind = kind;
    }

    /** Изменение периода и загрузки (UC-7); проверку правил выполняет BookingValidator */
    public void reschedule(LocalDate startDate, LocalDate endDate, int loadPercent) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.loadPercent = (short) loadPercent;
    }

    /** Снятие брони: сотрудник возвращается в пул доступных ресурсов на этот период */
    public void release(AppUser by) {
        if (!isActive()) throw new IllegalStateException("Бронь уже снята");
        this.releasedAt = Instant.now();
        this.releasedBy = by;
    }

    public boolean isActive() {
        return releasedAt == null;
    }

    /** Пересечение с периодом [from; to], границы включаются */
    public boolean overlaps(LocalDate from, LocalDate to) {
        return !endDate.isBefore(from) && !to.isBefore(startDate);
    }

    public boolean overlaps(Booking other) {
        return employee.getId().equals(other.employee.getId()) && overlaps(other.startDate, other.endDate);
    }

    public LocalDate overlapStart(Booking other) {
        return startDate.isAfter(other.startDate) ? startDate : other.startDate;
    }

    public LocalDate overlapEnd(Booking other) {
        return endDate.isBefore(other.endDate) ? endDate : other.endDate;
    }

    /** Время защищено от проектного бронирования (правило FR9-3) */
    public boolean isProtectedTime() {
        return false;
    }

    public boolean isProjectWork() {
        return kind != BookingKind.TRAINING;
    }

    public abstract String title();
}

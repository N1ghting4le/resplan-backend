package com.resplan.domain;

import com.resplan.error.BusinessRuleException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Ресурсный конфликт – пара пересекающихся бронирований с загрузкой более 100 % (BR-08) */
@Entity
@Table(name = "resource_conflict",
        uniqueConstraints = @UniqueConstraint(columnNames = {"booking_a_id", "booking_b_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResourceConflict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "conflict_id")
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "booking_a_id")
    private Booking bookingA;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "booking_b_id")
    private Booking bookingB;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private ConflictStatus status = ConflictStatus.OPEN;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private AppUser resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    /** Пара хранится упорядоченно по идентификатору (ограничение ck_conflict_pair_order) */
    public ResourceConflict(Booking x, Booking y) {
        boolean ordered = x.getId() < y.getId();
        this.bookingA = ordered ? x : y;
        this.bookingB = ordered ? y : x;
    }

    /** UC-6: конфликт закрывается решением ресурсного менеджера; снятие брони выполняет BookingLifecycle */
    public void resolve(String note, AppUser by) {
        requireOpen();
        status = ConflictStatus.RESOLVED;
        resolvedBy = by;
        resolvedAt = Instant.now();
        resolutionNote = note;
    }

    /** UC-6: передача неразрешимого конфликта операционному директору */
    public void escalate(String note) {
        if (note == null || note.isBlank()) throw new BusinessRuleException("NOTE", "Укажите причину эскалации");
        requireOpen();
        status = ConflictStatus.ESCALATED;
        resolutionNote = note;
    }

    /** Вторая бронь пары; ошибка BOOKING, если бронь не участвует в конфликте */
    public Booking other(Booking booking) {
        if (booking.getId().equals(bookingA.getId())) return bookingB;
        if (booking.getId().equals(bookingB.getId())) return bookingA;
        throw new BusinessRuleException("BOOKING", "Бронь " + booking.getId() + " не участвует в конфликте №" + id);
    }

    private void requireOpen() {
        if (status != ConflictStatus.OPEN) {
            throw new BusinessRuleException("CONFLICT_CLOSED", "Конфликт №" + id + " уже имеет статус " + status);
        }
    }

    public int totalLoad() {
        return bookingA.getLoadPercent() + bookingB.getLoadPercent();
    }
}

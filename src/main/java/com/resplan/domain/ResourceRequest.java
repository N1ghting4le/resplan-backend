package com.resplan.domain;

import com.resplan.error.BusinessRuleException;
import com.resplan.error.IllegalTransitionException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Запрос на подбор ресурса (UC-2). Смена статуса выполняется только
 * методами агрегата, которые проверяют допустимость перехода.
 */
@Entity
@Table(name = "resource_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResourceRequest {

    public static final int MIN_REASON_LENGTH = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "task_id")
    private Task task;

    @Column(nullable = false, length = 80)
    private String role;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "grade_id")
    private Grade grade;

    @Column(name = "load_percent", nullable = false)
    private short loadPercent;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 13)
    private RequestStatus status = RequestStatus.SEARCHING;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "booking_id", unique = true)
    private ProjectBooking booking;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "request_skill", joinColumns = @JoinColumn(name = "request_id"))
    @AttributeOverride(name = "level", column = @Column(name = "min_level", nullable = false))
    private List<SkillLevel> requiredSkills = new ArrayList<>();

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RequestRejection> rejections = new ArrayList<>();

    public ResourceRequest(Task task, String role, Grade grade, int loadPercent,
                           LocalDate startDate, LocalDate endDate, AppUser createdBy) {
        if (endDate.isBefore(startDate)) throw new BusinessRuleException("PERIOD", "Дата окончания раньше даты начала");
        if (loadPercent < 1 || loadPercent > 100) throw new BusinessRuleException("LOAD", "Загрузка должна быть от 1 до 100 %");
        this.task = task;
        this.role = role;
        this.grade = grade;
        this.loadPercent = (short) loadPercent;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdBy = createdBy;
    }

    public ResourceRequest requireSkill(Skill skill, int minLevel) {
        requiredSkills.add(new SkillLevel(skill, minLevel));
        return this;
    }

    public Project project() {
        return task.getProject();
    }

    private void moveTo(RequestStatus target) {
        if (!status.canTransitionTo(target)) throw new IllegalTransitionException(status, target);
        status = target;
    }

    /** RM предлагает кандидата: мягкая бронь закрепляется за запросом */
    public void proposeCandidate(ProjectBooking softBooking) {
        moveTo(RequestStatus.PENDING_PM);
        this.booking = softBooking;
    }

    /** PM утверждает кандидата: мягкая бронь становится жесткой, задача получает исполнителя */
    public ProjectBooking approve() {
        moveTo(RequestStatus.APPROVED);
        booking.harden();
        task.assign(booking.getEmployee());
        return booking;
    }

    /** PM отклоняет кандидата: причина сохраняется, запрос возвращается на подбор */
    public ProjectBooking rejectCandidate(String reason, AppUser by) {
        if (reason == null || reason.trim().length() < MIN_REASON_LENGTH) {
            throw new BusinessRuleException("REASON", "Необходимо указать причину отклонения");
        }
        moveTo(RequestStatus.SEARCHING);
        ProjectBooking released = booking;
        rejections.add(new RequestRejection(this, released.getEmployee(), reason.trim(), by));
        booking = null;
        return released;
    }

    /** Отмена запроса при закрытии или отмене проекта; возвращает освобождаемую бронь */
    public ProjectBooking cancel() {
        if (status == RequestStatus.APPROVED) task.assign(null);
        moveTo(RequestStatus.CANCELLED);
        ProjectBooking released = booking;
        booking = null;
        return released;
    }

    /**
     * Бронь кандидата снята ресурсным менеджером (UC-6, UC-7):
     * задача теряет исполнителя, запрос возвращается на подбор
     */
    public void bookingReleased() {
        if (status == RequestStatus.APPROVED) task.assign(null);
        moveTo(RequestStatus.SEARCHING);
        booking = null;
    }

    public boolean isActive() {
        return status == RequestStatus.PENDING_PM || status == RequestStatus.APPROVED;
    }

    public void sendToExternalHire() {
        moveTo(RequestStatus.EXTERNAL_HIRE);
    }

    public Set<Integer> rejectedEmployeeIds() {
        return rejections.stream().map(r -> r.getEmployee().getId()).collect(Collectors.toSet());
    }
}

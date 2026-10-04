package com.resplan.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Отклонение кандидата проектным менеджером с обязательной причиной */
@Entity
@Table(name = "request_rejection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequestRejection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rejection_id")
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private ResourceRequest request;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(nullable = false, length = 500)
    private String reason;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    private AppUser rejectedBy;

    @Column(name = "rejected_at", nullable = false)
    private Instant rejectedAt = Instant.now();

    RequestRejection(ResourceRequest request, Employee employee, String reason, AppUser rejectedBy) {
        this.request = request;
        this.employee = employee;
        this.reason = reason;
        this.rejectedBy = rejectedBy;
    }
}

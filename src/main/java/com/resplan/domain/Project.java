package com.resplan.domain;

import com.resplan.error.AccessDeniedException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "project")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_id")
    private Integer id;

    @Column(nullable = false, unique = true, length = 12)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "pm_user_id")
    private AppUser pm;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "location_id")
    private Location location;

    /** 1 – высокий, 3 – низкий */
    @Column(nullable = false)
    private short priority;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ProjectStatus status = ProjectStatus.PLANNING;

    public Project(String code, String name, AppUser pm, Location location, int priority, LocalDate startDate) {
        this.code = code;
        this.name = name;
        this.pm = pm;
        this.location = location;
        this.priority = (short) priority;
        this.startDate = startDate;
    }

    public boolean isManagedBy(AppUser user) {
        return pm.getId().equals(user.getId());
    }

    /** Проверка права на изменение данных проекта: действие доступно только PM этого проекта */
    public void requireManagedBy(AppUser user, String action) {
        if (!isManagedBy(user)) {
            throw new AccessDeniedException(action + " может только PM проекта " + code);
        }
    }
}

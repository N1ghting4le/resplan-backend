package com.resplan.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer id;

    @Column(nullable = false, unique = true, length = 40)
    private String login;

    /** Хеш пароля BCrypt (60 символов) */
    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private UserRole role;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @OneToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", unique = true)
    private Employee employee;

    public AppUser(String login, String passwordHash, UserRole role, Employee employee) {
        this.login = login;
        this.passwordHash = passwordHash;
        this.role = role;
        this.employee = employee;
    }

    /** FR4-3: фиксация успешного входа в систему */
    public void recordLogin(Instant at) {
        this.lastLoginAt = at;
    }

    public boolean hasRole(UserRole r) {
        return role == r;
    }
}

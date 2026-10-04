package com.resplan.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "recipient_id")
    private AppUser recipient;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "is_read", nullable = false)
    private boolean read;

    public void markRead() {
        this.read = true;
    }

    public boolean isAddressedTo(AppUser user) {
        return recipient.getId().equals(user.getId());
    }

    public Notification(AppUser recipient, String message) {
        this.recipient = recipient;
        this.message = message;
    }
}

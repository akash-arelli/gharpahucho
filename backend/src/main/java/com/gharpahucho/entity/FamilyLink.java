package com.gharpahucho.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** An unguessable, revocable, read-only page about one trip for a family member. */
@Entity
@Table(name = "family_link")
@Getter
@Setter
@NoArgsConstructor
public class FamilyLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 64, unique = true)
    private String token;

    @Column(nullable = false, length = 10)
    private String language = "en";

    @Column(name = "family_name", length = 60)
    private String familyName;

    /** PLANNED, ON_THE_WAY, DELAYED or REACHED. */
    @Column(nullable = false, length = 20)
    private String status = "PLANNED";

    @Column(name = "status_updated_at")
    private Instant statusUpdatedAt;

    @Column(length = 200)
    private String message;

    @Column(nullable = false)
    private boolean revoked;

    @Column(nullable = false)
    private int views;

    @Column(name = "last_viewed_at")
    private Instant lastViewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

package com.gharpahucho.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** "Tell me when someone posts a ride on this route." Matches are computed from live trips. */
@Entity
@Table(name = "ride_alert")
@Getter
@Setter
@NoArgsConstructor
public class RideAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "source_city", nullable = false, length = 80)
    private String sourceCity;

    @Column(name = "destination_city", nullable = false, length = 80)
    private String destinationCity;

    @Column(name = "travel_date", nullable = false)
    private LocalDate travelDate;

    @Column(name = "flex_days", nullable = false)
    private int flexDays = 1;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

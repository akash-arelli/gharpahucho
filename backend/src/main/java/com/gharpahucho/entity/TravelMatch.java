package com.gharpahucho.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Cached result of the matching engine for a (trip, candidate) pair. Recomputed whenever matches are requested. */
@Entity
@Table(name = "travel_match")
@Getter
@Setter
@NoArgsConstructor
public class TravelMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_trip_id", nullable = false)
    private Trip candidateTrip;

    @Column(nullable = false)
    private int score;

    @Column(name = "breakdown_json", nullable = false, columnDefinition = "TEXT")
    private String breakdownJson;

    @Column(name = "reasons_json", nullable = false, columnDefinition = "TEXT")
    private String reasonsJson;

    @Column(name = "algorithm_version", nullable = false, length = 20)
    private String algorithmVersion;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt = Instant.now();
}

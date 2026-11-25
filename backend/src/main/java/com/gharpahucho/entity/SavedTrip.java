package com.gharpahucho.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A recommendation the user bookmarked. The option is stored as a JSON snapshot (including its data-source label). */
@Entity
@Table(name = "saved_trip")
@Getter
@Setter
@NoArgsConstructor
public class SavedTrip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(name = "source_city", nullable = false, length = 80)
    private String sourceCity;

    @Column(name = "destination_city", nullable = false, length = 80)
    private String destinationCity;

    @Column(name = "travel_date", nullable = false)
    private LocalDate travelDate;

    @Column(name = "option_snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String optionSnapshotJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

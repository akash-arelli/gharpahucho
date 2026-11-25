package com.gharpahucho.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "trip")
@Getter
@Setter
@NoArgsConstructor
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "source_city", nullable = false, length = 80)
    private String sourceCity;

    /** Coarse area name only (e.g. "Whitefield"); exact addresses are never collected. */
    @Column(name = "source_locality", length = 80)
    private String sourceLocality;

    @Column(name = "source_lat", nullable = false)
    private double sourceLat;

    @Column(name = "source_lon", nullable = false)
    private double sourceLon;

    @Column(name = "destination_city", nullable = false, length = 80)
    private String destinationCity;

    @Column(name = "destination_lat", nullable = false)
    private double destinationLat;

    @Column(name = "destination_lon", nullable = false)
    private double destinationLon;

    @Column(name = "travel_date", nullable = false)
    private LocalDate travelDate;

    @Column(name = "flex_days", nullable = false)
    private int flexDays;

    @Column(name = "return_date")
    private LocalDate returnDate;

    @Column(nullable = false)
    private int travellers = 1;

    /** Maximum budget in INR, per traveller. */
    @Column(name = "max_budget")
    private Integer maxBudget;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_mode", nullable = false, length = 20)
    private TransportMode preferredMode = TransportMode.ANY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority = Priority.CHEAPEST;

    @Column(name = "arrive_by")
    private LocalTime arriveBy;

    /** Preferred departure time; used when the trip offers seats in a shared ride. */
    @Column(name = "departure_time")
    private LocalTime departureTime;

    @Column(name = "willing_to_share", nullable = false)
    private boolean willingToShare;

    @Column(name = "seats_available", nullable = false)
    private int seatsAvailable;

    @Column(name = "cost_per_seat")
    private Integer costPerSeat;

    @Column(length = 400)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility = Visibility.PRIVATE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripStatus status = TripStatus.PLANNED;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_origin", nullable = false, length = 20)
    private DataOrigin dataOrigin = DataOrigin.USER_CREATED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

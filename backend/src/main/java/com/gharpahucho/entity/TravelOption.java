package com.gharpahucho.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.UUID;

/**
 * One row of the SIMULATED transport inventory (a daily service template). It exists so the recommendation engine
 * can be demonstrated end-to-end; it is never presented as real-time availability.
 */
@Entity
@Table(name = "travel_option")
@Getter
@Setter
@NoArgsConstructor
public class TravelOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransportMode mode;

    @Column(name = "operator_name", nullable = false, length = 80)
    private String operatorName;

    @Column(name = "service_name", nullable = false, length = 120)
    private String serviceName;

    @Column(name = "origin_city", nullable = false, length = 80)
    private String originCity;

    @Column(name = "destination_city", nullable = false, length = 80)
    private String destinationCity;

    @Column(name = "departure_time", nullable = false)
    private LocalTime departureTime;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "base_fare", nullable = false)
    private int baseFare;

    @Column(name = "comfort_score", nullable = false)
    private int comfortScore = 3;

    @Column(name = "stops_text", length = 300)
    private String stopsText;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_source", nullable = false, length = 30)
    private DataSource dataSource = DataSource.SIMULATED_INVENTORY;

    @Column(nullable = false)
    private boolean active = true;
}

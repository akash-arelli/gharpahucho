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
@Table(name = "travel_group")
@Getter
@Setter
@NoArgsConstructor
public class TravelGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "source_city", nullable = false, length = 80)
    private String sourceCity;

    @Column(name = "destination_city", nullable = false, length = 80)
    private String destinationCity;

    @Column(name = "travel_date", nullable = false)
    private LocalDate travelDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupStatus status = GroupStatus.FORMING;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_mode", nullable = false, length = 20)
    private TransportMode planMode = TransportMode.CAB;

    @Column(name = "plan_notes", length = 600)
    private String planNotes;

    @Column(name = "meeting_area", length = 120)
    private String meetingArea;

    @Column(name = "planned_departure_time")
    private LocalTime plannedDepartureTime;

    @Column(name = "max_members", nullable = false)
    private int maxMembers = 4;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

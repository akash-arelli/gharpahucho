package com.gharpahucho.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "user_preference")
@Getter
@Setter
@NoArgsConstructor
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_mode", nullable = false, length = 20)
    private TransportMode preferredMode = TransportMode.ANY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority = Priority.CHEAPEST;

    @Column(name = "default_max_budget")
    private Integer defaultMaxBudget;

    @Column(name = "willing_to_share", nullable = false)
    private boolean willingToShare = true;

    @Column(name = "first_name_only", nullable = false)
    private boolean firstNameOnly = true;

    @Column(nullable = false)
    private boolean discoverable = true;

    @Column(length = 400)
    private String notes;
}

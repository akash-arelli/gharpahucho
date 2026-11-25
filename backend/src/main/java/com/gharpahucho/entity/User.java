package com.gharpahucho.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "home_city", length = 80)
    private String homeCity;

    @Column(name = "home_locality", length = 80)
    private String homeLocality;

    @Column(name = "preferred_language", nullable = false, length = 10)
    private String preferredLanguage = "en";

    @Column(nullable = false)
    private boolean onboarded;

    @Column(name = "beta_tester", nullable = false)
    private boolean betaTester;

    @Column(name = "demo_account", nullable = false)
    private boolean demoAccount;

    /** "free" or "plus". Server-side only: it is never accepted from a request body. */
    @Column(nullable = false, length = 20)
    private String plan = "free";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Privacy-conscious public name: first name plus last initial, never the e-mail. */
    public String publicName() {
        String[] parts = displayName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }
}

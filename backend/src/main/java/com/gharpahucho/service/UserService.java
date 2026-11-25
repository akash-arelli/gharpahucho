package com.gharpahucho.service;

import com.gharpahucho.config.GharPahuchoProperties;
import com.gharpahucho.dto.UserDtos.*;
import com.gharpahucho.entity.*;
import com.gharpahucho.exception.ApiException;
import com.gharpahucho.integration.flagsmith.FeatureFlagService;
import com.gharpahucho.integration.maps.City;
import com.gharpahucho.integration.maps.GeoService;
import com.gharpahucho.mapper.Mappers;
import com.gharpahucho.repository.TripRepository;
import com.gharpahucho.repository.UserPreferenceRepository;
import com.gharpahucho.repository.UserRepository;
import com.gharpahucho.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    public static final String DEMO_LOGIN_EMAIL = "priya.demo@gharpahucho.example";

    private final UserRepository users;
    private final UserPreferenceRepository prefs;
    private final TripRepository trips;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final GeoService geo;
    private final FeatureFlagService flags;
    private final GharPahuchoProperties props;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this e-mail already exists");
        }
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setDisplayName(req.displayName().trim());
        if (req.homeCity() != null && !req.homeCity().isBlank()) {
            u.setHomeCity(geo.requireCity(req.homeCity(), "home city").name());
        }
        users.save(u);
        UserPreference p = new UserPreference();
        p.setUser(u);
        prefs.save(p);
        return new AuthResponse(jwt.issue(u.getId(), u.getEmail()), Mappers.toMe(u, p));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User u = users.findByEmailIgnoreCase(req.email().trim())
                .filter(x -> encoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid e-mail or password"));
        return new AuthResponse(jwt.issue(u.getId(), u.getEmail()), Mappers.toMe(u, prefs.findByUserId(u.getId()).orElse(null)));
    }

    /** One-click login as the seeded demo traveller. Only available while demo mode is on. */
    @Transactional(readOnly = true)
    public AuthResponse demoLogin() {
        if (!props.getDemo().isEnabled() || !props.getDemo().isResetEndpointEnabled()) {
            throw ApiException.forbidden("Demo login is disabled");
        }
        User u = users.findByEmailIgnoreCase(DEMO_LOGIN_EMAIL)
                .orElseThrow(() -> ApiException.notFound("The demo traveller (demo data has not been seeded)"));
        return new AuthResponse(jwt.issue(u.getId(), u.getEmail()), Mappers.toMe(u, prefs.findByUserId(u.getId()).orElse(null)));
    }

    @Transactional(readOnly = true)
    public Me me(UUID userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        return Mappers.toMe(u, prefs.findByUserId(userId).orElse(null));
    }

    @Transactional
    public Me update(UUID userId, UpdateProfileRequest req) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (req.displayName() != null) {
            u.setDisplayName(req.displayName().trim());
        }
        if (req.homeCity() != null) {
            if (req.homeCity().isBlank()) {
                u.setHomeCity(null);
                u.setHomeLocality(null);
            } else {
                City city = geo.requireCity(req.homeCity(), "home city");
                u.setHomeCity(city.name());
            }
        }
        if (req.homeLocality() != null) {
            u.setHomeLocality(req.homeLocality().isBlank() ? null : req.homeLocality().trim());
        }
        if (req.preferredLanguage() != null) {
            u.setPreferredLanguage(req.preferredLanguage());
        }
        if (req.betaTester() != null) {
            u.setBetaTester(req.betaTester());
        }
        if (req.onboarded() != null) {
            u.setOnboarded(req.onboarded());
        }
        UserPreference p = prefs.findByUserId(userId).orElseGet(() -> {
            UserPreference np = new UserPreference();
            np.setUser(u);
            return np;
        });
        if (req.preferences() != null) {
            PreferenceDto d = req.preferences();
            p.setPreferredMode(d.preferredMode() == null ? TransportMode.ANY : d.preferredMode());
            p.setPriority(d.priority() == null ? Priority.CHEAPEST : d.priority());
            p.setDefaultMaxBudget(d.defaultMaxBudget());
            p.setWillingToShare(d.willingToShare());
            p.setFirstNameOnly(d.firstNameOnly());
            p.setDiscoverable(d.discoverable());
            p.setNotes(d.notes());
        }
        prefs.save(p);
        users.save(u);
        flags.invalidate(userId); // traits (city, language, beta_tester) changed: re-evaluate flags
        return Mappers.toMe(u, p);
    }

    @Transactional(readOnly = true)
    public PublicProfile publicProfile(UUID userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("Traveller"));
        UserPreference pref = prefs.findByUserId(userId).orElse(null);
        if (pref != null && !pref.isDiscoverable()) {
            throw ApiException.notFound("Traveller");
        }
        List<PublicTrip> upcoming = trips.findByOwnerIdAndStatusAndTravelDateGreaterThanEqualOrderByTravelDateAsc(
                        userId, TripStatus.PLANNED, LocalDate.now()).stream()
                .filter(t -> t.getVisibility() == Visibility.DISCOVERABLE)
                .map(t -> new PublicTrip(t.getId(), t.getSourceCity(),
                        t.getSourceLocality() == null ? t.getSourceCity() : t.getSourceLocality(),
                        t.getDestinationCity(), t.getTravelDate(), t.getPreferredMode(), t.getSeatsAvailable(), t.getCostPerSeat()))
                .toList();
        return new PublicProfile(u.getId(), Mappers.publicName(u, pref), Mappers.homeArea(u), u.getPreferredLanguage(), u.getCreatedAt(), upcoming);
    }
}

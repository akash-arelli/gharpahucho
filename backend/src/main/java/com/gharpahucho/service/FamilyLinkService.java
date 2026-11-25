package com.gharpahucho.service;

import com.gharpahucho.dto.GrowthDtos.CreateFamilyLinkRequest;
import com.gharpahucho.dto.GrowthDtos.FamilyLinkDto;
import com.gharpahucho.dto.GrowthDtos.FamilyView;
import com.gharpahucho.entity.FamilyLink;
import com.gharpahucho.entity.Trip;
import com.gharpahucho.exception.ApiException;
import com.gharpahucho.repository.FamilyLinkRepository;
import com.gharpahucho.repository.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Family link: a read-only page for a parent or relative, in their language. It shows the traveller's first name,
 * the route, the date and a status the traveller sets. Never a phone number, e-mail or exact location. Links are
 * unguessable, revocable, and stop working a few days after the trip.
 */
@Service
public class FamilyLinkService {

    static final Set<String> STATUSES = Set.of("PLANNED", "ON_THE_WAY", "DELAYED", "REACHED");
    static final Set<String> LANGUAGES = Set.of("en", "hi", "te");
    static final int MAX_LINKS_PER_TRIP = 5;
    static final int DAYS_VISIBLE_AFTER_TRIP = 3;
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{20,64}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FamilyLinkRepository links;
    private final TripRepository trips;
    private final Clock clock;

    public FamilyLinkService(FamilyLinkRepository links, TripRepository trips, Clock clock) {
        this.links = links;
        this.trips = trips;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FamilyLinkDto> list(UUID userId, UUID tripId) {
        ownedTrip(userId, tripId);
        return links.findByTripIdAndOwnerIdAndRevokedFalseOrderByCreatedAtDesc(tripId, userId).stream().map(FamilyLinkService::toDto).toList();
    }

    @Transactional
    public FamilyLinkDto create(UUID userId, UUID tripId, CreateFamilyLinkRequest req) {
        Trip t = ownedTrip(userId, tripId);
        if (links.findByTripIdAndOwnerIdAndRevokedFalseOrderByCreatedAtDesc(tripId, userId).size() >= MAX_LINKS_PER_TRIP) {
            throw ApiException.conflict("You already have " + MAX_LINKS_PER_TRIP + " family links for this journey. Stop sharing one first.");
        }
        FamilyLink l = new FamilyLink();
        l.setTrip(t);
        l.setOwner(t.getOwner());
        l.setToken(newToken());
        l.setLanguage(language(req == null ? null : req.language()));
        l.setFamilyName(clean(req == null ? null : req.familyName(), 60));
        l.setMessage(clean(req == null ? null : req.message(), 200));
        l.setStatusUpdatedAt(Instant.now());
        links.save(l);
        return toDto(l);
    }

    @Transactional
    public FamilyLinkDto updateStatus(UUID userId, UUID linkId, String status, String message) {
        FamilyLink l = links.findByIdAndOwnerId(linkId, userId).filter(x -> !x.isRevoked()).orElseThrow(() -> ApiException.notFound("Family link"));
        String s = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(s)) {
            throw ApiException.badRequest("Status must be one of " + String.join(", ", STATUSES));
        }
        l.setStatus(s);
        l.setMessage(clean(message, 200));
        l.setStatusUpdatedAt(Instant.now());
        return toDto(l);
    }

    @Transactional
    public void revoke(UUID userId, UUID linkId) {
        links.findByIdAndOwnerId(linkId, userId).orElseThrow(() -> ApiException.notFound("Family link")).setRevoked(true);
    }

    /** Public read. Unknown, revoked and expired tokens all answer the same 404. */
    @Transactional
    public FamilyView view(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw ApiException.notFound("Link");
        }
        FamilyLink l = links.findByToken(token).orElseThrow(() -> ApiException.notFound("Link"));
        Trip t = l.getTrip();
        if (l.isRevoked() || LocalDate.now(clock).isAfter(t.getTravelDate().plusDays(DAYS_VISIBLE_AFTER_TRIP))) {
            throw ApiException.notFound("Link");
        }
        l.setViews(l.getViews() + 1);
        l.setLastViewedAt(Instant.now());
        return render(l, t);
    }

    static FamilyView render(FamilyLink l, Trip t) {
        String first = t.getOwner().getDisplayName().trim().split("\\s+")[0];
        String lang = language(l.getLanguage());
        return new FamilyView(first, l.getFamilyName(), t.getSourceCity(), t.getDestinationCity(), t.getTravelDate(), t.getDepartureTime(),
                t.getSourceLocality(), l.getStatus(), FamilyTexts.statusLabel(lang, l.getStatus()),
                FamilyTexts.headline(lang, first, t.getDestinationCity(), l.getStatus()), l.getMessage(),
                l.getStatusUpdatedAt() == null ? l.getCreatedAt() : l.getStatusUpdatedAt(), lang, FamilyTexts.footer(lang, first));
    }

    private Trip ownedTrip(UUID userId, UUID tripId) {
        return trips.findWithOwner(tripId).filter(t -> t.getOwner().getId().equals(userId)).orElseThrow(() -> ApiException.notFound("Trip"));
    }

    static FamilyLinkDto toDto(FamilyLink l) {
        return new FamilyLinkDto(l.getId(), l.getToken(), "/f/" + l.getToken(), l.getLanguage(), l.getFamilyName(), l.getStatus(),
                l.getStatusUpdatedAt(), l.getMessage(), l.getViews(), l.getLastViewedAt(), l.getCreatedAt());
    }

    static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String language(String raw) {
        String l = raw == null ? "en" : raw.trim().toLowerCase(Locale.ROOT);
        return LANGUAGES.contains(l) ? l : "en";
    }

    private static String clean(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim().replaceAll("[\\p{Cntrl}]", " ");
        return t.length() > max ? t.substring(0, max) : t;
    }
}

package com.gharpahucho.service;

import com.gharpahucho.config.GharPahuchoProperties;
import com.gharpahucho.dto.GrowthDtos.Perk;
import com.gharpahucho.dto.GrowthDtos.PlansResponse;
import com.gharpahucho.dto.UserDtos.Me;
import com.gharpahucho.entity.User;
import com.gharpahucho.exception.ApiException;
import com.gharpahucho.integration.flagsmith.FeatureFlagService;
import com.gharpahucho.integration.flagsmith.FeatureKey;
import com.gharpahucho.integration.flagsmith.RuntimeConfig;
import com.gharpahucho.mapper.Mappers;
import com.gharpahucho.repository.UserPreferenceRepository;
import com.gharpahucho.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The Festival Pass. There is deliberately no payment provider here: in demo mode the checkout simply upgrades the
 * account (and says so in the activity feed). Entitlements are not stored in Flagsmith by this service; it only
 * updates the user's {@code plan} trait, and Flagsmith segments ("Plus members") decide which features switch on.
 */
@Service
public class BillingService {

    private final UserRepository users;
    private final UserPreferenceRepository prefs;
    private final FeatureFlagService flags;
    private final ActivityService activity;
    private final GharPahuchoProperties props;

    public BillingService(UserRepository users, UserPreferenceRepository prefs, FeatureFlagService flags, ActivityService activity,
                          GharPahuchoProperties props) {
        this.users = users;
        this.prefs = prefs;
        this.flags = flags;
        this.activity = activity;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public PlansResponse plans(UUID userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        RuntimeConfig c = flags.config(userId);
        return new PlansResponse(u.getPlan(), c.passPriceInr(), "per festival season", perks(c), checkoutAvailable(userId));
    }

    @Transactional
    public Me demoCheckout(UUID userId) {
        if (!checkoutAvailable(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CHECKOUT_UNAVAILABLE", "Demo checkout is not available in this environment");
        }
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (!"plus".equals(u.getPlan())) {
            u.setPlan("plus");
            activity.record(userId, "PLAN_UPGRADED", "Festival Pass activated (demo checkout, no payment taken)");
        }
        flags.invalidate(userId);
        return Mappers.toMe(u, prefs.findByUserId(userId).orElse(null));
    }

    @Transactional
    public Me demoCancel(UUID userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if ("plus".equals(u.getPlan())) {
            u.setPlan("free");
            activity.record(userId, "PLAN_CANCELLED", "Back on the free plan");
        }
        flags.invalidate(userId);
        return Mappers.toMe(u, prefs.findByUserId(userId).orElse(null));
    }

    private boolean checkoutAvailable(UUID userId) {
        return props.getDemo().isEnabled() && flags.isEnabled(FeatureKey.DEMO_CHECKOUT, userId);
    }

    static List<Perk> perks(RuntimeConfig c) {
        return List.of(
                new Perk("When-to-book plan", "Day-by-day crowd levels, when to shift your date, and every booking step for your journey.",
                        "Booking date only", "Full plan"),
                new Perk("Booking radar", "One screen showing which of your journeys need action this week.",
                        c.freeRadarTrips() + (c.freeRadarTrips() == 1 ? " journey" : " journeys"), "All journeys"),
                new Perk("Ride alerts", "Get told the moment someone posts a ride on your route and date.",
                        c.freeRideAlerts() + (c.freeRideAlerts() == 1 ? " alert" : " alerts"), "Unlimited"),
                new Perk("Family link", "A page in Hindi, Telugu or English that your family can open to see you are on the way.",
                        "Not included", "Included"));
    }
}

package com.gharpahucho.integration.flagsmith;

/**
 * Flagsmith remote-config values (feature VALUES) that tune the algorithms without redeploying.
 * A value is honoured when its feature is enabled in Flagsmith; otherwise the documented default applies.
 */
public enum ConfigKey {

    MATCHING_RADIUS_KM("matching_radius_km", "Matching radius (km)",
            "Maximum distance between starting points / destinations before the proximity score reaches zero."),
    DATE_FLEXIBILITY_DAYS("date_flexibility_days", "Date flexibility (days)",
            "Extra days either side of a travel date that are still considered compatible."),
    MAX_BUDGET_DEVIATION_PCT("max_budget_deviation_pct", "Max budget deviation (%)",
            "How far over budget an option may be and still be offered (clearly flagged) as an alternative."),
    MIN_COMPATIBILITY_SCORE("min_compatibility_score", "Min compatibility score",
            "Travellers scoring below this (0-100) are not shown."),
    MAX_RECOMMENDATIONS("max_recommendations", "Max recommendations",
            "Upper bound on the number of travel options returned."),
    MATCHING_WEIGHTS("matching_weights", "Matching weights (JSON)",
            "Weights for destination, date, start, budget and transport, e.g. {\"destination\":35,\"date\":25,\"start\":20,\"budget\":10,\"transport\":10}."),
    RAIL_BOOKING_WINDOW_DAYS("rail_booking_window_days", "Rail advance booking window (days)",
            "How many days before travel train bookings open. Indian Railways changes this from time to time, so it lives in Flagsmith and can be edited without a deploy."),
    FREE_RADAR_TRIPS("free_radar_trips", "Free booking-radar journeys",
            "How many upcoming journeys the free plan sees in the dashboard booking radar. Plus members see all of them."),
    FREE_RIDE_ALERTS("free_ride_alerts", "Free ride alerts",
            "How many active ride alerts a free-plan traveller may keep. Plus members are unlimited (flag unlimited_ride_alerts)."),
    FESTIVAL_PASS_PRICE_INR("festival_pass_price_inr", "Festival Pass price (INR)",
            "Price shown on the pass page. Change it in Flagsmith, or target a segment to A/B test pricing.");

    private final String key;
    private final String title;
    private final String description;

    ConfigKey(String key, String title, String description) {
        this.key = key;
        this.title = title;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }
}

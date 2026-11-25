package com.gharpahucho.integration.flagsmith;

/**
 * The ten Flagsmith feature flags that drive GharPahucho. The {@code key} is the exact feature name that must exist in
 * the Flagsmith project (see scripts/flagsmith-bootstrap.mjs which creates them through the Flagsmith Management API).
 */
public enum FeatureKey {

    SMART_TRIP_PLANNER("smart_trip_planner",
            "Smart Trip Planner",
            "Master switch for the trip planner and recommendation engine.",
            "OFF: /api/trips/plan returns 403 and the planner page shows an 'unavailable' state."),

    AI_VOICE_TRIP_CREATION("ai_voice_trip_creation",
            "Voice trip creation",
            "Lets travellers speak their trip (Sarvam speech-to-text).",
            "OFF: microphone buttons disappear and /api/assistant/voice returns 403."),

    SARVAM_MULTILINGUAL_ASSISTANT("sarvam_multilingual_assistant",
            "Sarvam multilingual assistant",
            "Uses Sarvam AI to understand and answer in Telugu, Hindi and other Indian languages.",
            "OFF: the assistant falls back to deterministic English-only parsing and templated replies."),

    TRAVEL_COMPANION_MATCHING("travel_companion_matching",
            "Travel companion matching",
            "Discover travellers on similar routes and dates.",
            "OFF: the Travellers area, match API and dashboard suggestions are hidden and blocked."),

    SHARED_RIDE_MATCHING("shared_ride_matching",
            "Shared ride matching",
            "Includes seats offered by other travellers as options and boosts sharing-compatible matches.",
            "OFF: traveller-published shared rides are removed from recommendations and ride-sharing is ignored in match scores."),

    ALTERNATIVE_ROUTE_RECOMMENDATIONS("alternative_route_recommendations",
            "Alternative routes",
            "Suggests one-change journeys and other modes when direct options fail your constraints.",
            "OFF: only direct options are returned; no connections or fallback alternatives."),

    BUDGET_OPTIMIZATION("budget_optimization",
            "Budget optimisation",
            "Finds cheaper dates and modes and tells you how much you would save.",
            "OFF: no budget tips are generated."),

    TRAVEL_GROUP_CREATION("travel_group_creation",
            "Travel groups",
            "Create travel groups, invite companions and chat.",
            "OFF: group creation, invitations and group chat are blocked."),

    EXPERIMENTAL_MATCHING_ALGORITHM("experimental_matching_algorithm",
            "Experimental matching (v2)",
            "Corridor-aware matching that also finds travellers heading along the same road. Ideal for gradual rollout.",
            "ON for a user: matches use algorithm v2 (route-corridor aware) and are labelled 'experimental'."),

    AI_TRIP_EXPLANATIONS("ai_trip_explanations",
            "AI trip explanations",
            "Asks Sarvam AI to explain why the top option fits you, grounded in the engine's facts.",
            "OFF: recommendations keep deterministic reasons only; no AI explanation is requested."),

    FESTIVAL_BOOKING_PLAN("festival_booking_plan",
            "Festival booking plan (paid)",
            "Full when-to-book plan: day-by-day crowding around your date, date-shift advice and every step. Granted to the 'Plus members' segment (trait plan = plus).",
            "OFF for a user: they still see when booking opens, but the date comparison and later steps are locked behind the Festival Pass."),

    RIDE_ALERTS("ride_alerts",
            "Ride alerts",
            "Tell me when a traveller posts a ride on my route. Kill switch for the whole feature.",
            "OFF: alerts cannot be created and no alert notifications are produced."),

    UNLIMITED_RIDE_ALERTS("unlimited_ride_alerts",
            "Unlimited ride alerts (paid)",
            "Removes the free-plan limit (config free_ride_alerts). Granted to the 'Plus members' segment.",
            "OFF for a user: they can keep at most 'free_ride_alerts' active alerts."),

    FAMILY_LINK("family_link",
            "Family link (paid)",
            "A read-only trip page for family, in their language, with 'on the way / reached' updates. Granted to the 'Plus members' segment.",
            "OFF for a user: creating a family link returns 403 and the UI shows the Festival Pass offer."),

    DEMO_CHECKOUT("demo_checkout",
            "Demo checkout",
            "Lets demo accounts unlock the Festival Pass without a payment. Also requires GP_DEMO_MODE=true on the server.",
            "OFF: the pass page shows no checkout button. Keep OFF in production.");

    private final String key;
    private final String title;
    private final String description;
    private final String effect;

    FeatureKey(String key, String title, String description, String effect) {
        this.key = key;
        this.title = title;
        this.description = description;
        this.effect = effect;
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

    public String effect() {
        return effect;
    }
}

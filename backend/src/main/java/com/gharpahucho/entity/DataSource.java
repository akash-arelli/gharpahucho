package com.gharpahucho.entity;

/**
 * Provenance of every travel result. The UI shows this label on every card so users (and judges) can always tell
 * measured data from user-generated data from demonstration data.
 */
public enum DataSource {
    /** Distance/duration come from a real routing engine (OSRM or Google Routes); the fare is a labelled estimate. */
    ROUTE_CALCULATION("Route calculation", "Distance and duration computed by a routing engine. Fare is an estimate."),
    /** A real journey published by another GharPahucho user. */
    USER_TRIP("Traveller-published", "Published by another traveller on GharPahucho. Not verified by GharPahucho."),
    /** Sample timetable/fare data used for demonstration. NEVER real-time availability. */
    SIMULATED_INVENTORY("Sample data", "Simulated schedule and fare for demonstration. Not real-time availability."),
    /** Reserved for future authorised provider integrations. */
    LIVE_PROVIDER("Live provider", "Fetched from an authorised provider API.");

    private final String label;
    private final String description;

    DataSource(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}

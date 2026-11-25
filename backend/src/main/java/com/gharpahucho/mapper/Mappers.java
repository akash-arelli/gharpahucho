package com.gharpahucho.mapper;

import com.gharpahucho.dto.TripDtos.TripDto;
import com.gharpahucho.dto.UserDtos.Me;
import com.gharpahucho.dto.UserDtos.PreferenceDto;
import com.gharpahucho.entity.Trip;
import com.gharpahucho.entity.User;
import com.gharpahucho.entity.UserPreference;

/** Entity to DTO conversion. DTOs are the only thing that ever leaves the API; entities never do. */
public final class Mappers {
    private Mappers() {
    }

    public static TripDto toDto(Trip t) {
        return new TripDto(t.getId(), t.getSourceCity(), t.getSourceLocality(), t.getDestinationCity(), t.getTravelDate(),
                t.getFlexDays(), t.getReturnDate(), t.getTravellers(), t.getMaxBudget(), t.getPreferredMode(), t.getPriority(),
                t.getArriveBy(), t.getDepartureTime(), t.isWillingToShare(), t.getSeatsAvailable(), t.getCostPerSeat(),
                t.getNotes(), t.getVisibility(), t.getStatus(), t.getDataOrigin(), t.getCreatedAt());
    }

    public static PreferenceDto toDto(UserPreference p) {
        return new PreferenceDto(p.getPreferredMode(), p.getPriority(), p.getDefaultMaxBudget(), p.isWillingToShare(),
                p.isFirstNameOnly(), p.isDiscoverable(), p.getNotes());
    }

    public static Me toMe(User u, UserPreference p) {
        return new Me(u.getId(), u.getEmail(), u.getDisplayName(), u.getHomeCity(), u.getHomeLocality(), u.getPreferredLanguage(),
                u.isOnboarded(), u.isBetaTester(), u.isDemoAccount(), u.getPlan(), p == null ? null : toDto(p), u.getCreatedAt());
    }

    /** Public name honouring the user's privacy setting: first name only (default) or first name plus last initial. */
    public static String publicName(User u, UserPreference pref) {
        boolean firstOnly = pref == null || pref.isFirstNameOnly();
        String first = u.getDisplayName().trim().split("\\s+")[0];
        return firstOnly ? first : u.publicName();
    }

    /** Coarse area only, e.g. "Whitefield, Bengaluru". Never an address. */
    public static String homeArea(User u) {
        if (u.getHomeLocality() != null && !u.getHomeLocality().isBlank() && u.getHomeCity() != null) {
            return u.getHomeLocality() + ", " + u.getHomeCity();
        }
        return u.getHomeCity() == null ? "India" : u.getHomeCity();
    }
}

package com.gharpahucho.integration.transport;

import com.gharpahucho.integration.maps.City;
import com.gharpahucho.integration.maps.RouteInfo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Everything a provider needs to answer "what can I take from A to B around this date?".
 *
 * @param searchFrom first departure date to consider (the day before when an arrival deadline is set, so overnight
 *                   services that arrive on the travel date are found)
 * @param searchTo   last departure date to consider
 */
public record SearchContext(
        City from,
        City to,
        LocalDate travelDate,
        LocalDate searchFrom,
        LocalDate searchTo,
        LocalTime arriveBy,
        int travellers,
        RouteInfo route,
        UUID userId,
        LocalDateTime now,
        double radiusKm,
        int flexDays) {

    public LocalDateTime deadline() {
        return arriveBy == null ? null : travelDate.atTime(arriveBy);
    }

    public SearchContext withDates(LocalDate newTravelDate) {
        long shift = java.time.temporal.ChronoUnit.DAYS.between(travelDate, newTravelDate);
        return new SearchContext(from, to, newTravelDate, searchFrom.plusDays(shift), searchTo.plusDays(shift), arriveBy,
                travellers, route, userId, now, radiusKm, flexDays);
    }
}

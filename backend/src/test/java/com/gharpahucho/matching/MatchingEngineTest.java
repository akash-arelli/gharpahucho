package com.gharpahucho.matching;

import com.gharpahucho.entity.TransportMode;
import com.gharpahucho.entity.Trip;
import com.gharpahucho.entity.User;
import com.gharpahucho.integration.flagsmith.RuntimeConfig;
import com.gharpahucho.matching.MatchingEngine.Params;
import com.gharpahucho.matching.MatchingEngine.Result;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingEngineTest {

    private static final LocalDate DAY = LocalDate.of(2026, 12, 20);
    private final MatchingEngine engine = new MatchingEngine();
    private final Params params = new Params(RuntimeConfig.defaults(), true, false);

    private static Trip trip(String from, double fLat, double fLon, String to, double tLat, double tLon, LocalDate date, Integer budget, TransportMode mode) {
        Trip t = new Trip();
        t.setOwner(new User());
        t.setSourceCity(from);
        t.setSourceLat(fLat);
        t.setSourceLon(fLon);
        t.setDestinationCity(to);
        t.setDestinationLat(tLat);
        t.setDestinationLon(tLon);
        t.setTravelDate(date);
        t.setMaxBudget(budget);
        t.setPreferredMode(mode);
        t.setWillingToShare(true);
        return t;
    }

    private static Trip blrToHyd(LocalDate date, Integer budget, TransportMode mode) {
        return trip("Bengaluru", 12.9716, 77.5946, "Hyderabad", 17.3850, 78.4867, date, budget, mode);
    }

    @Test
    void identicalJourneysScoreHigh() {
        Optional<Result> r = engine.score(blrToHyd(DAY, 1500, TransportMode.ANY), blrToHyd(DAY, 1500, TransportMode.ANY), params);
        assertThat(r).isPresent();
        assertThat(r.get().score()).isGreaterThanOrEqualTo(90);
        assertThat(r.get().breakdown()).containsEntry("destination", 100).containsEntry("date", 100);
        assertThat(r.get().reasons()).isNotEmpty();
    }

    @Test
    void scoreIsAlwaysWithinZeroAndHundred() {
        Result r = engine.score(blrToHyd(DAY, 100, TransportMode.TRAIN), blrToHyd(DAY.plusDays(1), 9000, TransportMode.BUS), params).orElseThrow();
        assertThat(r.score()).isBetween(0, 100);
        r.breakdown().values().forEach(v -> assertThat(v).isBetween(0, 100));
    }

    @Test
    void differentDestinationIsNeverAMatch() {
        Trip other = trip("Bengaluru", 12.9716, 77.5946, "Chennai", 13.0827, 80.2707, DAY, 1500, TransportMode.ANY);
        assertThat(engine.score(blrToHyd(DAY, 1500, TransportMode.ANY), other, params)).isEmpty();
    }

    @Test
    void datesOutsideTheFlexibilityWindowAreNotAMatch() {
        assertThat(engine.score(blrToHyd(DAY, 1500, TransportMode.ANY), blrToHyd(DAY.plusDays(10), 1500, TransportMode.ANY), params)).isEmpty();
    }

    @Test
    void closerDatesScoreHigherThanFurtherDates() {
        Trip seeker = blrToHyd(DAY, 1500, TransportMode.ANY);
        int same = engine.score(seeker, blrToHyd(DAY, 1500, TransportMode.ANY), params).orElseThrow().score();
        int plusOne = engine.score(seeker, blrToHyd(DAY.plusDays(1), 1500, TransportMode.ANY), params).orElseThrow().score();
        int plusTwo = engine.score(seeker, blrToHyd(DAY.plusDays(2), 1500, TransportMode.ANY), params).orElseThrow().score();
        assertThat(same).isGreaterThan(plusOne);
        assertThat(plusOne).isGreaterThan(plusTwo);
    }

    @Test
    void weightsFromFlagsmithChangeTheOutcome() {
        Trip seeker = blrToHyd(DAY, 1500, TransportMode.TRAIN);
        Trip other = blrToHyd(DAY, 1500, TransportMode.BUS);
        RuntimeConfig transportHeavy = new RuntimeConfig(25, 2, 20, 40, 12, RuntimeConfig.MatchWeights.normalised(5, 5, 5, 5, 80));
        int defaultScore = engine.score(seeker, other, params).orElseThrow().score();
        int heavy = engine.score(seeker, other, new Params(transportHeavy, true, false)).orElseThrow().score();
        assertThat(heavy).isLessThan(defaultScore);
    }

    @Test
    void weightsAreNormalisedWhateverIsConfigured() {
        RuntimeConfig.MatchWeights w = RuntimeConfig.MatchWeights.normalised(350, 250, 200, 100, 100);
        assertThat(w.destination() + w.date() + w.start() + w.budget() + w.transport()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        RuntimeConfig.MatchWeights zeros = RuntimeConfig.MatchWeights.normalised(0, 0, 0, 0, 0);
        assertThat(zeros.destination() + zeros.date() + zeros.start() + zeros.budget() + zeros.transport()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void experimentalAlgorithmCanMatchAStartAlongTheCorridor() {
        // Mysuru is ~140 km from Bengaluru: outside v1's reachable radius, but on the way to nowhere near Hyderabad,
        // so use Tumakuru (on the Bengaluru-Hyderabad highway, ~70 km out).
        Trip seeker = blrToHyd(DAY, 1500, TransportMode.ANY);
        Trip alongRoute = trip("Tumakuru", 13.3379, 77.1173, "Hyderabad", 17.3850, 78.4867, DAY, 1500, TransportMode.ANY);
        Optional<Result> v1 = engine.score(seeker, alongRoute, params);
        Optional<Result> v2 = engine.score(seeker, alongRoute, new Params(RuntimeConfig.defaults(), true, true));
        assertThat(v2).isPresent();
        assertThat(v2.get().algorithm()).isEqualTo(MatchingEngine.V2);
        v1.ifPresent(r -> assertThat(v2.get().score()).isGreaterThanOrEqualTo(r.score()));
    }
}

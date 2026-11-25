package com.gharpahucho.recommendation;

import com.gharpahucho.entity.DataSource;
import com.gharpahucho.entity.Priority;
import com.gharpahucho.entity.TransportMode;
import com.gharpahucho.integration.transport.Candidate;
import com.gharpahucho.recommendation.RecommendationEngine.Criteria;
import com.gharpahucho.recommendation.RecommendationEngine.Scored;
import com.gharpahucho.recommendation.RecommendationEngine.Settings;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationEngineTest {

    private static final LocalDate DAY = LocalDate.of(2026, 12, 20);
    private final RecommendationEngine engine = new RecommendationEngine();
    private final Settings settings = new Settings(12, 20, true);

    private static Candidate option(String id, TransportMode mode, int hourDep, int minutes, int cost, int transfers) {
        LocalDateTime dep = DAY.atTime(hourDep, 0);
        return new Candidate(id, mode, "Op " + id, id, dep, dep.plusMinutes(minutes), cost, transfers, "", DataSource.SIMULATED_INVENTORY,
                "SAMPLE_UNVERIFIED", "Sample", 3, null, null, null,
                List.of(new Candidate.Leg(mode, "A", "B", dep, dep.plusMinutes(minutes), "Op", id)), null);
    }

    private final List<Candidate> pool = List.of(
            option("cheap", TransportMode.BUS, 20, 840, 500, 0),
            option("fast", TransportMode.TRAIN, 6, 540, 1400, 0),
            option("middle", TransportMode.BUS, 21, 760, 900, 0),
            option("changes", TransportMode.TRAIN, 6, 900, 650, 1));

    private Criteria criteria(Integer budget, Priority priority) {
        return new Criteria(DAY, null, 1, budget, TransportMode.ANY, priority);
    }

    private static Scored byId(List<Scored> list, String id) {
        return list.stream().filter(s -> s.candidate().id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void taggingMarksCheapestFastestAndFewestChanges() {
        List<Scored> ranked = engine.rank(criteria(null, Priority.CHEAPEST), pool, settings);
        assertThat(byId(ranked, "cheap").tags()).contains(RecommendationEngine.TAG_CHEAPEST);
        assertThat(byId(ranked, "fast").tags()).contains(RecommendationEngine.TAG_FASTEST);
        assertThat(ranked.stream().anyMatch(s -> s.tags().contains(RecommendationEngine.TAG_BEST_OVERALL))).isTrue();
    }

    @Test
    void cheapestPriorityRanksTheCheapestFirstAndFastestPriorityTheFastest() {
        assertThat(engine.rank(criteria(null, Priority.CHEAPEST), pool, settings).get(0).candidate().id()).isEqualTo("cheap");
        assertThat(engine.rank(criteria(null, Priority.FASTEST), pool, settings).get(0).candidate().id()).isEqualTo("fast");
    }

    @Test
    void anOptionOverBudgetIsNeverPresentedAsWithinBudget() {
        List<Scored> ranked = engine.rank(criteria(1000, Priority.CHEAPEST), pool, settings);
        Scored fast = byId(ranked, "fast");
        assertThat(fast.meetsConstraints()).isFalse();
        assertThat(fast.violations()).isNotEmpty();
        assertThat(fast.tags()).doesNotContain(RecommendationEngine.TAG_WITHIN_BUDGET);
    }

    @Test
    void compliantOptionsAlwaysOutrankViolatingOnes() {
        List<Scored> ranked = engine.rank(criteria(1000, Priority.FASTEST), pool, settings);
        boolean seenViolation = false;
        for (Scored s : ranked) {
            if (!s.meetsConstraints()) {
                seenViolation = true;
            } else {
                assertThat(seenViolation).as("a compliant option must not appear after a violating one").isFalse();
            }
        }
    }

    @Test
    void whenNothingFitsTheClosestAreOfferedAndLabelledAsAlternatives() {
        List<Scored> ranked = engine.rank(criteria(300, Priority.CHEAPEST), pool, settings);
        assertThat(ranked).isNotEmpty();
        assertThat(ranked).allSatisfy(s -> {
            assertThat(s.meetsConstraints()).isFalse();
            assertThat(s.tags()).contains(RecommendationEngine.TAG_ALTERNATIVE);
        });
    }

    @Test
    void anArrivalDeadlineIsAHardConstraint() {
        LocalDateTime deadline = DAY.atTime(18, 0);
        Criteria c = new Criteria(DAY, deadline, 1, null, TransportMode.ANY, Priority.CHEAPEST);
        List<Scored> ranked = engine.rank(c, pool, settings);
        // "cheap" leaves at 20:00 and arrives the next morning: too late, so it is only an alternative.
        assertThat(byId(ranked, "cheap").meetsConstraints()).isFalse();
        assertThat(byId(ranked, "cheap").violations().get(0)).contains("after your required");
        // "fast" leaves at 06:00 and arrives 15:00: on time.
        assertThat(byId(ranked, "fast").meetsConstraints()).isTrue();
        assertThat(byId(ranked, "fast").tags()).contains(RecommendationEngine.TAG_ARRIVES_ON_TIME);
    }

    @Test
    void respectsTheMaxRecommendationsLimit() {
        assertThat(engine.rank(criteria(null, Priority.CHEAPEST), pool, new Settings(2, 20, true))).hasSize(2);
    }

    @Test
    void scoresAreBetweenZeroAndHundred() {
        engine.rank(criteria(null, Priority.COMFORTABLE), pool, settings).forEach(s -> {
            assertThat(s.scorePercent()).isBetween(0, 100);
            assertThat(s.overallPercent()).isBetween(0, 100);
        });
    }

    @Test
    void emptyInputGivesEmptyOutput() {
        assertThat(engine.rank(criteria(null, Priority.CHEAPEST), List.of(), settings)).isEmpty();
    }
}

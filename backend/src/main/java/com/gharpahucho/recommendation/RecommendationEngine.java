package com.gharpahucho.recommendation;

import com.gharpahucho.entity.Priority;
import com.gharpahucho.entity.TransportMode;
import com.gharpahucho.integration.transport.Candidate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Deterministic, explainable ranking of travel candidates. No LLM is involved anywhere in this class:
 * fares, schedules and availability come from providers, and the score is a weighted sum of five normalised factors
 * (cost, duration, arrival suitability, changes, preference/comfort) whose weights depend on the traveller's priority.
 *
 * <p>Hard constraints (budget, arrival deadline, seats) are never silently relaxed. An option that breaks one is only
 * returned when alternatives are enabled, and then it carries explicit {@code violations} and the ALTERNATIVE tag.
 */
@Component
public class RecommendationEngine {

    public static final String TAG_CHEAPEST = "CHEAPEST";
    public static final String TAG_FASTEST = "FASTEST";
    public static final String TAG_BEST_OVERALL = "BEST_OVERALL";
    public static final String TAG_FEWEST_CHANGES = "FEWEST_CHANGES";
    public static final String TAG_WITHIN_BUDGET = "WITHIN_BUDGET";
    public static final String TAG_ARRIVES_ON_TIME = "ARRIVES_ON_TIME";
    public static final String TAG_ALTERNATIVE = "ALTERNATIVE";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    public record Criteria(LocalDate travelDate, LocalDateTime deadline, int travellers, Integer maxBudget,
                           TransportMode preferredMode, Priority priority) {
    }

    public record Settings(int maxRecommendations, int maxBudgetDeviationPct, boolean includeAlternatives) {
    }

    public record Scored(Candidate candidate, boolean meetsConstraints, List<String> violations, double score,
                         double overallScore, List<String> tags, List<String> reasons) {
        public int scorePercent() {
            return (int) Math.round(score * 100);
        }

        public int overallPercent() {
            return (int) Math.round(overallScore * 100);
        }
    }

    private record Weights(double cost, double duration, double arrival, double changes, double preference) {
    }

    private static Weights weightsFor(Priority priority) {
        return switch (priority) {
            case CHEAPEST -> new Weights(0.45, 0.15, 0.15, 0.10, 0.15);
            case FASTEST -> new Weights(0.15, 0.45, 0.15, 0.15, 0.10);
            case COMFORTABLE -> new Weights(0.10, 0.15, 0.15, 0.20, 0.40);
        };
    }

    private static final Weights BALANCED = new Weights(0.30, 0.30, 0.15, 0.10, 0.15);

    public List<Scored> rank(Criteria c, List<Candidate> candidates, Settings settings) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        int minCost = candidates.stream().mapToInt(Candidate::costPerPerson).min().orElse(1);
        int minDuration = candidates.stream().mapToInt(Candidate::durationMinutes).min().orElse(1);
        Weights own = weightsFor(c.priority() == null ? Priority.CHEAPEST : c.priority());

        List<Scored> all = new ArrayList<>();
        for (Candidate cand : candidates) {
            List<String> violations = violationsOf(c, cand, settings);
            double s = score(cand, c, own, minCost, minDuration, c.priority());
            double o = score(cand, c, BALANCED, minCost, minDuration, null);
            all.add(new Scored(cand, violations.isEmpty(), violations, s, o, new ArrayList<>(), new ArrayList<>()));
        }

        List<Scored> compliant = all.stream().filter(Scored::meetsConstraints).sorted(byScore()).toList();
        List<Scored> alternatives = settings.includeAlternatives()
                ? all.stream().filter(x -> !x.meetsConstraints()).sorted(byViolationsThenScore()).toList() : List.of();

        assignTags(compliant, c);
        for (Scored a : alternatives) {
            a.tags().add(TAG_ALTERNATIVE);
        }
        for (Scored s : compliant) {
            reasons(s, c, compliant, minCost);
        }
        for (Scored s : alternatives) {
            reasons(s, c, compliant, minCost);
        }

        int max = Math.max(1, settings.maxRecommendations());
        List<Scored> out = new ArrayList<>(compliant.stream().limit(max).toList());
        int room = Math.max(0, max - out.size());
        int altCap = out.isEmpty() ? Math.min(max, 6) : Math.min(room, 4);
        out.addAll(alternatives.stream().limit(altCap).toList());
        return out;
    }

    // ------------------------------------------------------------------ constraints

    List<String> violationsOf(Criteria c, Candidate cand, Settings settings) {
        List<String> v = new ArrayList<>();
        if (c.maxBudget() != null && cand.costPerPerson() > c.maxBudget()) {
            int over = cand.costPerPerson() - c.maxBudget();
            double tolerance = c.maxBudget() * (1 + settings.maxBudgetDeviationPct() / 100.0);
            if (cand.costPerPerson() <= tolerance) {
                v.add("₹" + over + " over your ₹" + c.maxBudget() + " budget (within the allowed " + settings.maxBudgetDeviationPct() + "% tolerance)");
            } else {
                v.add("₹" + over + " over your ₹" + c.maxBudget() + " budget");
            }
        }
        if (c.deadline() != null && cand.arrival().isAfter(c.deadline())) {
            long late = Duration.between(c.deadline(), cand.arrival()).toMinutes();
            v.add("Arrives " + TIME.format(cand.arrival()) + " on " + DATE.format(cand.arrival()) + ", " + human(late)
                    + " after your required " + TIME.format(c.deadline()));
        }
        if (cand.seatsLeft() != null && cand.seatsLeft() < c.travellers()) {
            v.add("Only " + cand.seatsLeft() + " seat" + (cand.seatsLeft() == 1 ? "" : "s") + " posted for " + c.travellers() + " travellers");
        }
        if (c.preferredMode() != null && c.preferredMode() != TransportMode.ANY && cand.mode() != c.preferredMode()) {
            v.add("Different from your preferred mode (" + c.preferredMode().label() + ")");
        }
        return v;
    }

    // ------------------------------------------------------------------ scoring

    private double score(Candidate cand, Criteria c, Weights w, int minCost, int minDuration, Priority priority) {
        double cost = (double) minCost / Math.max(1, cand.costPerPerson());
        double duration = (double) minDuration / Math.max(1, cand.durationMinutes());
        double arrival = arrivalSuitability(cand, c);
        double changes = Math.max(0, 1.0 - 0.35 * cand.transfers());
        double modeMatch = c.preferredMode() == null || c.preferredMode() == TransportMode.ANY ? 0.5
                : (cand.mode() == c.preferredMode() ? 1.0 : 0.0);
        double comfortShare = priority == Priority.COMFORTABLE ? 0.8 : 0.5;
        double preference = comfortShare * (cand.comfort() / 5.0) + (1 - comfortShare) * modeMatch;
        double s = w.cost() * cost + w.duration() * duration + w.arrival() * arrival + w.changes() * changes + w.preference() * preference;
        return Math.max(0, Math.min(1, s));
    }

    /** With a deadline: comfortable slack is best, too tight is risky, too early wastes time. Without: avoid 00:00-05:00 arrivals. */
    private double arrivalSuitability(Candidate cand, Criteria c) {
        if (c.deadline() != null) {
            long slack = Duration.between(cand.arrival(), c.deadline()).toMinutes();
            if (slack < 0) {
                return 0;
            }
            if (slack < 30) {
                return 0.6;
            }
            if (slack <= 180) {
                return 1.0;
            }
            return Math.max(0.7, 1.0 - (slack - 180) / 2880.0);
        }
        int hour = cand.arrival().getHour();
        return hour < 5 ? 0.4 : 1.0;
    }

    // ------------------------------------------------------------------ tags and reasons

    private void assignTags(List<Scored> compliant, Criteria c) {
        if (compliant.isEmpty()) {
            return;
        }
        Scored cheapest = compliant.stream().min(Comparator.comparingInt((Scored s) -> s.candidate().costPerPerson())
                .thenComparingInt(s -> s.candidate().durationMinutes())).orElseThrow();
        Scored fastest = compliant.stream().min(Comparator.comparingInt((Scored s) -> s.candidate().durationMinutes())
                .thenComparingInt(s -> s.candidate().costPerPerson())).orElseThrow();
        Scored best = compliant.stream().max(Comparator.comparingDouble(Scored::overallScore)).orElseThrow();
        int minTransfers = compliant.stream().mapToInt(s -> s.candidate().transfers()).min().orElse(0);
        Scored fewest = compliant.stream().filter(s -> s.candidate().transfers() == minTransfers)
                .min(Comparator.comparingInt((Scored s) -> s.candidate().costPerPerson())).orElseThrow();

        cheapest.tags().add(TAG_CHEAPEST);
        fastest.tags().add(TAG_FASTEST);
        best.tags().add(TAG_BEST_OVERALL);
        if (compliant.stream().anyMatch(s -> s.candidate().transfers() != minTransfers)) {
            fewest.tags().add(TAG_FEWEST_CHANGES);
        }
        for (Scored s : compliant) {
            if (c.maxBudget() != null) {
                s.tags().add(TAG_WITHIN_BUDGET);
            }
            if (c.deadline() != null) {
                s.tags().add(TAG_ARRIVES_ON_TIME);
            }
        }
    }

    private void reasons(Scored s, Criteria c, List<Scored> compliant, int minCost) {
        Candidate cand = s.candidate();
        List<String> r = s.reasons();
        if (s.tags().contains(TAG_CHEAPEST)) {
            r.add("Cheapest option that fits your constraints (₹" + cand.costPerPerson() + " per person)");
        }
        if (s.tags().contains(TAG_FASTEST)) {
            r.add("Fastest option that fits your constraints (" + human(cand.durationMinutes()) + ")");
        }
        if (s.tags().contains(TAG_BEST_OVERALL)) {
            r.add("Best balance of price, time and comfort among " + compliant.size() + " matching options");
        }
        if (c.maxBudget() != null && cand.costPerPerson() <= c.maxBudget()) {
            int under = c.maxBudget() - cand.costPerPerson();
            r.add(under > 0 ? "₹" + under + " under your ₹" + c.maxBudget() + " budget" : "Exactly on your ₹" + c.maxBudget() + " budget");
        }
        if (c.deadline() != null && !cand.arrival().isAfter(c.deadline())) {
            long slack = Duration.between(cand.arrival(), c.deadline()).toMinutes();
            r.add("Arrives " + TIME.format(cand.arrival()) + ", " + human(slack) + " before your " + TIME.format(c.deadline()) + " deadline"
                    + (slack < 30 ? " (tight)" : ""));
        }
        if (cand.transfers() == 0) {
            r.add("Direct, no changes");
        } else {
            r.add(cand.transfers() + " change: " + cand.stops());
        }
        if (c.preferredMode() != null && c.preferredMode() != TransportMode.ANY && cand.mode() == c.preferredMode()) {
            r.add("Matches your preferred mode (" + cand.mode().label() + ")");
        }
        if (cand.mode() == TransportMode.SHARED_CAR) {
            r.add("Cost is shared with a fellow traveller going your way");
        }
        if (cand.mode() == TransportMode.CAB) {
            r.add("Door to door, and you choose when to leave");
        }
        if (!cand.departure().toLocalDate().equals(cand.arrival().toLocalDate()) && cand.departure().getHour() >= 18) {
            r.add("Overnight journey: you travel while you sleep");
        }
        if (cand.festiveNote() != null) {
            r.add(cand.festiveNote());
        }
        if (cand.costPerPerson() > minCost && s.meetsConstraints() && r.size() < 2) {
            r.add("₹" + (cand.costPerPerson() - minCost) + " more than the cheapest available option");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Comparator<Scored> byScore() {
        return Comparator.comparingDouble(Scored::score).reversed()
                .thenComparingInt((Scored s) -> s.candidate().costPerPerson())
                .thenComparing(s -> s.candidate().departure());
    }

    private static Comparator<Scored> byViolationsThenScore() {
        return Comparator.comparingInt((Scored s) -> s.violations().size()).thenComparing(byScore());
    }

    public static String human(long minutes) {
        long m = Math.abs(minutes);
        long h = m / 60;
        long rem = m % 60;
        if (h == 0) {
            return rem + "m";
        }
        return rem == 0 ? h + "h" : h + "h " + rem + "m";
    }
}

package com.gharpahucho.recommendation;

import com.gharpahucho.dto.PlanDtos.BudgetTip;
import com.gharpahucho.entity.TransportMode;
import com.gharpahucho.integration.transport.Candidate;
import com.gharpahucho.integration.transport.RouteBasedCabProvider;
import com.gharpahucho.integration.transport.SearchContext;
import com.gharpahucho.integration.transport.SimulatedInventoryProvider;
import com.gharpahucho.recommendation.RecommendationEngine.Criteria;
import com.gharpahucho.recommendation.RecommendationEngine.Scored;
import com.gharpahucho.recommendation.RecommendationEngine.Settings;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Personalised budget tips computed from the same engine and data as the main results (never invented):
 * cheaper dates, cheaper modes, cab sharing, and what a slightly higher budget would unlock.
 * Enabled by the Flagsmith flag {@code budget_optimization}; the date window comes from {@code date_flexibility_days}.
 */
@Component
public class BudgetOptimizer {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private final SimulatedInventoryProvider inventory;
    private final RecommendationEngine engine;
    private final RouteBasedCabProvider cab;

    public BudgetOptimizer(SimulatedInventoryProvider inventory, RecommendationEngine engine, RouteBasedCabProvider cab) {
        this.inventory = inventory;
        this.engine = engine;
        this.cab = cab;
    }

    public List<BudgetTip> tips(SearchContext ctx, Criteria crit, Settings settings, List<Scored> ranked, int flexDays) {
        List<BudgetTip> tips = new ArrayList<>();
        List<Scored> compliant = ranked.stream().filter(Scored::meetsConstraints).toList();
        Scored cheapest = compliant.stream().min(Comparator.comparingInt(s -> s.candidate().costPerPerson())).orElse(null);
        Scored best = compliant.stream().max(Comparator.comparingDouble(Scored::overallScore)).orElse(null);

        tips.addAll(dateTips(ctx, crit, settings, cheapest, flexDays));

        if (cheapest != null && best != null && cheapest != best
                && cheapest.candidate().costPerPerson() + 50 <= best.candidate().costPerPerson()) {
            int saving = best.candidate().costPerPerson() - cheapest.candidate().costPerPerson();
            long extra = java.time.Duration.between(best.candidate().arrival(), cheapest.candidate().arrival()).toMinutes();
            String time = extra > 0 ? "arrives " + RecommendationEngine.human(extra) + " later" : "arrives " + RecommendationEngine.human(-extra) + " earlier";
            tips.add(new BudgetTip("MODE_SWITCH",
                    "Save ₹" + saving + " with " + cheapest.candidate().mode().label().toLowerCase(),
                    cheapest.candidate().serviceName() + " costs ₹" + cheapest.candidate().costPerPerson() + " versus ₹"
                            + best.candidate().costPerPerson() + " for the best-overall pick, and " + time + ".",
                    saving, null, cheapest.candidate().id()));
        }

        compliant.stream().filter(s -> s.candidate().mode() == TransportMode.CAB).findFirst().ifPresent(cabOption -> {
            int travellers = Math.max(1, crit.travellers());
            if (travellers < 3) {
                int total = cab.estimateCabTotal(ctx.route());
                int shared = (int) Math.ceil(total / 3.0);
                int saving = cabOption.candidate().costPerPerson() - shared;
                if (saving >= 100) {
                    tips.add(new BudgetTip("SHARE_CAB", "Share the cab and save about ₹" + saving,
                            "Splitting one cab between 3 travellers brings the estimate to about ₹" + shared
                                    + " per person. Use Find companions to meet someone going your way.",
                            saving, null, cabOption.candidate().id()));
                }
            }
        });

        if (compliant.isEmpty() && crit.maxBudget() != null) {
            ranked.stream()
                    .filter(s -> !s.meetsConstraints() && s.violations().stream().allMatch(v -> v.contains("budget")))
                    .min(Comparator.comparingInt(s -> s.candidate().costPerPerson()))
                    .ifPresent(s -> {
                        int gap = s.candidate().costPerPerson() - crit.maxBudget();
                        tips.add(new BudgetTip("BUDGET_GAP", "Raise your budget by ₹" + gap + " to unlock options",
                                "The cheapest option that meets your other requirements is " + s.candidate().serviceName() + " at ₹"
                                        + s.candidate().costPerPerson() + " per person.", 0, null, s.candidate().id()));
                    });
        }

        tips.sort(Comparator.comparingInt(BudgetTip::savingPerPerson).reversed());
        return tips.stream().limit(4).toList();
    }

    private List<BudgetTip> dateTips(SearchContext ctx, Criteria crit, Settings settings, Scored baseline, int flexDays) {
        List<BudgetTip> out = new ArrayList<>();
        LocalDate today = ctx.now().toLocalDate();
        for (int d = -flexDays; d <= flexDays; d++) {
            if (d == 0) {
                continue;
            }
            LocalDate date = ctx.travelDate().plusDays(d);
            if (date.isBefore(today)) {
                continue;
            }
            SearchContext shifted = ctx.withDates(date);
            List<Candidate> candidates = inventory.search(shifted);
            Criteria shiftedCrit = new Criteria(date, shifted.deadline(), crit.travellers(), crit.maxBudget(), crit.preferredMode(), crit.priority());
            List<Scored> ranked = engine.rank(shiftedCrit, candidates, new Settings(50, settings.maxBudgetDeviationPct(), false));
            ranked.stream().min(Comparator.comparingInt(s -> s.candidate().costPerPerson())).ifPresent(s -> {
                int cost = s.candidate().costPerPerson();
                if (baseline == null) {
                    out.add(new BudgetTip("DATE_SHIFT", "Options fit your needs on " + DAY.format(date),
                            "Cheapest is " + s.candidate().serviceName() + " at ₹" + cost + " per person.", 0, date, s.candidate().id()));
                } else if (cost + 30 <= baseline.candidate().costPerPerson()) {
                    int saving = baseline.candidate().costPerPerson() - cost;
                    out.add(new BudgetTip("DATE_SHIFT", "Travel on " + DAY.format(date) + " and save ₹" + saving,
                            "Cheapest matching option that day is " + s.candidate().serviceName() + " at ₹" + cost + " per person"
                                    + (s.candidate().festiveNote() == null ? "." : " (" + s.candidate().festiveNote() + ")."),
                            saving, date, s.candidate().id()));
                }
            });
        }
        out.sort(Comparator.comparingInt(BudgetTip::savingPerPerson).reversed());
        return out.stream().limit(2).toList();
    }
}

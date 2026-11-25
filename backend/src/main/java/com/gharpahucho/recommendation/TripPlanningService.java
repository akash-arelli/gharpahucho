package com.gharpahucho.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gharpahucho.dto.PlanDtos.*;
import com.gharpahucho.entity.DataSource;
import com.gharpahucho.entity.Priority;
import com.gharpahucho.entity.TransportMode;
import com.gharpahucho.exception.ApiException;
import com.gharpahucho.integration.flagsmith.FeatureFlagService;
import com.gharpahucho.integration.flagsmith.FeatureKey;
import com.gharpahucho.integration.flagsmith.FeatureSnapshot;
import com.gharpahucho.integration.flagsmith.RuntimeConfig;
import com.gharpahucho.integration.maps.City;
import com.gharpahucho.integration.maps.GeoService;
import com.gharpahucho.integration.maps.RouteInfo;
import com.gharpahucho.integration.sarvam.SarvamClient;
import com.gharpahucho.integration.sarvam.SarvamUnavailableException;
import com.gharpahucho.integration.transport.*;
import com.gharpahucho.recommendation.RecommendationEngine.Criteria;
import com.gharpahucho.recommendation.RecommendationEngine.Scored;
import com.gharpahucho.recommendation.RecommendationEngine.Settings;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Orchestrates one trip search: resolve cities, compute the real route, gather candidates from every enabled provider,
 * rank them deterministically and attach provenance, tips and notices. The Flagsmith snapshot for the calling user
 * decides which providers and features take part.
 */
@Slf4j
@Service
public class TripPlanningService {

    private static final String DISCLAIMER = "Train and bus schedules and fares are simulated sample data for demonstration and are "
            + "not real-time availability. Cab fares are estimates; distance and duration come from a routing engine. "
            + "Traveller-published rides are unverified until the traveller confirms.";

    private final GeoService geo;
    private final SimulatedInventoryProvider inventory;
    private final RouteBasedCabProvider cab;
    private final UserTripProvider userTrips;
    private final ConnectionProvider connections;
    private final RecommendationEngine engine;
    private final BudgetOptimizer optimizer;
    private final FeatureFlagService flags;
    private final FestiveCalendar festivals;
    private final SarvamClient sarvam;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;
    private final Clock clock;

    public TripPlanningService(GeoService geo, SimulatedInventoryProvider inventory, RouteBasedCabProvider cab,
                               UserTripProvider userTrips, ConnectionProvider connections, RecommendationEngine engine,
                               BudgetOptimizer optimizer, FeatureFlagService flags, FestiveCalendar festivals,
                               SarvamClient sarvam, ObjectMapper mapper, MeterRegistry meters, Clock clock) {
        this.geo = geo;
        this.inventory = inventory;
        this.cab = cab;
        this.userTrips = userTrips;
        this.connections = connections;
        this.engine = engine;
        this.optimizer = optimizer;
        this.flags = flags;
        this.festivals = festivals;
        this.sarvam = sarvam;
        this.mapper = mapper;
        this.meters = meters;
        this.clock = clock;
    }

    public PlanResponse plan(UUID userId, PlanRequest request) {
        FeatureSnapshot snap = flags.snapshot(userId);
        RuntimeConfig cfg = snap.runtime();
        LocalDateTime now = LocalDateTime.now(clock);

        City from = geo.requireCity(request.sourceCity(), "source");
        City to = geo.requireCity(request.destinationCity(), "destination");
        if (from.name().equalsIgnoreCase(to.name())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SAME_CITY", "Source and destination are the same city");
        }
        validateDate(request.travelDate(), now.toLocalDate());

        int travellers = request.travellers() == null ? 1 : request.travellers();
        TransportMode preferred = request.preferredMode() == null ? TransportMode.ANY : request.preferredMode();
        Priority priority = request.priority() == null ? Priority.CHEAPEST : request.priority();
        PlanRequest normalised = new PlanRequest(from.name(), to.name(), request.travelDate(), request.returnDate(), travellers,
                request.maxBudget(), preferred, priority, request.arriveBy());

        RouteInfo route = geo.route(from, to);
        LocalDate searchFrom = request.arriveBy() != null ? request.travelDate().minusDays(1) : request.travelDate();
        SearchContext ctx = new SearchContext(from, to, request.travelDate(), searchFrom, request.travelDate(), request.arriveBy(),
                travellers, route, userId, now, cfg.matchingRadiusKm(), cfg.dateFlexibilityDays());

        List<String> notices = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        collect(inventory, ctx, candidates, notices);
        collect(cab, ctx, candidates, notices);

        boolean sharedRides = snap.enabled(FeatureKey.SHARED_RIDE_MATCHING);
        boolean alternatives = snap.enabled(FeatureKey.ALTERNATIVE_ROUTE_RECOMMENDATIONS);
        boolean budgetTips = snap.enabled(FeatureKey.BUDGET_OPTIMIZATION);
        if (sharedRides) {
            collect(userTrips, ctx, candidates, notices);
        } else {
            notices.add("Shared rides are switched off for you (feature flag shared_ride_matching).");
        }
        if (alternatives) {
            try {
                candidates.addAll(connections.search(ctx));
            } catch (Exception e) {
                log.warn("event=connection_search_failed error={}", e.getMessage());
            }
        } else {
            notices.add("Alternative routes are switched off for you (feature flag alternative_route_recommendations): only options that fit every requirement are shown.");
        }

        Criteria criteria = new Criteria(request.travelDate(), ctx.deadline(), travellers, request.maxBudget(), preferred, priority);
        Settings settings = new Settings(cfg.maxRecommendations(), cfg.maxBudgetDeviationPct(), alternatives);
        List<Scored> ranked = engine.rank(criteria, candidates, settings);
        meters.counter("gharpahucho.plan.requests", "alternatives", String.valueOf(alternatives)).increment();

        List<BudgetTip> tips = List.of();
        if (budgetTips) {
            try {
                tips = optimizer.tips(ctx, criteria, settings, ranked, cfg.dateFlexibilityDays());
            } catch (Exception e) {
                log.warn("event=budget_optimizer_failed error={}", e.getMessage());
            }
        }

        if (!route.measured()) {
            notices.add(route.note());
        }
        long compliantCount = ranked.stream().filter(Scored::meetsConstraints).count();
        if (compliantCount == 0) {
            notices.add(ranked.isEmpty()
                    ? "No option satisfies every requirement. Try relaxing the budget or arrival time" + (alternatives ? "." : ", or enable alternative routes.")
                    : "No option satisfies every requirement. The options below are alternatives and say exactly which requirement they miss.");
        }
        if (ranked.stream().anyMatch(s -> s.candidate().source() == DataSource.SIMULATED_INVENTORY)) {
            notices.add("Train and bus results are sample data, not live availability.");
        }

        FestiveCalendar.Demand demand = festivals.demandOn(request.travelDate());
        FestiveInfo festive = demand.festival() == null ? null
                : new FestiveInfo(demand.festival().name(), demand.level(), festivals.noteFor(demand));

        Map<String, Boolean> applied = new LinkedHashMap<>();
        applied.put(FeatureKey.SHARED_RIDE_MATCHING.key(), sharedRides);
        applied.put(FeatureKey.ALTERNATIVE_ROUTE_RECOMMENDATIONS.key(), alternatives);
        applied.put(FeatureKey.BUDGET_OPTIMIZATION.key(), budgetTips);
        applied.put(FeatureKey.AI_TRIP_EXPLANATIONS.key(), snap.enabled(FeatureKey.AI_TRIP_EXPLANATIONS));

        List<OptionDto> options = ranked.stream().map(s -> toDto(s, travellers)).toList();
        return new PlanResponse(normalised, toDto(route), options, tips, applied, notices, candidates.size(),
                (int) compliantCount, festive, DISCLAIMER, Instant.now());
    }

    /** Grounded AI explanation: the model only receives the engine's own facts for ONE option, and nothing is stored. */
    public ExplainResponse explain(UUID userId, ExplainRequest request) {
        PlanResponse plan = plan(userId, request.plan());
        OptionDto option = plan.options().stream().filter(o -> o.id().equals(request.optionId())).findFirst()
                .orElseThrow(() -> ApiException.notFound("That option (it may no longer be available)"));
        if (!sarvam.isConfigured()) {
            return new ExplainResponse(option.id(), null, "UNAVAILABLE", false,
                    "AI explanations need a Sarvam API key. The deterministic reasons above are still accurate.");
        }
        try {
            ObjectNode facts = mapper.createObjectNode();
            facts.put("from", plan.request().sourceCity());
            facts.put("to", plan.request().destinationCity());
            facts.put("travellers", plan.request().travellers());
            if (plan.request().maxBudget() != null) {
                facts.put("budgetPerPersonInr", plan.request().maxBudget());
            }
            facts.put("option", option.title());
            facts.put("mode", option.modeLabel());
            facts.put("departure", option.departure().toString());
            facts.put("arrival", option.arrival().toString());
            facts.put("durationMinutes", option.durationMinutes());
            facts.put("costPerPersonInr", option.costPerPerson());
            facts.put("changes", option.transfers());
            facts.put("dataSource", option.dataSourceLabel());
            facts.put("availability", option.availabilityLabel());
            facts.set("reasons", mapper.valueToTree(option.reasons()));
            facts.set("violations", mapper.valueToTree(option.violations()));
            String lang = languageName(request.language());
            String system = "You are GharPahucho's travel explainer. Write 2-3 short, friendly sentences in " + lang
                    + " explaining why this option suits the traveller. Use ONLY the facts in the JSON. Never invent prices, times, "
                    + "operators or availability. If dataSource says sample data, say it is sample data. If violations is not empty, state them plainly. "
                    + "No markdown, no bullet points.";
            String text = sarvam.chat(List.of(SarvamClient.ChatMessage.system(system),
                    SarvamClient.ChatMessage.user(mapper.writeValueAsString(facts))), 0.3, 300, false, "trip_explanation");
            return new ExplainResponse(option.id(), text.trim(), "SARVAM_AI", true, null);
        } catch (SarvamUnavailableException e) {
            return new ExplainResponse(option.id(), null, "UNAVAILABLE", false,
                    "Sarvam AI could not be reached right now (" + e.getMessage() + "). The deterministic reasons above are unaffected.");
        } catch (Exception e) {
            log.warn("event=explain_failed error={}", e.getMessage());
            return new ExplainResponse(option.id(), null, "UNAVAILABLE", false, "The explanation could not be generated.");
        }
    }

    static String languageName(String code) {
        if (code == null) {
            return "English";
        }
        return switch (code.toLowerCase(Locale.ROOT).split("[-_]")[0]) {
            case "te" -> "Telugu (native script)";
            case "hi" -> "Hindi (Devanagari)";
            case "ta" -> "Tamil (native script)";
            case "kn" -> "Kannada (native script)";
            case "ml" -> "Malayalam (native script)";
            case "mr" -> "Marathi (Devanagari)";
            case "bn" -> "Bengali (native script)";
            default -> "English";
        };
    }

    private void collect(TransportProvider provider, SearchContext ctx, List<Candidate> sink, List<String> notices) {
        try {
            sink.addAll(provider.search(ctx));
        } catch (Exception e) {
            log.warn("event=provider_failed provider={} error={}", provider.id(), e.getMessage());
            notices.add("The " + provider.id() + " source is temporarily unavailable; results from other sources are shown.");
        }
    }

    private static void validateDate(LocalDate date, LocalDate today) {
        if (date.isBefore(today)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DATE_IN_PAST", "The travel date is in the past");
        }
        if (date.isAfter(today.plusDays(365))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DATE_TOO_FAR", "Please choose a date within the next year");
        }
    }

    public static RouteDto toDto(RouteInfo r) {
        return new RouteDto(new CityDto(r.from().name(), r.from().state(), r.from().lat(), r.from().lon()),
                new CityDto(r.to().name(), r.to().state(), r.to().lat(), r.to().lon()),
                r.distanceKm(), r.durationMinutes(), r.engine(), r.measured(), r.note(), r.geometry());
    }

    static OptionDto toDto(Scored s, int travellers) {
        Candidate c = s.candidate();
        List<LegDto> legs = c.legs().stream()
                .map(l -> new LegDto(l.mode(), l.from(), l.to(), l.departure(), l.arrival(), l.operator(), l.serviceName())).toList();
        return new OptionDto(c.id(), c.mode(), c.mode().label(), c.serviceName(), c.operator(), legs, c.departure(), c.arrival(),
                c.durationMinutes(), c.costPerPerson(), c.costPerPerson() * travellers, c.transfers(), c.stops(), c.source(),
                c.source().label(), c.source().description(), c.availability(), c.availabilityLabel(), s.meetsConstraints(),
                s.violations(), s.scorePercent(), s.overallPercent(), s.tags(), s.reasons(), c.comfort(), c.seatsLeft(),
                c.hostTripId(), c.hostName(), c.festiveNote());
    }
}

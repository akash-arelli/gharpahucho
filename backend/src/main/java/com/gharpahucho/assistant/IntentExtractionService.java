package com.gharpahucho.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gharpahucho.dto.AssistantDtos.TripDraft;
import com.gharpahucho.entity.Priority;
import com.gharpahucho.entity.TransportMode;
import com.gharpahucho.integration.maps.City;
import com.gharpahucho.integration.maps.GeoService;
import com.gharpahucho.integration.sarvam.SarvamClient;
import com.gharpahucho.integration.sarvam.SarvamClient.ChatMessage;
import com.gharpahucho.integration.sarvam.SarvamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns free text into a structured {@link TripDraft}. Sarvam AI does the understanding when it is available and
 * enabled; every field it returns is then validated (cities against the gazetteer / geocoder, dates, ranges, enums).
 * When Sarvam is unavailable the deterministic parser answers instead and the result says so.
 */
@Service
public class IntentExtractionService {

    public static final String SOURCE_AI = "SARVAM_AI";
    public static final String SOURCE_RULES = "RULE_BASED_FALLBACK";

    private static final Logger log = LoggerFactory.getLogger(IntentExtractionService.class);

    public record Extraction(TripDraft draft, String source, String language, String intent, List<String> notices) {
    }

    private final SarvamClient sarvam;
    private final RuleBasedIntentParser rules;
    private final GeoService geo;
    private final ObjectMapper mapper;
    private final Clock clock;

    public IntentExtractionService(SarvamClient sarvam, RuleBasedIntentParser rules, GeoService geo, ObjectMapper mapper, Clock clock) {
        this.sarvam = sarvam;
        this.rules = rules;
        this.geo = geo;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * @param useAi   false when the {@code sarvam_multilingual_assistant} flag is off for this user
     * @param context what is already known from earlier turns (may be null)
     */
    public Extraction extract(String text, TripDraft context, boolean useAi) {
        RuleBasedIntentParser.Parsed rule = rules.parse(text);
        List<String> notices = new ArrayList<>();

        if (!useAi) {
            notices.add("Understood with built-in rules because the AI assistant feature is switched off for you.");
            return new Extraction(rule.draft(), SOURCE_RULES, rule.language(), rule.intent(), notices);
        }
        if (!sarvam.isConfigured()) {
            notices.add("Sarvam AI is not configured, so built-in rules were used. Add SARVAM_API_KEY for free-form multilingual understanding.");
            return new Extraction(rule.draft(), SOURCE_RULES, rule.language(), rule.intent(), notices);
        }
        try {
            JsonNode json = callModel(text, context);
            TripDraft ai = validate(json, notices);
            // Deterministic parse fills anything the model left out; the model's validated values win otherwise.
            TripDraft merged = merge(ai, rule.draft());
            String lang = json.path("language").asText(null);
            String language = lang == null || lang.isBlank() ? rule.language() : normaliseLanguage(lang, rule.language());
            String intent = validIntent(json.path("intent").asText(null), rule.intent());
            return new Extraction(merged, SOURCE_AI, language, intent, notices);
        } catch (SarvamUnavailableException e) {
            notices.add("Sarvam AI could not be reached (" + e.getMessage() + "); built-in rules were used instead.");
        } catch (Exception e) {
            log.warn("event=intent_extraction_failed error={}", e.getMessage());
            notices.add("The AI response could not be understood; built-in rules were used instead.");
        }
        return new Extraction(rule.draft(), SOURCE_RULES, rule.language(), rule.intent(), notices);
    }

    private JsonNode callModel(String text, TripDraft context) throws Exception {
        LocalDate today = LocalDate.now(clock);
        String system = """
                You extract travel intent for GharPahucho, an Indian festive travel planner. Today is %s (%s). The user writes in English, Telugu, Hindi \
                or a mix, in native script or romanised. Return ONLY a JSON object with exactly these keys:
                source_city (English city name or null), destination_city (English city name or null), travel_date (YYYY-MM-DD or null), \
                return_date (YYYY-MM-DD or null), travellers (integer or null), max_budget_inr (integer per person or null), \
                mode (TRAIN, BUS, CAB, SHARED_CAR or null), priority (CHEAPEST, FASTEST, COMFORTABLE or null), arrive_by (HH:mm 24h or null), \
                willing_to_share (true, false or null), language (BCP-47 such as te-IN, hi-IN, en-IN), \
                intent (PLAN_TRIP, FIND_COMPANIONS, SHOW_GROUPS, GREETING or OTHER).
                Rules: never guess a value the user did not say; use null instead. Dates without a year mean the next upcoming occurrence. \
                "from X to Y" means source X and destination Y; "X nunchi Y vellali" (Telugu) and "X se Y" (Hindi) also mean from X to Y. \
                The current known trip is given as JSON; keep those values unless the new message changes them (return the merged result).
                """.formatted(today, today.getDayOfWeek().toString().toLowerCase(Locale.ROOT));
        ObjectNode user = mapper.createObjectNode();
        user.put("message", text);
        if (context != null) {
            user.set("known_trip", mapper.valueToTree(context));
        }
        String content = sarvam.chat(List.of(ChatMessage.system(system), ChatMessage.user(mapper.writeValueAsString(user))), 0.0, 400, true, "intent_extraction");
        return mapper.readTree(stripFences(content));
    }

    /** Validates model output against hard rules. Anything doubtful becomes null (and the rule parser may fill it). */
    private TripDraft validate(JsonNode j, List<String> notices) {
        LocalDate today = LocalDate.now(clock);
        String source = cityName(text(j, "source_city"));
        String dest = cityName(text(j, "destination_city"));
        if (text(j, "source_city") != null && source == null) {
            notices.add("\"" + text(j, "source_city") + "\" was not recognised as a city we can route from.");
        }
        if (text(j, "destination_city") != null && dest == null) {
            notices.add("\"" + text(j, "destination_city") + "\" was not recognised as a city we can route to.");
        }
        LocalDate date = date(text(j, "travel_date"));
        if (date != null && date.isBefore(today)) {
            notices.add("The date " + date + " is in the past, so it was ignored.");
            date = null;
        }
        LocalDate ret = date(text(j, "return_date"));
        if (ret != null && (date != null && ret.isBefore(date) || ret.isBefore(today))) {
            ret = null;
        }
        Integer travellers = range(j.path("travellers"), 1, 12);
        Integer budget = range(j.path("max_budget_inr"), 50, 100000);
        TransportMode mode = enumOf(TransportMode.class, text(j, "mode"));
        if (mode == TransportMode.ANY) {
            mode = null;
        }
        Priority priority = enumOf(Priority.class, text(j, "priority"));
        LocalTime arrive = null;
        try {
            if (text(j, "arrive_by") != null) {
                arrive = LocalTime.parse(text(j, "arrive_by"));
            }
        } catch (Exception ignored) {
            // leave null
        }
        Boolean share = j.hasNonNull("willing_to_share") && j.get("willing_to_share").isBoolean() ? j.get("willing_to_share").asBoolean() : null;
        return new TripDraft(source, dest, date, ret, travellers, budget, mode, priority, arrive, share);
    }

    private static TripDraft merge(TripDraft primary, TripDraft fallback) {
        return new TripDraft(
                first(primary.sourceCity(), fallback.sourceCity()), first(primary.destinationCity(), fallback.destinationCity()),
                first(primary.travelDate(), fallback.travelDate()), first(primary.returnDate(), fallback.returnDate()),
                first(primary.travellers(), fallback.travellers()), first(primary.maxBudget(), fallback.maxBudget()),
                first(primary.preferredMode(), fallback.preferredMode()), first(primary.priority(), fallback.priority()),
                first(primary.arriveBy(), fallback.arriveBy()), first(primary.willingToShare(), fallback.willingToShare()));
    }

    private static <T> T first(T a, T b) {
        return a != null ? a : b;
    }

    private String cityName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return geo.resolveCity(raw).map(City::name).orElse(null);
    }

    private static String text(JsonNode j, String field) {
        JsonNode n = j.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        String s = n.asText().trim();
        return s.isEmpty() || s.equalsIgnoreCase("null") ? null : s;
    }

    private static LocalDate date(String s) {
        try {
            return s == null ? null : LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer range(JsonNode n, int lo, int hi) {
        if (n == null || n.isNull() || !n.isNumber()) {
            return null;
        }
        int v = n.asInt();
        return v >= lo && v <= hi ? v : null;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String s) {
        if (s == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String validIntent(String s, String fallback) {
        if (s == null) {
            return fallback;
        }
        String u = s.trim().toUpperCase(Locale.ROOT);
        return List.of("PLAN_TRIP", "FIND_COMPANIONS", "SHOW_GROUPS", "GREETING", "OTHER").contains(u) ? u : fallback;
    }

    private static String normaliseLanguage(String s, String fallback) {
        String l = s.trim();
        return l.matches("[a-z]{2}-[A-Z]{2}") ? l : fallback;
    }

    private static String stripFences(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        int a = t.indexOf('{');
        int b = t.lastIndexOf('}');
        return a >= 0 && b > a ? t.substring(a, b + 1) : t;
    }
}

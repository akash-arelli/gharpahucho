package com.gharpahucho.assistant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gharpahucho.assistant.IntentExtractionService.Extraction;
import com.gharpahucho.dto.AssistantDtos.*;
import com.gharpahucho.dto.GroupDtos.GroupSummary;
import com.gharpahucho.dto.MatchDtos.MatchCandidate;
import com.gharpahucho.dto.MatchDtos.MatchResponse;
import com.gharpahucho.dto.PlanDtos.OptionDto;
import com.gharpahucho.dto.PlanDtos.PlanRequest;
import com.gharpahucho.dto.PlanDtos.PlanResponse;
import com.gharpahucho.entity.DataSource;
import com.gharpahucho.entity.User;
import com.gharpahucho.exception.ApiException;
import com.gharpahucho.integration.flagsmith.FeatureFlagService;
import com.gharpahucho.integration.flagsmith.FeatureKey;
import com.gharpahucho.integration.sarvam.SarvamClient;
import com.gharpahucho.integration.sarvam.SarvamClient.ChatMessage;
import com.gharpahucho.integration.sarvam.SarvamUnavailableException;
import com.gharpahucho.recommendation.RecommendationEngine;
import com.gharpahucho.recommendation.TripPlanningService;
import com.gharpahucho.repository.UserRepository;
import com.gharpahucho.security.AiRateLimiter;
import com.gharpahucho.service.GroupService;
import com.gharpahucho.service.TravellerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The conversational layer. Sarvam AI understands the user; deterministic Java services answer. Every figure in a
 * reply comes from a real backend call (planner, matcher, groups), and the reply says when data is sample data.
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final long MAX_AUDIO_BYTES = 5L * 1024 * 1024;

    private final IntentExtractionService extraction;
    private final TripPlanningService planner;
    private final TravellerService travellers;
    private final GroupService groups;
    private final FeatureFlagService flags;
    private final SarvamClient sarvam;
    private final AiRateLimiter limiter;
    private final UserRepository users;
    private final ObjectMapper mapper;

    public AssistantService(IntentExtractionService extraction, TripPlanningService planner, TravellerService travellers,
                            GroupService groups, FeatureFlagService flags, SarvamClient sarvam, AiRateLimiter limiter,
                            UserRepository users, ObjectMapper mapper) {
        this.extraction = extraction;
        this.planner = planner;
        this.travellers = travellers;
        this.groups = groups;
        this.flags = flags;
        this.sarvam = sarvam;
        this.limiter = limiter;
        this.users = users;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ parse (natural-language trip creation)

    public ParseResponse parse(UUID userId, ParseRequest req) {
        limiter.check(userId);
        boolean useAi = flags.isEnabled(FeatureKey.SARVAM_MULTILINGUAL_ASSISTANT, userId);
        Extraction ex = extraction.extract(req.text(), null, useAi);
        List<String> notices = new ArrayList<>(ex.notices());
        TripDraft draft = withHomeCity(userId, ex.draft(), notices, Replies.base(ex.language()));
        return new ParseResponse(draft, ex.source(), ex.language(), missing(draft), notices);
    }

    // ------------------------------------------------------------------ chat

    public AssistantResponse chat(UUID userId, AssistantRequest req) {
        limiter.check(userId);
        boolean useAi = flags.isEnabled(FeatureKey.SARVAM_MULTILINGUAL_ASSISTANT, userId);
        return respond(userId, req.message(), req.languageHint(), req.context(), useAi);
    }

    /** Voice: Sarvam speech-to-text, then exactly the same grounded pipeline as typed text. */
    public VoiceResponse voice(UUID userId, MultipartFile audio, String languageHint, TripDraft context) {
        limiter.check(userId);
        if (audio == null || audio.isEmpty()) {
            throw ApiException.badRequest("No audio was received");
        }
        if (audio.getSize() > MAX_AUDIO_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "AUDIO_TOO_LARGE", "Please keep voice messages under 5 MB (about a minute).");
        }
        String type = audio.getContentType() == null ? "" : audio.getContentType().toLowerCase(Locale.ROOT);
        if (!type.startsWith("audio/") && !type.startsWith("video/webm") && !type.equals("application/octet-stream")) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_AUDIO", "Unsupported audio type: " + type);
        }
        if (!sarvam.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SPEECH_UNAVAILABLE",
                    "Voice input needs a Sarvam API key. You can type your request instead.");
        }
        try {
            SarvamClient.Transcript t = sarvam.transcribe(audio.getBytes(), filenameFor(type), languageCode(languageHint));
            boolean useAi = flags.isEnabled(FeatureKey.SARVAM_MULTILINGUAL_ASSISTANT, userId);
            AssistantResponse answer = respond(userId, t.text(), t.languageCode() != null ? t.languageCode() : languageHint, context, useAi);
            return new VoiceResponse(t.text(), t.languageCode(), "SARVAM_AI", answer);
        } catch (SarvamUnavailableException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SPEECH_UNAVAILABLE",
                    "Speech recognition is unavailable right now (" + e.getMessage() + "). You can type your request instead.");
        } catch (IOException e) {
            throw ApiException.badRequest("The audio could not be read");
        }
    }

    /** Typed or transcribed text through the whole pipeline. Public so the voice flow and tests share one path. */
    public AssistantResponse respond(UUID userId, String message, String languageHint, TripDraft context, boolean useAi) {
        Extraction ex = extraction.extract(message, context, useAi);
        List<String> notices = new ArrayList<>(ex.notices());
        String lang = Replies.base(ex.language() != null ? ex.language() : languageHint);
        TripDraft draft = overlay(context, ex.draft());
        draft = withHomeCity(userId, draft, notices, lang);

        List<OptionDto> options = List.of();
        List<MatchCandidate> matches = List.of();
        List<AssistantAction> actions = new ArrayList<>();
        StringBuilder reply = new StringBuilder();
        String intent = ex.intent();

        switch (intent) {
            case "GREETING" -> reply.append(Replies.greeting(lang));
            case "SHOW_GROUPS" -> {
                List<GroupSummary> mine = groups.mine(userId);
                reply.append(mine.isEmpty() ? Replies.noGroups(lang) : Replies.groups(lang, mine.size()));
                for (GroupSummary g : mine.stream().limit(3).toList()) {
                    reply.append("\n• ").append(g.name());
                }
                actions.add(new AssistantAction("OPEN_GROUPS", "Open groups", Map.of()));
            }
            case "FIND_COMPANIONS" -> {
                String ask = askMissing(draft, lang);
                if (ask != null) {
                    reply.append(ask);
                } else if (!flags.isEnabled(FeatureKey.TRAVEL_COMPANION_MATCHING, userId)) {
                    reply.append(Replies.companionsOff(lang));
                } else {
                    MatchResponse mr = travellers.matchesForDraft(userId, draft);
                    matches = mr.matches().stream().limit(3).toList();
                    if (matches.isEmpty()) {
                        reply.append(Replies.noCompanions(lang));
                    } else {
                        reply.append(Replies.companionsFound(lang, mr.total(), draft.sourceCity(), draft.destinationCity()));
                        for (MatchCandidate m : matches) {
                            String why = m.reasons().isEmpty() ? "" : m.reasons().get(0);
                            reply.append("\n• ").append(Replies.companionLine(lang, m.name(), m.score(), why).trim());
                        }
                    }
                    actions.add(new AssistantAction("OPEN_TRAVELLERS", "See all travellers", draftPayload(draft)));
                }
            }
            case "PLAN_TRIP", "OTHER" -> {
                String ask = intent.equals("OTHER") && draft.destinationCity() == null && draft.travelDate() == null
                        ? Replies.didNotUnderstand(lang) : askMissing(draft, lang);
                if (ask != null) {
                    reply.append(ask);
                } else if (!flags.isEnabled(FeatureKey.SMART_TRIP_PLANNER, userId)) {
                    reply.append(Replies.plannerOff(lang));
                } else {
                    PlanResponse plan = planner.plan(userId, toPlanRequest(draft));
                    options = highlights(plan);
                    describePlan(reply, plan, options, draft, lang);
                    actions.add(new AssistantAction("OPEN_RECOMMENDATIONS", "See all options", draftPayload(draft)));
                    actions.add(new AssistantAction("CREATE_TRIP", "Save this journey", draftPayload(draft)));
                    notices.addAll(plan.notices());
                }
            }
            default -> reply.append(Replies.didNotUnderstand(lang));
        }

        String template = reply.toString();
        String finalReply = template;
        String replySource = "TEMPLATE";
        boolean aiAvailable = useAi && sarvam.isConfigured();
        // English templates are already final: rewriting them adds cost and risk but no value.
        String spoken = ex.language() != null ? ex.language() : languageHint;
        boolean needsTranslation = spoken != null && !spoken.isBlank() && !spoken.toLowerCase(Locale.ROOT).startsWith("en");
        if (aiAvailable && needsTranslation && !intent.equals("SHOW_GROUPS")) {
            Optional<String> polished = polish(template, ex.language());
            if (polished.isPresent()) {
                finalReply = polished.get();
                replySource = "SARVAM_AI";
            } else {
                notices.add("AI phrasing was unavailable; showing the standard reply (facts are unchanged).");
            }
        }
        return new AssistantResponse(finalReply, ex.language(), intent, draft, ex.source(), replySource, aiAvailable,
                notices, options, matches, actions);
    }

    // ------------------------------------------------------------------ reply building

    private void describePlan(StringBuilder out, PlanResponse plan, List<OptionDto> highlights, TripDraft d, String lang) {
        if (plan.options().isEmpty()) {
            out.append(Replies.noOptions(lang, d.sourceCity(), d.destinationCity()));
            return;
        }
        out.append(Replies.planIntro(lang, d.sourceCity(), d.destinationCity(), DAY.format(d.travelDate()), plan.options().size()));
        for (OptionDto o : highlights) {
            String label = o.tags().contains(RecommendationEngine.TAG_CHEAPEST) ? Replies.labelCheapest(lang)
                    : o.tags().contains(RecommendationEngine.TAG_FASTEST) ? Replies.labelFastest(lang)
                    : o.tags().contains(RecommendationEngine.TAG_BEST_OVERALL) ? Replies.labelBest(lang) : Replies.labelOption(lang);
            out.append("\n• ").append(Replies.optionLine(lang, label, o.title(), o.costPerPerson(), Replies.mins(lang, o.durationMinutes()),
                    o.departure().toLocalTime().toString()));
        }
        if (plan.withinConstraints() == 0 && d.maxBudget() != null) {
            out.append("\n").append(Replies.budgetWarning(lang, d.maxBudget()));
        }
        if (highlights.stream().anyMatch(o -> o.dataSource() == DataSource.SIMULATED_INVENTORY)) {
            out.append("\n").append(Replies.sampleDataNote(lang));
        }
    }

    /** Cheapest, fastest and best-overall, de-duplicated; falls back to the top ranked options. */
    private static List<OptionDto> highlights(PlanResponse plan) {
        LinkedHashMap<String, OptionDto> picks = new LinkedHashMap<>();
        for (String tag : List.of(RecommendationEngine.TAG_BEST_OVERALL, RecommendationEngine.TAG_CHEAPEST, RecommendationEngine.TAG_FASTEST)) {
            plan.options().stream().filter(o -> o.tags().contains(tag)).findFirst().ifPresent(o -> picks.putIfAbsent(o.id(), o));
        }
        for (OptionDto o : plan.options()) {
            if (picks.size() >= 3) {
                break;
            }
            picks.putIfAbsent(o.id(), o);
        }
        return picks.values().stream().limit(3).toList();
    }

    /**
     * Lets Sarvam rephrase a grounded reply naturally in the user's language. Rejected if it drops any number from the
     * template (a cheap but effective guard against invented or altered figures).
     */
    private Optional<String> polish(String template, String languageCode) {
        try {
            String system = "You are GharPahucho's friendly travel assistant. Rewrite the reply below in " + languageName(languageCode)
                    + ", warm and concise. Keep EVERY number, price, time, city name and the data-source note exactly as given, using ASCII digits. "
                    + "Do not add, remove or guess any facts, options or promises. Keep bullet lines starting with the bullet character. No markdown.";
            String out = sarvam.chat(List.of(ChatMessage.system(system), ChatMessage.user(template)), 0.2, 500, false, "assistant_reply").trim();
            if (out.isEmpty() || !preservesNumbers(template, out)) {
                log.info("event=assistant_polish_rejected reason=numbers_changed");
                return Optional.empty();
            }
            if (!addsNoNewNames(template, out)) {
                log.info("event=assistant_polish_rejected reason=invented_content");
                return Optional.empty();
            }
            return Optional.of(out);
        } catch (SarvamUnavailableException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.warn("event=assistant_polish_failed error={}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Every Latin-script word in a rewrite (city, operator and product names, which stay in Latin script inside Telugu or
     * Hindi text) must already be in the grounded template. This stops the model inventing sources or operators, such as
     * a made-up "Data source: Ministry of Railways" line.
     */
    static boolean addsNoNewNames(String original, String rewritten) {
        Set<String> known = new HashSet<>();
        Matcher o = Pattern.compile("\\p{IsLatin}{2,}").matcher(original.toLowerCase(Locale.ROOT));
        while (o.find()) {
            known.add(o.group());
        }
        Matcher r = Pattern.compile("\\p{IsLatin}{2,}").matcher(rewritten.toLowerCase(Locale.ROOT));
        while (r.find()) {
            if (!known.contains(r.group())) {
                return false;
            }
        }
        return true;
    }

    static boolean preservesNumbers(String original, String rewritten) {
        Matcher m = Pattern.compile("\\d+").matcher(original);
        Set<String> numbers = new HashSet<>();
        while (m.find()) {
            numbers.add(m.group());
        }
        Matcher r = Pattern.compile("\\d+").matcher(rewritten);
        Set<String> got = new HashSet<>();
        while (r.find()) {
            got.add(r.group());
        }
        return got.containsAll(numbers);
    }

    // ------------------------------------------------------------------ draft helpers

    private static String askMissing(TripDraft d, String lang) {
        if (d.destinationCity() == null) {
            return Replies.askDestination(lang);
        }
        if (d.sourceCity() == null) {
            return Replies.askSource(lang);
        }
        if (d.travelDate() == null) {
            return Replies.askDate(lang);
        }
        return null;
    }

    static List<String> missing(TripDraft d) {
        List<String> m = new ArrayList<>();
        if (d.sourceCity() == null) {
            m.add("sourceCity");
        }
        if (d.destinationCity() == null) {
            m.add("destinationCity");
        }
        if (d.travelDate() == null) {
            m.add("travelDate");
        }
        return m;
    }

    private TripDraft withHomeCity(UUID userId, TripDraft d, List<String> notices, String lang) {
        if (d.sourceCity() != null || d.destinationCity() == null) {
            return d;
        }
        User u = users.findById(userId).orElse(null);
        if (u == null || u.getHomeCity() == null || u.getHomeCity().equalsIgnoreCase(d.destinationCity())) {
            return d;
        }
        notices.add(Replies.assumedHome(lang, u.getHomeCity()));
        return new TripDraft(u.getHomeCity(), d.destinationCity(), d.travelDate(), d.returnDate(), d.travellers(), d.maxBudget(),
                d.preferredMode(), d.priority(), d.arriveBy(), d.willingToShare());
    }

    /** New values replace old ones; fields the user did not mention keep their earlier value. */
    private static TripDraft overlay(TripDraft base, TripDraft fresh) {
        if (base == null) {
            return fresh;
        }
        return new TripDraft(pick(fresh.sourceCity(), base.sourceCity()), pick(fresh.destinationCity(), base.destinationCity()),
                pick(fresh.travelDate(), base.travelDate()), pick(fresh.returnDate(), base.returnDate()),
                pick(fresh.travellers(), base.travellers()), pick(fresh.maxBudget(), base.maxBudget()),
                pick(fresh.preferredMode(), base.preferredMode()), pick(fresh.priority(), base.priority()),
                pick(fresh.arriveBy(), base.arriveBy()), pick(fresh.willingToShare(), base.willingToShare()));
    }

    private static <T> T pick(T fresh, T old) {
        return fresh != null ? fresh : old;
    }

    private static PlanRequest toPlanRequest(TripDraft d) {
        return new PlanRequest(d.sourceCity(), d.destinationCity(), d.travelDate(), d.returnDate(), d.travellers() == null ? 1 : d.travellers(),
                d.maxBudget(), d.preferredMode(), d.priority(), d.arriveBy());
    }

    private Map<String, Object> draftPayload(TripDraft d) {
        return mapper.convertValue(d, new TypeReference<Map<String, Object>>() {
        });
    }

    private static String languageName(String code) {
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

    private static String languageCode(String hint) {
        if (hint == null || hint.isBlank() || hint.equalsIgnoreCase("auto")) {
            return "unknown";
        }
        return hint.matches("[a-z]{2}-[A-Z]{2}") ? hint : "unknown";
    }

    private static String filenameFor(String contentType) {
        if (contentType.contains("webm")) {
            return "voice.webm";
        }
        if (contentType.contains("ogg")) {
            return "voice.ogg";
        }
        if (contentType.contains("mp4") || contentType.contains("m4a") || contentType.contains("aac")) {
            return "voice.mp4";
        }
        if (contentType.contains("mpeg") || contentType.contains("mp3")) {
            return "voice.mp3";
        }
        return "voice.wav";
    }
}

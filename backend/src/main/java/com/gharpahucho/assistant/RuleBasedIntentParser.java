package com.gharpahucho.assistant;

import com.gharpahucho.dto.AssistantDtos.TripDraft;
import com.gharpahucho.entity.Priority;
import com.gharpahucho.entity.TransportMode;
import com.gharpahucho.integration.maps.City;
import com.gharpahucho.integration.maps.CityGazetteer;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic travel-intent parser. It is both the safety net when Sarvam is unavailable and the validator that keeps
 * the model honest (cities are always resolved against the gazetteer). Understands English, romanised Telugu/Hindi
 * ("Naaku December 20th Bangalore nunchi Hyderabad vellali. Budget 1500.") and Telugu / Devanagari script.
 */
@Component
public class RuleBasedIntentParser {

    public record Parsed(TripDraft draft, String language, String intent) {
    }

    private static final Set<String> SOURCE_AFTER = Set.of("nunchi", "nundi", "nuncho", "nunchee", "se", "to", "-", "->", "→");
    private static final String[] SOURCE_AFTER_PREFIX = {"నుంచి", "నుండి", "से"};
    private static final Set<String> DEST_AFTER = Set.of("ko", "ki", "ku", "tak", "vellali", "vellaali", "jana", "jaana", "jaanaa", "kavali");
    private static final String[] DEST_AFTER_PREFIX = {"కి", "కు", "को", "तक", "వెళ్ళాలి", "వెళ్లాలి", "जाना"};
    private static final Set<String> SOURCE_BEFORE = Set.of("from", "leaving", "starting");
    private static final Set<String> DEST_BEFORE = Set.of("to", "towards", "for", "till", "reach", "reaching");

    private static final Set<String> TELUGU_ROMAN = Set.of("naaku", "naku", "nenu", "nunchi", "nundi", "vellali", "vellaali", "kavali",
            "kaavali", "repu", "ellundi", "chavaka", "tirigi", "undi", "meeru", "mee", "cheppandi", "ledu", "evaru", "evarina", "tho", "todu", "bandi");
    private static final Set<String> HINDI_ROMAN = Set.of("mujhe", "mera", "meri", "hum", "chahiye", "jana", "jaana", "wapas", "vapas", "sasta",
            "jaldi", "kitna", "kal", "parso", "saath", "saathi", "sath", "mein", "aur", "hai", "chalna", "rupaye");

    private static final Map<String, Integer> MONTHS = new LinkedHashMap<>();

    static {
        String[][] latin = {{"january", "jan"}, {"february", "feb"}, {"march", "mar"}, {"april", "apr"}, {"may"}, {"june", "jun"},
                {"july", "jul"}, {"august", "aug"}, {"september", "sept", "sep"}, {"october", "oct"}, {"november", "nov"}, {"december", "dec"}};
        String[][] telugu = {{"జనవరి"}, {"ఫిబ్రవరి"}, {"మార్చి"}, {"ఏప్రిల్"}, {"మే"}, {"జూన్"}, {"జూలై"}, {"ఆగస్టు"}, {"సెప్టెంబర్"}, {"అక్టోబర్"}, {"నవంబర్"}, {"డిసెంబర్"}};
        String[][] hindi = {{"जनवरी"}, {"फरवरी", "फ़रवरी"}, {"मार्च"}, {"अप्रैल"}, {"मई"}, {"जून"}, {"जुलाई"}, {"अगस्त"}, {"सितंबर", "सितम्बर"}, {"अक्टूबर"}, {"नवंबर", "नवम्बर"}, {"दिसंबर", "दिसम्बर"}};
        String[][] romanHiTe = {{"janavari", "janvari"}, {"pharavari", "farvari"}, {"maarchi"}, {"epril"}, {"mei"}, {"joon"}, {"julai"}, {"agast", "agastu"}, {"sitambar"}, {"aktoobar", "aktubar"}, {"navambar"}, {"disambar", "disember"}};
        String[][][] all = {latin, telugu, hindi, romanHiTe};
        for (String[][] group : all) {
            for (int i = 0; i < 12; i++) {
                for (String name : group[i]) {
                    MONTHS.put(CityGazetteer.norm(name), i + 1);
                }
            }
        }
    }

    private static final String MONTH_ALT;

    static {
        List<String> names = new ArrayList<>(MONTHS.keySet());
        names.sort(Comparator.comparingInt(String::length).reversed());
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(Pattern.quote(n));
        }
        MONTH_ALT = sb.toString();
    }

    private static final String NOT_LETTER_AFTER = "(?![\\p{L}\\p{M}])";
    private static final Pattern ISO = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");
    private static final Pattern DAY_MONTH = Pattern.compile("(?<![\\d/-])(\\d{1,2})\\s*(?:st|nd|rd|th)?\\s*(?:of\\s+)?(" + MONTH_ALT + ")" + NOT_LETTER_AFTER
            + "(?:\\s*,?\\s*(\\d{4}))?", Pattern.UNICODE_CASE);
    private static final Pattern MONTH_DAY = Pattern.compile("(?<![\\p{L}\\p{M}])(" + MONTH_ALT + ")\\s*(\\d{1,2})(?:st|nd|rd|th)?(?![\\d:])"
            + "(?:\\s*,?\\s*(\\d{4}))?", Pattern.UNICODE_CASE);
    private static final Pattern NUMERIC = Pattern.compile("(?<![\\d/.-])(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?(?![\\d:])");
    private static final Pattern RELATIVE = Pattern.compile("(?<![\\p{L}\\p{M}])(day after tomorrow|dayaftertomorrow|tomorrow|today|tonight|parso|ellundi|repu|ripu|kal|aaj|రేపు|ఎల్లుండి|ఈరోజు|ఈ రోజు|आज|कल|परसों)"
            + NOT_LETTER_AFTER, Pattern.UNICODE_CASE);

    private static final String CURRENCY = "(?:budget|బడ్జెట్|बजट|under|within|upto|up to|max|maximum|below|₹|rs\\.?|inr|rupees|rupee|rupaye|rupayalu|రూపాయలు|रुपये)";
    private static final Pattern BUDGET_BEFORE = Pattern.compile("(?<![\\p{L}\\p{M}])" + CURRENCY + "\\s*(?:is|of|:|=|-)?\\s*(?:rs\\.?|₹|inr)?\\s*(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k|వేలు|हज़ार|hazaar|thousand)?", Pattern.UNICODE_CASE);
    private static final Pattern BUDGET_AFTER = Pattern.compile("(?<![\\d/.-])(\\d[\\d,]*)\\s*(k)?\\s*(?:rs\\.?|₹|inr|rupees|rupaye|rupayalu|రూపాయలు|रुपये|budget|బడ్జెట్|बजट)(?![\\p{L}])", Pattern.UNICODE_CASE);
    private static final Pattern PEOPLE = Pattern.compile("(?<![\\d/.-])(\\d{1,2})\\s*(?:people|persons?|travell?ers?|passengers?|members|adults|friends|pax|tickets?|seats?|log|mandi|మంది|लोग|जन)(?![\\p{L}])", Pattern.UNICODE_CASE);
    private static final Pattern PEOPLE_FOR = Pattern.compile("(?:for|we are|we're|group of)\\s+(\\d{1,2})(?![\\d:/.-]|\\s*(?:st|nd|rd|th|" + MONTH_ALT + "))", Pattern.UNICODE_CASE);
    private static final Pattern ARRIVE_BY = Pattern.compile("(?:arrive|reach|reaching|be there)?\\s*(?:by|before|tak|లోపు|तक)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)", Pattern.UNICODE_CASE);
    private static final Pattern ARRIVE_BY_24 = Pattern.compile("(?:arrive|reach|reaching|by|before)\\s+(\\d{1,2}):(\\d{2})(?!\\s*(?:am|pm))", Pattern.UNICODE_CASE);

    private final CityGazetteer gazetteer;
    private final java.time.Clock clock;

    public RuleBasedIntentParser(CityGazetteer gazetteer, java.time.Clock clock) {
        this.gazetteer = gazetteer;
        this.clock = clock;
    }

    public Parsed parse(String raw) {
        String text = asciiDigits(CityGazetteer.norm(raw));
        String language = detectLanguage(text);
        LocalDate today = LocalDate.now(clock);

        City[] route = resolveRoute(text);
        List<LocalDate> dates = findDates(text, today);
        LocalDate travelDate = dates.isEmpty() ? null : dates.get(0);
        LocalDate returnDate = dates.size() > 1 && !dates.get(1).isBefore(dates.get(0)) ? dates.get(1) : null;

        Integer budget = findBudget(text);
        Integer travellers = findTravellers(text);
        TransportMode mode = findMode(text);
        Priority priority = findPriority(text);
        LocalTime arriveBy = findArriveBy(text);
        Boolean share = null;
        if (containsAny(text, "share", "shared", "carpool", "car pool", "ride share", "ridesharing", "together", "saath", "साथ", "తో కలిసి")) {
            share = Boolean.TRUE;
        }
        if (mode == TransportMode.SHARED_CAR) {
            share = Boolean.TRUE;
        }

        TripDraft draft = new TripDraft(route[0] == null ? null : route[0].name(), route[1] == null ? null : route[1].name(),
                travelDate, returnDate, travellers, budget, mode, priority, arriveBy, share);
        return new Parsed(draft, language, intent(text, draft));
    }

    // ------------------------------------------------------------------ route

    private City[] resolveRoute(String text) {
        List<CityGazetteer.Mention> mentions = gazetteer.mentions(text);
        City source = null;
        City dest = null;
        List<CityGazetteer.Mention> unmarked = new ArrayList<>();
        for (CityGazetteer.Mention m : mentions) {
            String before = text.substring(0, m.start()).stripTrailing();
            String after = text.substring(m.end()).stripLeading();
            String prevWord = lastToken(before);
            String nextWord = firstToken(after);
            boolean isSource = SOURCE_BEFORE.contains(prevWord) || SOURCE_AFTER.contains(nextWord) || startsWithAny(text.substring(m.end()), SOURCE_AFTER_PREFIX)
                    || startsWithAny(after, SOURCE_AFTER_PREFIX);
            boolean isDest = DEST_BEFORE.contains(prevWord) || DEST_AFTER.contains(nextWord) || startsWithAny(text.substring(m.end()), DEST_AFTER_PREFIX)
                    || startsWithAny(after, DEST_AFTER_PREFIX);
            // "X to Y": X is followed by "to" (source) while Y is preceded by "to" (destination); the preceding marker wins for Y.
            if (DEST_BEFORE.contains(prevWord) && !SOURCE_BEFORE.contains(prevWord)) {
                isSource = false;
            }
            if (isSource && source == null) {
                source = m.city();
            } else if (isDest && dest == null && !m.city().equals(source)) {
                dest = m.city();
            } else if (!isSource && !isDest) {
                unmarked.add(m);
            }
        }
        for (CityGazetteer.Mention m : unmarked) {
            if (source == null && dest == null) {
                source = m.city();
            } else if (dest == null && !m.city().equals(source)) {
                dest = m.city();
            } else if (source == null && !m.city().equals(dest)) {
                source = m.city();
            }
        }
        // A single unmarked city is most likely the destination ("I want to go to Hyderabad" / "Hyderabad").
        if (mentions.size() == 1 && source != null && dest == null && unmarked.size() == 1) {
            dest = source;
            source = null;
        }
        return new City[]{source, dest};
    }

    private static String lastToken(String s) {
        String[] parts = s.split("[^\\p{L}\\p{M}\\p{N}>→-]+");
        return parts.length == 0 ? "" : parts[parts.length - 1];
    }

    private static String firstToken(String s) {
        String[] parts = s.split("[^\\p{L}\\p{M}\\p{N}>→-]+", 2);
        return parts.length == 0 ? "" : parts[0];
    }

    private static boolean startsWithAny(String s, String[] prefixes) {
        for (String p : prefixes) {
            if (s.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ dates

    private List<LocalDate> findDates(String text, LocalDate today) {
        record Hit(int start, LocalDate date) {
        }
        List<Hit> hits = new ArrayList<>();
        boolean[] used = new boolean[text.length()];

        Matcher m = ISO.matcher(text);
        while (m.find()) {
            take(hits, used, m, safeDate(num(m.group(1)), num(m.group(2)), num(m.group(3)), today, true), (s, d) -> new Hit(s, d));
        }
        m = DAY_MONTH.matcher(text);
        while (m.find()) {
            Integer year = m.group(3) == null ? null : num(m.group(3));
            take(hits, used, m, safeDate(year, MONTHS.get(m.group(2)), num(m.group(1)), today, year != null), (s, d) -> new Hit(s, d));
        }
        m = MONTH_DAY.matcher(text);
        while (m.find()) {
            Integer year = m.group(3) == null ? null : num(m.group(3));
            take(hits, used, m, safeDate(year, MONTHS.get(m.group(1)), num(m.group(2)), today, year != null), (s, d) -> new Hit(s, d));
        }
        m = NUMERIC.matcher(text);
        while (m.find()) {
            Integer year = m.group(3) == null ? null : num(m.group(3));
            if (year != null && year < 100) {
                year += 2000;
            }
            take(hits, used, m, safeDate(year, num(m.group(2)), num(m.group(1)), today, year != null), (s, d) -> new Hit(s, d));
        }
        m = RELATIVE.matcher(text);
        while (m.find()) {
            LocalDate d = switch (m.group(1)) {
                case "today", "tonight", "aaj", "ఈరోజు", "ఈ రోజు", "आज" -> today;
                case "tomorrow", "kal", "repu", "ripu", "రేపు", "कल" -> today.plusDays(1);
                default -> today.plusDays(2);
            };
            take(hits, used, m, d, (s, dd) -> new Hit(s, dd));
        }
        hits.sort(Comparator.comparingInt(Hit::start));
        return hits.stream().map(Hit::date).toList();
    }

    private static <H> void take(List<H> hits, boolean[] used, Matcher m, LocalDate date, java.util.function.BiFunction<Integer, LocalDate, H> make) {
        if (date == null) {
            return;
        }
        for (int i = m.start(); i < m.end(); i++) {
            if (used[i]) {
                return;
            }
        }
        for (int i = m.start(); i < m.end(); i++) {
            used[i] = true;
        }
        hits.add(make.apply(m.start(), date));
    }

    /** Dates without a year resolve to the next occurrence. Impossible dates (30 February) yield null. */
    private static LocalDate safeDate(Integer year, Integer month, Integer day, LocalDate today, boolean explicitYear) {
        if (month == null || day == null) {
            return null;
        }
        try {
            if (explicitYear && year != null) {
                return LocalDate.of(year, month, day);
            }
            LocalDate d = LocalDate.of(today.getYear(), month, day);
            return d.isBefore(today) ? d.plusYears(1) : d;
        } catch (DateTimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ other fields

    private static Integer findBudget(String text) {
        Matcher m = BUDGET_BEFORE.matcher(text);
        if (m.find()) {
            return amount(m.group(1), m.group(2));
        }
        m = BUDGET_AFTER.matcher(text);
        if (m.find()) {
            return amount(m.group(1), m.group(2));
        }
        return null;
    }

    private static Integer amount(String digits, String suffix) {
        try {
            double v = Double.parseDouble(digits.replace(",", ""));
            if (suffix != null && !suffix.isBlank()) {
                v *= 1000;
            }
            int n = (int) Math.round(v);
            return n >= 50 && n <= 100000 ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer findTravellers(String text) {
        Matcher m = PEOPLE.matcher(text);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            return n >= 1 && n <= 12 ? n : null;
        }
        m = PEOPLE_FOR.matcher(text);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            return n >= 1 && n <= 12 ? n : null;
        }
        if (containsAny(text, "alone", "solo", "just me", "only me")) {
            return 1;
        }
        return null;
    }

    private static TransportMode findMode(String text) {
        Set<String> tokens = tokens(text);
        if (text.contains("share cab") || text.contains("shared cab") || text.contains("shared ride") || text.contains("ride share")
                || text.contains("carpool") || text.contains("car pool") || text.contains("shared car")) {
            return TransportMode.SHARED_CAR;
        }
        if (anyToken(tokens, "train", "rail", "railway", "irctc", "ట్రైన్", "రైలు", "रेल", "ट्रेन")) {
            return TransportMode.TRAIN;
        }
        if (anyToken(tokens, "bus", "buses", "volvo", "sleeper", "బస్సు", "బస్", "बस")) {
            return TransportMode.BUS;
        }
        if (anyToken(tokens, "cab", "taxi", "car", "ola", "uber", "క్యాబ్", "కారు", "कैब", "टैक्सी", "कार")) {
            return TransportMode.CAB;
        }
        return null;
    }

    private static Priority findPriority(String text) {
        Set<String> tokens = tokens(text);
        if (anyToken(tokens, "cheapest", "cheap", "sasta", "sasti", "chavaka", "చవక", "సస్తా", "सस्ता", "economical", "lowest")) {
            return Priority.CHEAPEST;
        }
        if (anyToken(tokens, "fastest", "quickest", "quick", "fast", "jaldi", "tvaraga", "త్వరగా", "जल्दी", "तेज़", "earliest")) {
            return Priority.FASTEST;
        }
        if (anyToken(tokens, "comfortable", "comfort", "comfy", "aaram", "आराम", "హాయిగా", "premium")) {
            return Priority.COMFORTABLE;
        }
        return null;
    }

    private static LocalTime findArriveBy(String text) {
        Matcher m = ARRIVE_BY.matcher(text);
        if (m.find()) {
            int h = Integer.parseInt(m.group(1));
            int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            boolean pm = m.group(3).startsWith("p");
            if (h >= 1 && h <= 12 && min < 60) {
                return LocalTime.of(h % 12 + (pm ? 12 : 0), min);
            }
        }
        m = ARRIVE_BY_24.matcher(text);
        if (m.find()) {
            int h = Integer.parseInt(m.group(1));
            int min = Integer.parseInt(m.group(2));
            if (h < 24 && min < 60) {
                return LocalTime.of(h, min);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ language and intent

    static String detectLanguage(String text) {
        int te = 0;
        int hi = 0;
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeBlock b = Character.UnicodeBlock.of(text.charAt(i));
            if (b == Character.UnicodeBlock.TELUGU) {
                te++;
            } else if (b == Character.UnicodeBlock.DEVANAGARI) {
                hi++;
            }
        }
        if (te > 0 || hi > 0) {
            return te >= hi ? "te-IN" : "hi-IN";
        }
        Set<String> tokens = tokens(text);
        long teHits = tokens.stream().filter(TELUGU_ROMAN::contains).count();
        long hiHits = tokens.stream().filter(HINDI_ROMAN::contains).count();
        if (teHits > 0 && teHits >= hiHits) {
            return "te-IN";
        }
        if (hiHits > 0) {
            return "hi-IN";
        }
        return "en-IN";
    }

    private static String intent(String text, TripDraft d) {
        boolean companions = containsAny(text, "companion", "co-traveller", "co traveller", "co-traveler", "travel buddy", "travel partner", "travelling together",
                "travel together", "anyone going", "someone going", "who is going", "who's going", "join me", "find travellers", "find travelers",
                "saathi", "sathi", "తోడు", "సహ ప్రయాణికు", "साथी", "हमसफ़र", "हमसफर");
        if (companions) {
            return "FIND_COMPANIONS";
        }
        if (d.sourceCity() != null || d.destinationCity() != null || d.travelDate() != null) {
            return "PLAN_TRIP";
        }
        if (containsAny(text, "my group", "my groups", "group", "గ్రూప్", "ग्रुप")) {
            return "SHOW_GROUPS";
        }
        if (containsAny(text, "hello", "hi ", "hey", "namaste", "namaskaram", "నమస్కారం", "नमस्ते", "help", "what can you do", "who are you")
                || text.equals("hi")) {
            return "GREETING";
        }
        if (d.travellers() != null || d.maxBudget() != null || d.preferredMode() != null || d.priority() != null || d.arriveBy() != null) {
            return "PLAN_TRIP";
        }
        return "OTHER";
    }

    // ------------------------------------------------------------------ utils

    private static Set<String> tokens(String text) {
        return new HashSet<>(Arrays.asList(text.split("[^\\p{L}\\p{M}\\p{N}]+")));
    }

    private static boolean anyToken(Set<String> tokens, String... words) {
        for (String w : words) {
            if (tokens.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String n : needles) {
            if (text.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static Integer num(String s) {
        return Integer.parseInt(s);
    }

    /** Converts Devanagari / Telugu digits to ASCII so "౨౦ డిసెంబర్" parses like "20 డిసెంబర్". */
    private static String asciiDigits(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > 127 && Character.isDigit(c)) {
                sb.append(Character.digit(c, 10));
            } else {
                sb.append(c);
            }
        }
        return Normalizer.normalize(sb, Normalizer.Form.NFC);
    }
}

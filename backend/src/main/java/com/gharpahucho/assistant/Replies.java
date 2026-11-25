package com.gharpahucho.assistant;

import java.util.Locale;

/**
 * Deterministic reply templates in English, Hindi and Telugu. They are the grounded source of every assistant answer:
 * Sarvam may rephrase them, but it never adds facts, and if it is unavailable these are returned as-is.
 */
final class Replies {

    private Replies() {
    }

    static String base(String languageCode) {
        if (languageCode == null) {
            return "en";
        }
        String b = languageCode.toLowerCase(Locale.ROOT).split("[-_]")[0];
        return b.equals("hi") || b.equals("te") ? b : "en";
    }

    static String greeting(String l) {
        return switch (l) {
            case "te" -> "నమస్కారం! నేను GharPahucho సహాయకుడిని. మీరు ఎక్కడి నుంచి ఎక్కడికి, ఏ తేదీన ప్రయాణించాలనుకుంటున్నారో చెప్పండి. ఉదాహరణ: \"20 డిసెంబర్ బెంగళూరు నుంచి హైదరాబాద్, బడ్జెట్ 1500\".";
            case "hi" -> "नमस्ते! मैं GharPahucho सहायक हूँ। बताइए आप कहाँ से कहाँ, किस तारीख को जाना चाहते हैं। उदाहरण: \"20 दिसंबर बेंगलुरु से हैदराबाद, बजट 1500\"।";
            default -> "Hi! I'm the GharPahucho assistant. Tell me where you're travelling from and to, and on which date. For example: \"Bangalore to Hyderabad on 20 December, budget 1500\".";
        };
    }

    static String askSource(String l) {
        return switch (l) {
            case "te" -> "మీరు ఏ నగరం నుంచి బయలుదేరుతున్నారు?";
            case "hi" -> "आप किस शहर से यात्रा शुरू कर रहे हैं?";
            default -> "Which city are you leaving from?";
        };
    }

    static String askDestination(String l) {
        return switch (l) {
            case "te" -> "మీరు ఏ నగరానికి వెళ్లాలనుకుంటున్నారు?";
            case "hi" -> "आप किस शहर जाना चाहते हैं?";
            default -> "Which city are you travelling to?";
        };
    }

    static String askDate(String l) {
        return switch (l) {
            case "te" -> "మీరు ఏ తేదీన ప్రయాణించాలనుకుంటున్నారు?";
            case "hi" -> "आप किस तारीख को यात्रा करना चाहते हैं?";
            default -> "Which date do you want to travel?";
        };
    }

    static String plannerOff(String l) {
        return switch (l) {
            case "te" -> "ట్రిప్ ప్లానర్ ప్రస్తుతం మీ కోసం అందుబాటులో లేదు.";
            case "hi" -> "ट्रिप प्लानर अभी आपके लिए उपलब्ध नहीं है।";
            default -> "The trip planner isn't available for you right now.";
        };
    }

    static String noOptions(String l, String from, String to) {
        return switch (l) {
            case "te" -> "%s నుంచి %s కి ఎంపికలు ఏవీ దొరకలేదు. తేదీ లేదా బడ్జెట్ మార్చి చూడండి.".formatted(from, to);
            case "hi" -> "%s से %s के लिए कोई विकल्प नहीं मिला। तारीख या बजट बदलकर देखें।".formatted(from, to);
            default -> "I couldn't find options from %s to %s. Try another date or a higher budget.".formatted(from, to);
        };
    }

    static String planIntro(String l, String from, String to, String date, int count) {
        return switch (l) {
            case "te" -> "%s నుంచి %s కి %s న %d ఎంపికలు దొరికాయి.".formatted(from, to, date, count);
            case "hi" -> "%s से %s, %s के लिए %d विकल्प मिले।".formatted(from, to, date, count);
            default -> "I found %d options from %s to %s on %s.".formatted(count, from, to, date);
        };
    }

    static String optionLine(String l, String label, String title, int cost, String duration, String depart) {
        return switch (l) {
            case "te" -> "%s: %s, ₹%d, %s, బయలుదేరే సమయం %s.".formatted(label, title, cost, duration, depart);
            case "hi" -> "%s: %s, ₹%d, %s, प्रस्थान %s।".formatted(label, title, cost, duration, depart);
            default -> "%s: %s, ₹%d per person, %s, departs %s.".formatted(label, title, cost, duration, depart);
        };
    }

    static String labelCheapest(String l) {
        return switch (l) {
            case "te" -> "చౌకైనది";
            case "hi" -> "सबसे सस्ता";
            default -> "Cheapest";
        };
    }

    static String labelFastest(String l) {
        return switch (l) {
            case "te" -> "వేగవంతమైనది";
            case "hi" -> "सबसे तेज़";
            default -> "Fastest";
        };
    }

    static String labelBest(String l) {
        return switch (l) {
            case "te" -> "ఉత్తమ ఎంపిక";
            case "hi" -> "सर्वश्रेष्ठ विकल्प";
            default -> "Best overall";
        };
    }

    static String labelOption(String l) {
        return switch (l) {
            case "te" -> "ఎంపిక";
            case "hi" -> "विकल्प";
            default -> "Option";
        };
    }

    static String budgetWarning(String l, int budget) {
        return switch (l) {
            case "te" -> "గమనిక: ₹%d బడ్జెట్‌లో ఏ ఎంపికా సరిపోలేదు; దగ్గరగా ఉన్నవి 'ప్రత్యామ్నాయం'గా చూపించాను.".formatted(budget);
            case "hi" -> "ध्यान दें: ₹%d के बजट में कोई विकल्प फिट नहीं हुआ; सबसे नज़दीकी विकल्प 'वैकल्पिक' के रूप में दिखाए हैं।".formatted(budget);
            default -> "Note: nothing fit your ₹%d budget, so the closest options are shown and labelled as alternatives.".formatted(budget);
        };
    }

    static String sampleDataNote(String l) {
        return switch (l) {
            case "te" -> "గమనిక: రైలు/బస్సు ధరలు, సమయాలు నమూనా డేటా మాత్రమే, లైవ్ లభ్యత కాదు.";
            case "hi" -> "ध्यान दें: ट्रेन/बस के किराए और समय नमूना डेटा हैं, लाइव उपलब्धता नहीं।";
            default -> "Note: train and bus fares and times are sample data, not live availability.";
        };
    }

    static String companionsOff(String l) {
        return switch (l) {
            case "te" -> "తోటి ప్రయాణికులను కనుగొనే ఫీచర్ ప్రస్తుతం మీ కోసం ఆఫ్‌లో ఉంది.";
            case "hi" -> "सहयात्री खोजने की सुविधा अभी आपके लिए बंद है।";
            default -> "Finding travel companions is switched off for you right now.";
        };
    }

    static String companionsFound(String l, int total, String from, String to) {
        return switch (l) {
            case "te" -> "%s నుంచి %s కి వెళ్లే %d అనుకూల ప్రయాణికులు దొరికారు.".formatted(from, to, total);
            case "hi" -> "%s से %s जा रहे %d संगत यात्री मिले।".formatted(from, to, total);
            default -> "I found %d compatible travellers going from %s to %s.".formatted(total, from, to);
        };
    }

    static String companionLine(String l, String name, int score, String reason) {
        return switch (l) {
            case "te" -> "%s: అనుకూలత %d/100. %s".formatted(name, score, reason);
            case "hi" -> "%s: संगतता %d/100। %s".formatted(name, score, reason);
            default -> "%s: %d/100 compatible. %s".formatted(name, score, reason);
        };
    }

    static String noCompanions(String l) {
        return switch (l) {
            case "te" -> "ప్రస్తుతం ఈ ప్రయాణానికి అనుకూల ప్రయాణికులు ఎవరూ లేరు. మీ ప్రయాణాన్ని పబ్లిష్ చేస్తే ఇతరులు మిమ్మల్ని కనుగొనగలరు.";
            case "hi" -> "इस यात्रा के लिए अभी कोई संगत यात्री नहीं है। अपनी यात्रा प्रकाशित करें ताकि दूसरे आपको ढूँढ सकें।";
            default -> "No compatible travellers for this journey yet. Publish your journey so others can find you.";
        };
    }

    static String groups(String l, int count) {
        return switch (l) {
            case "te" -> "మీరు %d ట్రావెల్ గ్రూప్‌లలో ఉన్నారు.".formatted(count);
            case "hi" -> "आप %d ट्रैवल ग्रुप में हैं।".formatted(count);
            default -> "You're in %d travel group%s.".formatted(count, count == 1 ? "" : "s");
        };
    }

    static String noGroups(String l) {
        return switch (l) {
            case "te" -> "మీరు ఇంకా ఏ ట్రావెల్ గ్రూప్‌లోనూ లేరు.";
            case "hi" -> "आप अभी किसी ट्रैवल ग्रुप में नहीं हैं।";
            default -> "You're not in any travel group yet.";
        };
    }

    static String didNotUnderstand(String l) {
        return switch (l) {
            case "te" -> "నాకు అర్థం కాలేదు. ఎక్కడి నుంచి ఎక్కడికి, ఏ తేదీన వెళ్లాలో చెప్పండి.";
            case "hi" -> "मैं समझ नहीं पाया। बताइए कहाँ से कहाँ और किस तारीख को जाना है।";
            default -> "I didn't catch that. Tell me where from, where to, and the date.";
        };
    }

    static String assumedHome(String l, String city) {
        return switch (l) {
            case "te" -> "మీ హోమ్ సిటీ %s నుంచి అని భావిస్తున్నాను.".formatted(city);
            case "hi" -> "आपके होम सिटी %s से मान रहा हूँ।".formatted(city);
            default -> "Assuming you're leaving from your home city, %s.".formatted(city);
        };
    }

    static String mins(String l, int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        return switch (l) {
            case "te" -> h > 0 ? "%d గం %d నిమి".formatted(h, m) : "%d నిమి".formatted(m);
            case "hi" -> h > 0 ? "%d घंटे %d मिनट".formatted(h, m) : "%d मिनट".formatted(m);
            default -> h > 0 ? "%dh %02dm".formatted(h, m) : "%dm".formatted(m);
        };
    }
}

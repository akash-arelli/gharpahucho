package com.gharpahucho.service;

/** Fixed, human-written wording for the family page (English, Hindi, Telugu). No AI: this is a safety-adjacent message. */
final class FamilyTexts {

    private FamilyTexts() {
    }

    static String statusLabel(String lang, String status) {
        return switch (lang) {
            case "hi" -> switch (status) {
                case "ON_THE_WAY" -> "रास्ते में हैं";
                case "DELAYED" -> "थोड़ी देरी हो रही है";
                case "REACHED" -> "पहुँच गए";
                default -> "यात्रा की योजना बनी है";
            };
            case "te" -> switch (status) {
                case "ON_THE_WAY" -> "ప్రయాణంలో ఉన్నారు";
                case "DELAYED" -> "కొంచెం ఆలస్యం అవుతోంది";
                case "REACHED" -> "చేరుకున్నారు";
                default -> "ప్రయాణం ప్లాన్ చేశారు";
            };
            default -> switch (status) {
                case "ON_THE_WAY" -> "On the way";
                case "DELAYED" -> "Running a little late";
                case "REACHED" -> "Reached safely";
                default -> "Trip planned";
            };
        };
    }

    static String headline(String lang, String name, String destination, String status) {
        return switch (lang) {
            case "hi" -> switch (status) {
                case "ON_THE_WAY" -> name + " " + destination + " के रास्ते में हैं";
                case "DELAYED" -> name + " की यात्रा में थोड़ी देरी है";
                case "REACHED" -> name + " " + destination + " पहुँच गए हैं";
                default -> name + " " + destination + " जा रहे हैं";
            };
            case "te" -> switch (status) {
                case "ON_THE_WAY" -> name + " " + destination + " కి ప్రయాణంలో ఉన్నారు";
                case "DELAYED" -> name + " ప్రయాణంలో కొంచెం ఆలస్యం అవుతోంది";
                case "REACHED" -> name + " " + destination + " చేరుకున్నారు";
                default -> name + " " + destination + " కి వెళ్తున్నారు";
            };
            default -> switch (status) {
                case "ON_THE_WAY" -> name + " is on the way to " + destination;
                case "DELAYED" -> name + " is running a little late";
                case "REACHED" -> name + " has reached " + destination;
                default -> name + " is travelling to " + destination;
            };
        };
    }

    static String footer(String lang, String name) {
        return switch (lang) {
            case "hi" -> name + " ने GharPahucho से साझा किया। फ़ोन नंबर या सटीक लोकेशन नहीं दिखाया जाता।";
            case "te" -> name + " GharPahucho ద్వారా షేర్ చేశారు. ఫోన్ నంబర్ లేదా ఖచ్చితమైన లొకేషన్ చూపబడదు.";
            default -> "Shared by " + name + " using GharPahucho. No phone number or exact location is shown.";
        };
    }
}

package com.gharpahucho.integration.maps;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline gazetteer of major Indian travel hubs with English, Hindi and Telugu names. It makes city recognition
 * deterministic and fast, and is what the AI output is validated against: a city the model "extracts" only counts
 * if it resolves here (or through OpenStreetMap Nominatim when that is enabled).
 */
@Component
public class CityGazetteer {

    /** A city name found in free text. */
    public record Mention(City city, int start, int end, String text) {
    }

    private final List<City> cities = new ArrayList<>();
    private final Map<String, City> byAlias = new HashMap<>();
    private List<String> aliasesLongestFirst = List.of();

    public CityGazetteer() {
        add("Bengaluru", "Karnataka", 12.9716, 77.5946, "bangalore,bengaluru,bengalooru,bangaluru,blr,बेंगलुरु,बैंगलोर,बेंगलूरु,బెంగళూరు,బెంగుళూరు,బెంగళూర్,బెంగుళూర్",
                "Whitefield:12.9698:77.7500;Electronic City:12.8452:77.6602;Majestic:12.9767:77.5713;Koramangala:12.9352:77.6245;Hebbal:13.0358:77.5970;Yelahanka:13.1007:77.5963;Marathahalli:12.9591:77.6974;Jayanagar:12.9308:77.5838;Silk Board:12.9177:77.6233");
        add("Hyderabad", "Telangana", 17.3850, 78.4867, "hyderabad,hyd,haidarabad,हैदराबाद,హైదరాబాద్,హైదరాబాదు,హైదరాబాద్‌",
                "Hitec City:17.4435:78.3772;Gachibowli:17.4401:78.3489;Secunderabad:17.4399:78.4983;Uppal:17.4058:78.5591;Kukatpally:17.4948:78.3996;LB Nagar:17.3457:78.5522;Miyapur:17.4968:78.3614;Ameerpet:17.4375:78.4483");
        add("Chennai", "Tamil Nadu", 13.0827, 80.2707, "chennai,madras,चेन्नई,చెన్నై",
                "T Nagar:13.0418:80.2341;Tambaram:12.9249:80.1000;Velachery:12.9815:80.2180;Anna Nagar:13.0850:80.2101;OMR:12.9010:80.2279");
        add("Mumbai", "Maharashtra", 19.0760, 72.8777, "mumbai,bombay,मुंबई,ముంబై,ముంబయి",
                "Andheri:19.1136:72.8697;Thane:19.2183:72.9781;Borivali:19.2307:72.8567;Dadar:19.0178:72.8478;Navi Mumbai:19.0330:73.0297");
        add("Pune", "Maharashtra", 18.5204, 73.8567, "pune,poona,पुणे,పూణే,పుణె",
                "Hinjewadi:18.5912:73.7389;Kothrud:18.5074:73.8077;Shivajinagar:18.5308:73.8475;Hadapsar:18.5089:73.9259");
        add("Delhi", "Delhi", 28.6139, 77.2090, "delhi,new delhi,dilli,दिल्ली,ఢిల్లీ",
                "Dwarka:28.5921:77.0460;Noida:28.5355:77.3910;Gurugram:28.4595:77.0266;Rohini:28.7041:77.1025;Saket:28.5245:77.2066");
        add("Kolkata", "West Bengal", 22.5726, 88.3639, "kolkata,calcutta,कोलकाता,కోల్‌కతా,కోల్కతా",
                "Salt Lake:22.5867:88.4170;Howrah:22.5958:88.2636");
        add("Ahmedabad", "Gujarat", 23.0225, 72.5714, "ahmedabad,अहमदाबाद,అహ్మదాబాద్", "");
        add("Vijayawada", "Andhra Pradesh", 16.5062, 80.6480, "vijayawada,bezawada,विजयवाड़ा,విజయవాడ", "Benz Circle:16.4970:80.6560;Governorpet:16.5141:80.6143");
        add("Visakhapatnam", "Andhra Pradesh", 17.6868, 83.2185, "visakhapatnam,vishakhapatnam,vizag,vishakapatnam,विशाखापत्तनम,విశాఖపట్నం,వైజాగ్,విశాఖ", "");
        add("Tirupati", "Andhra Pradesh", 13.6288, 79.4192, "tirupati,tirupathi,तिरुपति,తిరుపతి", "");
        add("Warangal", "Telangana", 17.9689, 79.5941, "warangal,वारंगल,వరంగల్,వరంగల్లు", "");
        add("Guntur", "Andhra Pradesh", 16.3067, 80.4365, "guntur,गुंटूर,గుంటూరు", "");
        add("Rajahmundry", "Andhra Pradesh", 17.0005, 81.8040, "rajahmundry,rajamahendravaram,రాజమండ్రి,రాజమహేంద్రవరం", "");
        add("Kurnool", "Andhra Pradesh", 15.8281, 78.0373, "kurnool,కర్నూలు,कर्नूल", "");
        add("Nellore", "Andhra Pradesh", 14.4426, 79.9865, "nellore,నెల్లూరు,नेल्लोर", "");
        add("Coimbatore", "Tamil Nadu", 11.0168, 76.9558, "coimbatore,kovai,कोयंबटूर,కోయంబత్తూరు", "");
        add("Madurai", "Tamil Nadu", 9.9252, 78.1198, "madurai,मदुरै,మదురై", "");
        add("Kochi", "Kerala", 9.9312, 76.2673, "kochi,cochin,ernakulam,कोच्चि,కొచ్చి", "");
        add("Thiruvananthapuram", "Kerala", 8.5241, 76.9366, "thiruvananthapuram,trivandrum,तिरुवनंतपुरम,తిరువనంతపురం", "");
        add("Mysuru", "Karnataka", 12.2958, 76.6394, "mysuru,mysore,मैसूर,మైసూరు", "");
        add("Mangaluru", "Karnataka", 12.9141, 74.8560, "mangaluru,mangalore,मंगलुरु,మంగళూరు", "");
        add("Hubballi", "Karnataka", 15.3647, 75.1240, "hubballi,hubli,हुबली,హుబ్బళ్లి", "");
        add("Nagpur", "Maharashtra", 21.1458, 79.0882, "nagpur,नागपुर,నాగ్‌పూర్,నాగపూర్", "");
        add("Jaipur", "Rajasthan", 26.9124, 75.7873, "jaipur,जयपुर,జైపూర్", "");
        add("Lucknow", "Uttar Pradesh", 26.8467, 80.9462, "lucknow,लखनऊ,లక్నో", "");
        add("Patna", "Bihar", 25.5941, 85.1376, "patna,पटना,పాట్నా", "");
        add("Bhubaneswar", "Odisha", 20.2961, 85.8245, "bhubaneswar,bhubaneshwar,भुवनेश्वर,భువనేశ్వర్", "");
        add("Indore", "Madhya Pradesh", 22.7196, 75.8577, "indore,इंदौर,ఇండోర్", "");
        add("Chandigarh", "Chandigarh", 30.7333, 76.7794, "chandigarh,चंडीगढ़,చండీగఢ్", "");
        add("Surat", "Gujarat", 21.1702, 72.8311, "surat,सूरत,సూరత్", "");
        add("Goa", "Goa", 15.4909, 73.8278, "goa,panaji,panjim,गोवा,గోవా", "");
        rebuildIndex();
    }

    private void add(String name, String state, double lat, double lon, String aliases, String localities) {
        List<String> aliasList = new ArrayList<>();
        aliasList.add(name.toLowerCase(Locale.ROOT));
        for (String a : aliases.split(",")) {
            if (!a.isBlank()) {
                aliasList.add(norm(a));
            }
        }
        List<City.Locality> locs = new ArrayList<>();
        if (!localities.isBlank()) {
            for (String l : localities.split(";")) {
                String[] p = l.split(":");
                locs.add(new City.Locality(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2])));
            }
        }
        cities.add(new City(name, state, lat, lon, aliasList.stream().distinct().toList(), locs));
    }

    private void rebuildIndex() {
        byAlias.clear();
        for (City c : cities) {
            for (String a : c.aliases()) {
                byAlias.put(a, c);
            }
        }
        aliasesLongestFirst = byAlias.keySet().stream().sorted((a, b) -> b.length() - a.length()).toList();
    }

    public List<City> all() {
        return Collections.unmodifiableList(cities);
    }

    public static String norm(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFC).toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    /** Exact (case-insensitive) match on a city name or any alias. */
    public Optional<City> find(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byAlias.get(norm(text)));
    }

    public Optional<City> findByName(String canonicalName) {
        return cities.stream().filter(c -> c.name().equalsIgnoreCase(canonicalName)).findFirst();
    }

    public Optional<City.Locality> locality(City city, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String n = norm(name);
        return city.localities().stream().filter(l -> norm(l.name()).equals(n)).findFirst();
    }

    /** Prefix-first search used by the autocomplete. */
    public List<City> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return cities.stream().limit(limit).toList();
        }
        String q = norm(query);
        List<City> out = new ArrayList<>();
        for (City c : cities) {
            if (c.aliases().stream().anyMatch(a -> a.startsWith(q))) {
                out.add(c);
            }
        }
        for (City c : cities) {
            if (!out.contains(c) && c.aliases().stream().anyMatch(a -> a.contains(q))) {
                out.add(c);
            }
        }
        return out.stream().limit(limit).toList();
    }

    /** Finds every known city mentioned in free text, in order of appearance, non-overlapping. */
    public List<Mention> mentions(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String lower = norm(text);
        List<Mention> found = new ArrayList<>();
        boolean[] used = new boolean[lower.length()];
        for (String alias : aliasesLongestFirst) {
            boolean latin = alias.chars().allMatch(ch -> ch < 0x0250);
            Pattern p = latin
                    ? Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(alias) + "(?![\\p{L}\\p{N}])")
                    : Pattern.compile(Pattern.quote(alias));
            Matcher m = p.matcher(lower);
            while (m.find()) {
                boolean overlap = false;
                for (int i = m.start(); i < m.end(); i++) {
                    if (used[i]) {
                        overlap = true;
                        break;
                    }
                }
                if (!overlap) {
                    for (int i = m.start(); i < m.end(); i++) {
                        used[i] = true;
                    }
                    found.add(new Mention(byAlias.get(alias), m.start(), m.end(), lower.substring(m.start(), m.end())));
                }
            }
        }
        found.sort(Comparator.comparingInt(Mention::start));
        return found;
    }
}

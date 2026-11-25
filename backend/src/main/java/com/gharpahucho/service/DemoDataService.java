package com.gharpahucho.service;

import com.gharpahucho.config.GharPahuchoProperties;
import com.gharpahucho.entity.*;
import com.gharpahucho.integration.maps.City;
import com.gharpahucho.integration.maps.CityGazetteer;
import com.gharpahucho.integration.maps.GeoUtil;
import com.gharpahucho.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds everything needed to demo GharPahucho without any third-party account: a simulated train/bus inventory
 * (always labelled as sample data) and a cast of clearly fictional travellers whose journeys cluster around one date.
 * Demo travellers are flagged {@code demo_account}; a reset removes only them (foreign keys cascade), never real users.
 */
@Service
@RequiredArgsConstructor
public class DemoDataService {

    private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);

    public static final String BETA_LOGIN_EMAIL = "neha.beta@gharpahucho.example";
    private static final String DOMAIN = "@gharpahucho.example";

    private final GharPahuchoProperties props;
    private final UserRepository users;
    private final UserPreferenceRepository prefs;
    private final TripRepository trips;
    private final TravelOptionRepository options;
    private final TravelInterestRepository interests;
    private final TravelGroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupMessageRepository messages;
    private final ActivityEventRepository activities;
    private final CityGazetteer gazetteer;
    private final PasswordEncoder encoder;
    private final Clock clock;

    @PersistenceContext
    private EntityManager em;

    /** Idempotent: fills in whatever is missing. Called once at startup by {@link DemoDataSeeder}. */
    @Transactional
    public void seedIfMissing() {
        if (options.count() == 0) {
            seedInventory();
        }
        if (!users.existsByEmailIgnoreCase(UserService.DEMO_LOGIN_EMAIL)) {
            seedPeople();
        }
    }

    /** Removes every demo traveller (and what depends on them) and rebuilds the cast with fresh, relative dates. */
    @Transactional
    public void reset() {
        int removed = users.deleteDemoAccounts();
        em.flush();
        em.clear();
        if (options.count() == 0) {
            seedInventory();
        }
        seedPeople();
        log.info("event=demo_reset removedDemoUsers={}", removed);
    }

    // ------------------------------------------------------------------ simulated inventory

    private void seedInventory() {
        List<TravelOption> rows = new ArrayList<>();
        List<City> cities = gazetteer.all();
        for (City a : cities) {
            for (City b : cities) {
                if (a == b) {
                    continue;
                }
                double road = GeoUtil.haversineKm(a.lat(), a.lon(), b.lat(), b.lon()) * 1.25;
                if (road < 60 || road > 2600) {
                    continue;
                }
                addBuses(rows, a, b, road);
                addTrains(rows, a, b, road);
            }
        }
        options.saveAll(rows);
        log.info("event=demo_inventory_seeded rows={}", rows.size());
    }

    private void addBuses(List<TravelOption> rows, City a, City b, double road) {
        if (road > 1500) {
            return;
        }
        rows.add(option(TransportMode.BUS, "Sample Travels", "Overnight AC Sleeper", a, b, LocalTime.of(21, 30), road / 52 + 0.5, 0.85 * road + 150, 4, "2 rest stops"));
        if (road >= 250) {
            rows.add(option(TransportMode.BUS, "Demo Roadways", "Night Non-AC Sleeper", a, b, LocalTime.of(20, 15), road / 46 + 0.5, 0.58 * road + 90, 3, "3 rest stops"));
        }
        if (road <= 700) {
            rows.add(option(TransportMode.BUS, "Sample Travels", "Morning AC Seater", a, b, LocalTime.of(6, 30), road / 50 + 0.4, 0.62 * road + 80, 3, "1 rest stop"));
        }
        if (road <= 500) {
            rows.add(option(TransportMode.BUS, "Demo Roadways", "Afternoon Express", a, b, LocalTime.of(14, 0), road / 48 + 0.3, 0.55 * road + 70, 3, "1 rest stop"));
        }
    }

    private void addTrains(List<TravelOption> rows, City a, City b, double road) {
        if (road < 150) {
            return;
        }
        if (road <= 600) {
            rows.add(option(TransportMode.TRAIN, "Sample Rail", "Intercity Express", a, b, LocalTime.of(6, 0), road / 58 + 0.3, 0.40 * road + 70, 3, "Major stops only"));
        }
        if (road >= 350) {
            rows.add(option(TransportMode.TRAIN, "Sample Rail", "Overnight Superfast (Sleeper)", a, b, LocalTime.of(20, 45), road / 62 + 0.5, 0.48 * road + 95, 3, "Limited stops"));
        }
        rows.add(option(TransportMode.TRAIN, "Sample Rail", "Premium AC Express", a, b, LocalTime.of(16, 30), road / 68 + 0.4, 1.35 * road + 210, 4, "Limited stops"));
    }

    private static TravelOption option(TransportMode mode, String operator, String service, City a, City b, LocalTime dep, double hours, double fare,
                                       int comfort, String stops) {
        TravelOption o = new TravelOption();
        o.setMode(mode);
        o.setOperatorName(operator);
        o.setServiceName(service + " (sample)");
        o.setOriginCity(a.name());
        o.setDestinationCity(b.name());
        o.setDepartureTime(dep);
        o.setDurationMinutes((int) Math.round(hours * 60 / 5.0) * 5);
        o.setBaseFare((int) Math.round(fare / 5.0) * 5);
        o.setComfortScore(comfort);
        o.setStopsText(stops);
        o.setDataSource(DataSource.SIMULATED_INVENTORY);
        return o;
    }

    // ------------------------------------------------------------------ people and journeys

    private void seedPeople() {
        LocalDate anchor = LocalDate.now(clock).plusDays(21);
        String hash = encoder.encode(props.getDemo().getDemoPassword());

        User priya = person("priya.demo", "Priya Sharma", "Bengaluru", "Whitefield", "te", false, hash);
        User arjun = person("arjun.reddy", "Arjun Reddy", "Bengaluru", "Electronic City", "te", false, hash);
        User sneha = person("sneha.iyer", "Sneha Iyer", "Bengaluru", "Koramangala", "en", false, hash);
        User karthik = person("karthik.naidu", "Karthik Naidu", "Mysuru", null, "te", false, hash);
        User meera = person("meera.krishnan", "Meera Krishnan", "Chennai", "Velachery", "en", false, hash);
        User rahul = person("rahul.verma", "Rahul Verma", "Bengaluru", "Hebbal", "hi", false, hash);
        User ananya = person("ananya.rao", "Ananya Rao", "Bengaluru", "Hebbal", "en", false, hash);
        User imran = person("imran.khan", "Imran Khan", "Bengaluru", "Marathahalli", "hi", false, hash);
        User divya = person("divya.menon", "Divya Menon", "Kochi", null, "en", false, hash);
        User vikram = person("vikram.singh", "Vikram Singh", "Delhi", "Dwarka", "hi", false, hash);
        User lakshmi = person("lakshmi.prasad", "Lakshmi Prasad", "Hyderabad", "Kukatpally", "te", false, hash);
        User suresh = person("suresh.babu", "Suresh Babu", "Bengaluru", "Yelahanka", "te", false, hash);
        User pooja = person("pooja.desai", "Pooja Desai", "Pune", "Kothrud", "en", false, hash);
        User ravi = person("ravi.teja", "Ravi Teja", "Hyderabad", "Gachibowli", "te", false, hash);
        User neha = person("neha.beta", "Neha Gupta", "Bengaluru", "Koramangala", "en", true, hash);

        // Priya: the demo traveller. Her journey is discoverable so the matching demo is immediate.
        Trip priyaTrip = trip(priya, "Bengaluru", "Whitefield", "Hyderabad", anchor, 1, 1500, TransportMode.ANY, Priority.CHEAPEST, 1, 0, null,
                true, Visibility.DISCOVERABLE, "Heading home for the festival. Happy to share a cab.", LocalTime.of(21, 0));
        trip(priya, "Hyderabad", null, "Bengaluru", anchor.plusDays(9), 0, 1500, TransportMode.ANY, Priority.FASTEST, 1, 0, null,
                false, Visibility.PRIVATE, "Return journey (private).", null);

        trip(arjun, "Bengaluru", "Electronic City", "Hyderabad", anchor, 1, 1800, TransportMode.CAB, Priority.COMFORTABLE, 1, 3, 650,
                true, Visibility.DISCOVERABLE, "Driving to Hyderabad, 3 seats free. Leaving around 9 PM.", LocalTime.of(21, 0));
        trip(sneha, "Bengaluru", "Koramangala", "Hyderabad", anchor.plusDays(1), 1, 1400, TransportMode.TRAIN, Priority.CHEAPEST, 1, 0, null,
                true, Visibility.DISCOVERABLE, "Prefer the overnight train.", null);
        Trip karthikTrip = trip(karthik, "Mysuru", null, "Hyderabad", anchor, 1, 2000, TransportMode.ANY, Priority.FASTEST, 1, 0, null,
                true, Visibility.DISCOVERABLE, "Can meet along the Bengaluru road.", null);
        trip(meera, "Chennai", "Velachery", "Hyderabad", anchor, 0, 1500, TransportMode.BUS, Priority.CHEAPEST, 2, 0, null,
                true, Visibility.DISCOVERABLE, "Travelling with my sister.", null);
        trip(rahul, "Bengaluru", "Hebbal", "Hyderabad", anchor.plusDays(3), 0, 1200, TransportMode.BUS, Priority.CHEAPEST, 1, 0, null,
                true, Visibility.DISCOVERABLE, "Flexible on bus timing.", null);
        Trip ananyaTrip = trip(ananya, "Bengaluru", "Hebbal", "Hyderabad", anchor, 0, 1600, TransportMode.SHARED_CAR, Priority.CHEAPEST, 1, 2, 700,
                true, Visibility.DISCOVERABLE, "Two seats in my car, non-smoking.", LocalTime.of(22, 0));
        trip(imran, "Bengaluru", "Marathahalli", "Vijayawada", anchor, 1, 1500, TransportMode.ANY, Priority.CHEAPEST, 1, 0, null,
                true, Visibility.DISCOVERABLE, "Going via Hyderabad highway, then onwards.", null);
        trip(divya, "Kochi", null, "Chennai", anchor, 0, 1300, TransportMode.TRAIN, Priority.CHEAPEST, 1, 0, null,
                true, Visibility.DISCOVERABLE, null, null);
        trip(vikram, "Delhi", "Dwarka", "Jaipur", anchor.plusDays(2), 0, 900, TransportMode.BUS, Priority.CHEAPEST, 1, 0, null,
                true, Visibility.DISCOVERABLE, null, null);
        trip(lakshmi, "Hyderabad", "Kukatpally", "Vijayawada", anchor.plusDays(1), 0, 800, TransportMode.CAB, Priority.COMFORTABLE, 1, 2, 500,
                true, Visibility.DISCOVERABLE, "Cab to Vijayawada, 2 seats open.", LocalTime.of(7, 0));
        trip(suresh, "Bengaluru", "Yelahanka", "Tirupati", anchor.plusDays(2), 0, 900, TransportMode.ANY, Priority.CHEAPEST, 1, 0, null,
                true, Visibility.DISCOVERABLE, null, null);
        trip(pooja, "Pune", "Kothrud", "Mumbai", anchor, 0, 700, TransportMode.CAB, Priority.FASTEST, 1, 3, 350,
                true, Visibility.DISCOVERABLE, null, LocalTime.of(8, 0));
        trip(ravi, "Hyderabad", "Gachibowli", "Bengaluru", anchor.plusDays(5), 1, 1500, TransportMode.ANY, Priority.CHEAPEST, 1, 0, null,
                true, Visibility.DISCOVERABLE, null, null);
        trip(neha, "Bengaluru", "Koramangala", "Hyderabad", anchor, 1, 1600, TransportMode.ANY, Priority.CHEAPEST, 1, 1, 600,
                true, Visibility.DISCOVERABLE, "Beta tester account used to show Flagsmith segmentation.", LocalTime.of(20, 30));

        // A pending request to Priya and one she can answer in the demo.
        interest(ananya, priya, ananyaTrip, priyaTrip, "Hi! I'm driving from Hebbal. Want to share the ride?");
        interest(karthik, priya, karthikTrip, priyaTrip, "I can join you at the Bengaluru highway exit.");

        // A group Priya has been invited to.
        TravelGroup g = new TravelGroup();
        g.setName("Bengaluru → Hyderabad · festive ride");
        g.setOwner(arjun);
        g.setSourceCity("Bengaluru");
        g.setDestinationCity("Hyderabad");
        g.setTravelDate(anchor);
        g.setPlanMode(TransportMode.CAB);
        g.setPlanNotes("One cab, leaving after dinner. Split fuel and tolls equally.");
        g.setMeetingArea("Silk Board junction");
        g.setPlannedDepartureTime(LocalTime.of(21, 0));
        g.setMaxMembers(4);
        groups.save(g);
        member(g, arjun, MemberRole.OWNER, MemberStatus.JOINED);
        member(g, sneha, MemberRole.MEMBER, MemberStatus.JOINED);
        member(g, priya, MemberRole.MEMBER, MemberStatus.INVITED);
        message(g, arjun, "Welcome! I'm planning to leave Silk Board around 9 PM.", 90);
        message(g, sneha, "Works for me. Should we split tolls too?", 60);
        message(g, arjun, "Yes, equal split on fuel and tolls.", 45);

        activity(priya, "WELCOME", "Welcome to GharPahucho. Your journey is already visible to compatible travellers.", 240);
        activity(priya, "GROUP_INVITED", "You were invited to \"" + g.getName() + "\"", 90);
        activity(priya, "INTEREST_RECEIVED", "Ananya R. wants to travel with you to Hyderabad", 50);
        activity(neha, "WELCOME", "Welcome! This beta-tester account shows Flagsmith segment targeting.", 120);

        log.info("event=demo_people_seeded users=15 anchorDate={} login={} password=(see GP_DEMO_PASSWORD)", anchor, UserService.DEMO_LOGIN_EMAIL);
    }

    private User person(String handle, String name, String city, String locality, String language, boolean beta, String hash) {
        User u = new User();
        u.setEmail(handle + DOMAIN);
        u.setPasswordHash(hash);
        u.setDisplayName(name);
        u.setHomeCity(city);
        u.setHomeLocality(locality);
        u.setPreferredLanguage(language);
        u.setOnboarded(true);
        u.setBetaTester(beta);
        u.setDemoAccount(true);
        // Demo accounts start on the free plan so the upgrade (demo checkout) is part of the walkthrough.
        u.setPlan("free");
        users.save(u);
        UserPreference p = new UserPreference();
        p.setUser(u);
        p.setDiscoverable(true);
        p.setFirstNameOnly(!handle.equals("priya.demo"));
        prefs.save(p);
        return u;
    }

    private Trip trip(User owner, String from, String fromLocality, String to, LocalDate date, int flex, Integer budget, TransportMode mode,
                      Priority priority, int travellers, int seats, Integer costPerSeat, boolean share, Visibility visibility, String notes,
                      LocalTime departure) {
        City a = gazetteer.findByName(from).orElseThrow();
        City b = gazetteer.findByName(to).orElseThrow();
        Trip t = new Trip();
        t.setOwner(owner);
        t.setSourceCity(a.name());
        t.setSourceLat(a.lat());
        t.setSourceLon(a.lon());
        if (fromLocality != null) {
            t.setSourceLocality(fromLocality);
            gazetteer.locality(a, fromLocality).ifPresent(l -> {
                t.setSourceLat(l.lat());
                t.setSourceLon(l.lon());
            });
        }
        t.setDestinationCity(b.name());
        t.setDestinationLat(b.lat());
        t.setDestinationLon(b.lon());
        t.setTravelDate(date);
        t.setFlexDays(flex);
        t.setTravellers(travellers);
        t.setMaxBudget(budget);
        t.setPreferredMode(mode);
        t.setPriority(priority);
        t.setSeatsAvailable(seats);
        t.setCostPerSeat(costPerSeat);
        t.setWillingToShare(share);
        t.setVisibility(visibility);
        t.setNotes(notes);
        t.setDepartureTime(departure);
        t.setDataOrigin(DataOrigin.DEMO_SEED);
        return trips.save(t);
    }

    private void interest(User from, User to, Trip fromTrip, Trip toTrip, String message) {
        TravelInterest i = new TravelInterest();
        i.setFromUser(from);
        i.setToUser(to);
        i.setFromTrip(fromTrip);
        i.setToTrip(toTrip);
        i.setMessage(message);
        interests.save(i);
    }

    private void member(TravelGroup g, User u, MemberRole role, MemberStatus status) {
        GroupMember m = new GroupMember();
        m.setGroup(g);
        m.setUser(u);
        m.setRole(role);
        m.setStatus(status);
        if (status == MemberStatus.JOINED) {
            m.setJoinedAt(Instant.now());
        }
        members.save(m);
    }

    private void message(TravelGroup g, User sender, String body, int minutesAgo) {
        GroupMessage m = new GroupMessage();
        m.setGroup(g);
        m.setSender(sender);
        m.setBody(body);
        m.setCreatedAt(Instant.now().minus(minutesAgo, ChronoUnit.MINUTES));
        messages.save(m);
    }

    private void activity(User user, String type, String message, int minutesAgo) {
        ActivityEvent e = new ActivityEvent();
        e.setUser(user);
        e.setType(type);
        e.setMessage(message);
        e.setCreatedAt(Instant.now().minus(minutesAgo, ChronoUnit.MINUTES));
        activities.save(e);
    }
}

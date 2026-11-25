package com.gharpahucho.integration.transport;

import com.gharpahucho.entity.DataSource;
import com.gharpahucho.entity.TravelOption;
import com.gharpahucho.repository.TravelOptionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SIMULATED train and bus inventory read from the {@code travel_option} table. Every result is labelled
 * {@link DataSource#SIMULATED_INVENTORY} and its availability reads "sample schedule: not verified".
 * Replace or complement this class with a real authorised provider adapter to go live.
 */
@Component
public class SimulatedInventoryProvider implements TransportProvider {

    private final TravelOptionRepository repository;
    private final FestiveCalendar festivals;

    public SimulatedInventoryProvider(TravelOptionRepository repository, FestiveCalendar festivals) {
        this.repository = repository;
        this.festivals = festivals;
    }

    @Override
    public String id() {
        return "simulated-inventory";
    }

    @Override
    public DataSource dataSource() {
        return DataSource.SIMULATED_INVENTORY;
    }

    @Override
    public List<Candidate> search(SearchContext ctx) {
        return searchBetween(ctx.from().name(), ctx.to().name(), ctx.searchFrom(), ctx.searchTo(), ctx.now());
    }

    /** Also used by the connection builder for the individual legs of a one-change journey. */
    public List<Candidate> searchBetween(String origin, String destination, LocalDate from, LocalDate to, LocalDateTime notBefore) {
        List<TravelOption> services = repository.findByOriginCityAndDestinationCityAndActiveTrue(origin, destination);
        List<Candidate> out = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            FestiveCalendar.Demand demand = festivals.demandOn(date);
            String note = festivals.noteFor(demand);
            for (TravelOption s : services) {
                LocalDateTime dep = date.atTime(s.getDepartureTime());
                if (dep.isBefore(notBefore)) {
                    continue;
                }
                LocalDateTime arr = dep.plusMinutes(s.getDurationMinutes());
                int fare = roundTo5(s.getBaseFare() * demand.multiplier());
                String id = "sim:" + s.getId() + ":" + date;
                out.add(new Candidate(id, s.getMode(), s.getOperatorName(), s.getServiceName(), dep, arr, fare, 0,
                        s.getStopsText(), DataSource.SIMULATED_INVENTORY, "SAMPLE_UNVERIFIED",
                        "Sample schedule: availability not verified", s.getComfortScore(), null, null, null,
                        List.of(new Candidate.Leg(s.getMode(), origin, destination, dep, arr, s.getOperatorName(), s.getServiceName())),
                        note));
            }
        }
        return out;
    }

    private static int roundTo5(double v) {
        return (int) (Math.round(v / 5.0) * 5);
    }
}

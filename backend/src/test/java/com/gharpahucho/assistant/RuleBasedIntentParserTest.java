package com.gharpahucho.assistant;

import com.gharpahucho.assistant.RuleBasedIntentParser.Parsed;
import com.gharpahucho.entity.Priority;
import com.gharpahucho.entity.TransportMode;
import com.gharpahucho.integration.maps.CityGazetteer;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedIntentParserTest {

    // Fixed "today": 25 Nov 2026, so relative and year-less dates are deterministic.
    private final Clock clock = Clock.fixed(LocalDate.of(2026, 11, 25).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
    private final RuleBasedIntentParser parser = new RuleBasedIntentParser(new CityGazetteer(), clock);

    @Test
    void parsesTheRomanisedTeluguExampleFromTheBrief() {
        Parsed p = parser.parse("Naaku December 20th Bangalore nunchi Hyderabad vellali. Budget 1500.");
        assertThat(p.draft().sourceCity()).isEqualTo("Bengaluru");
        assertThat(p.draft().destinationCity()).isEqualTo("Hyderabad");
        assertThat(p.draft().travelDate()).isEqualTo(LocalDate.of(2026, 12, 20));
        assertThat(p.draft().maxBudget()).isEqualTo(1500);
        assertThat(p.language()).isEqualTo("te-IN");
        assertThat(p.intent()).isEqualTo("PLAN_TRIP");
    }

    @Test
    void parsesTeluguScript() {
        Parsed p = parser.parse("20 డిసెంబర్ బెంగళూరు నుంచి హైదరాబాద్ వెళ్ళాలి బడ్జెట్ 1500");
        assertThat(p.draft().sourceCity()).isEqualTo("Bengaluru");
        assertThat(p.draft().destinationCity()).isEqualTo("Hyderabad");
        assertThat(p.draft().travelDate()).isEqualTo(LocalDate.of(2026, 12, 20));
        assertThat(p.draft().maxBudget()).isEqualTo(1500);
        assertThat(p.language()).isEqualTo("te-IN");
    }

    @Test
    void parsesHindiScriptAndMode() {
        Parsed p = parser.parse("मुझे 22 दिसंबर को दिल्ली से जयपुर जाना है, बजट 800, बस");
        assertThat(p.draft().sourceCity()).isEqualTo("Delhi");
        assertThat(p.draft().destinationCity()).isEqualTo("Jaipur");
        assertThat(p.draft().travelDate()).isEqualTo(LocalDate.of(2026, 12, 22));
        assertThat(p.draft().maxBudget()).isEqualTo(800);
        assertThat(p.draft().preferredMode()).isEqualTo(TransportMode.BUS);
        assertThat(p.language()).isEqualTo("hi-IN");
    }

    @Test
    void parsesRomanisedHindiWithRelativeDate() {
        Parsed p = parser.parse("Delhi se Jaipur jana hai kal, 2 log, sasta");
        assertThat(p.draft().sourceCity()).isEqualTo("Delhi");
        assertThat(p.draft().destinationCity()).isEqualTo("Jaipur");
        assertThat(p.draft().travelDate()).isEqualTo(LocalDate.of(2026, 11, 26));
        assertThat(p.draft().travellers()).isEqualTo(2);
        assertThat(p.draft().priority()).isEqualTo(Priority.CHEAPEST);
    }

    @Test
    void parsesEnglishWithTimeAndTravellers() {
        Parsed p = parser.parse("2 people from Chennai to Hyderabad tomorrow by train, arrive by 8 am, cheapest");
        assertThat(p.draft().sourceCity()).isEqualTo("Chennai");
        assertThat(p.draft().destinationCity()).isEqualTo("Hyderabad");
        assertThat(p.draft().travellers()).isEqualTo(2);
        assertThat(p.draft().preferredMode()).isEqualTo(TransportMode.TRAIN);
        assertThat(p.draft().arriveBy()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    void yearlessDatesInThePastRollToNextYear() {
        Parsed p = parser.parse("Bangalore to Goa on 5th January under 3000 rupees");
        assertThat(p.draft().travelDate()).isEqualTo(LocalDate.of(2027, 1, 5));
        assertThat(p.draft().maxBudget()).isEqualTo(3000);
    }

    @Test
    void readsReturnDateAndSharing() {
        Parsed p = parser.parse("Hyderabad to Vizag on 2026-12-24 return 28/12 share cab");
        assertThat(p.draft().destinationCity()).isEqualTo("Visakhapatnam");
        assertThat(p.draft().travelDate()).isEqualTo(LocalDate.of(2026, 12, 24));
        assertThat(p.draft().returnDate()).isEqualTo(LocalDate.of(2026, 12, 28));
        assertThat(p.draft().preferredMode()).isEqualTo(TransportMode.SHARED_CAR);
        assertThat(p.draft().willingToShare()).isTrue();
    }

    @Test
    void aBudgetIsNeverMistakenForADateOrTravellerCount() {
        Parsed p = parser.parse("Pune to Mumbai 20 December budget 1500 for 3 people");
        assertThat(p.draft().travelDate()).isEqualTo(LocalDate.of(2026, 12, 20));
        assertThat(p.draft().maxBudget()).isEqualTo(1500);
        assertThat(p.draft().travellers()).isEqualTo(3);
    }

    @Test
    void impossibleDatesAreIgnoredRatherThanInvented() {
        Parsed p = parser.parse("Pune to Mumbai on 31 February");
        assertThat(p.draft().travelDate()).isNull();
    }

    @Test
    void doesNotInventAnythingFromSmallTalk() {
        Parsed p = parser.parse("hello");
        assertThat(p.draft().sourceCity()).isNull();
        assertThat(p.draft().destinationCity()).isNull();
        assertThat(p.draft().travelDate()).isNull();
        assertThat(p.intent()).isEqualTo("GREETING");
    }

    @Test
    void recognisesCompanionIntent() {
        Parsed p = parser.parse("Anyone going from Bangalore to Hyderabad on 20 December? find travel companions");
        assertThat(p.intent()).isEqualTo("FIND_COMPANIONS");
    }

    @Test
    void aSingleCityWithoutMarkersIsTheDestination() {
        Parsed p = parser.parse("Hyderabad 20 Dec");
        assertThat(p.draft().destinationCity()).isEqualTo("Hyderabad");
        assertThat(p.draft().sourceCity()).isNull();
    }
}

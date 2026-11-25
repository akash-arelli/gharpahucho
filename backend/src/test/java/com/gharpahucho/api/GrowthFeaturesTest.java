package com.gharpahucho.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Festival Pass checkout, ride alerts and the family link. The free plan is simulated by switching unlimited alerts off. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gharpahucho_growth;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "gharpahucho.flagsmith.defaults.unlimited_ride_alerts=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GrowthFeaturesTest {

    private static final LocalDate DAY = LocalDate.now().plusDays(21);

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    private JsonNode call(MockHttpServletRequestBuilder b, String token, int expected) throws Exception {
        MvcResult r = mvc.perform(token == null ? b : b.header("Authorization", "Bearer " + token)).andExpect(status().is(expected)).andReturn();
        String body = r.getResponse().getContentAsString();
        return body.isBlank() ? mapper.createObjectNode() : mapper.readTree(body);
    }

    private JsonNode postJson(String path, Object body, String token, int expected) throws Exception {
        return call(post(path).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)), token, expected);
    }

    private String register() throws Exception {
        String email = "g-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        return postJson("/api/auth/register", Map.of("email", email, "password", "password123", "displayName", "Asha Rao", "homeCity", "Pune"), null, 201)
                .path("token").asText();
    }

    private String trip(String token, String from, String to, Map<String, Object> extra) throws Exception {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>(Map.of("sourceCity", from, "destinationCity", to, "travelDate", DAY.toString()));
        body.putAll(extra);
        return postJson("/api/trips", body, token, 201).path("id").asText();
    }

    @Test
    void demoCheckoutUpgradesThePlanWithoutAnyPaymentDetails() throws Exception {
        String t = register();
        JsonNode plans = call(get("/api/billing/plans"), t, 200);
        assertThat(plans.path("currentPlan").asText()).isEqualTo("free");
        assertThat(plans.path("demoCheckoutAvailable").asBoolean()).isTrue();
        assertThat(plans.path("priceInr").asInt()).isEqualTo(299);
        assertThat(plans.path("perks").size()).isEqualTo(4);

        JsonNode me = call(post("/api/billing/demo-checkout"), t, 200);
        assertThat(me.path("plan").asText()).isEqualTo("plus");
        assertThat(call(get("/api/users/me"), t, 200).path("plan").asText()).isEqualTo("plus");
        assertThat(call(post("/api/billing/demo-cancel"), t, 200).path("plan").asText()).isEqualTo("free");
        call(post("/api/billing/demo-checkout"), null, 401);
    }

    @Test
    void freeUsersGetOneRideAlertThenAnUpgradeMessage() throws Exception {
        String t = register();
        String first = trip(t, "Pune", "Mumbai", Map.of());
        String second = trip(t, "Pune", "Hyderabad", Map.of());

        JsonNode a = postJson("/api/alerts", Map.of("tripId", first), t, 201);
        assertThat(postJson("/api/alerts", Map.of("tripId", first), t, 201).path("id").asText()).as("same route and date is not duplicated").isEqualTo(a.path("id").asText());

        JsonNode limit = postJson("/api/alerts", Map.of("tripId", second), t, 403);
        assertThat(limit.path("code").asText()).isEqualTo("PLAN_LIMIT");
        assertThat(limit.path("message").asText()).contains("Festival Pass");

        JsonNode list = call(get("/api/alerts"), t, 200);
        assertThat(list.path("limit").asInt()).isEqualTo(1);
        assertThat(list.path("unlimited").asBoolean()).isFalse();

        call(delete("/api/alerts/" + a.path("id").asText()), t, 204);
        postJson("/api/alerts", Map.of("tripId", second), t, 201);
        call(delete("/api/alerts/" + UUID.randomUUID()), t, 404);
    }

    @Test
    void anAlertFindsRidesPostedLaterAndNotifiesTheSubscriber() throws Exception {
        String rider = register();
        String alert = postJson("/api/alerts", Map.of("tripId", trip(rider, "Chennai", "Madurai", Map.of())), rider, 201).path("id").asText();
        assertThat(call(get("/api/alerts"), rider, 200).path("alerts").get(0).path("rides").size()).isZero();

        String driver = register();
        trip(driver, "Chennai", "Madurai", Map.of("seatsAvailable", 2, "costPerSeat", 450, "visibility", "DISCOVERABLE"));
        // A private trip or one on a different route must not match.
        trip(driver, "Chennai", "Coimbatore", Map.of("seatsAvailable", 2, "visibility", "DISCOVERABLE"));

        JsonNode alerts = call(get("/api/alerts"), rider, 200).path("alerts");
        assertThat(alerts.get(0).path("rides").size()).isEqualTo(1);
        JsonNode ride = alerts.get(0).path("rides").get(0);
        assertThat(ride.path("fresh").asBoolean()).isTrue();
        assertThat(ride.path("seats").asInt()).isEqualTo(2);
        assertThat(ride.toString()).doesNotContain("@example.com");
        assertThat(call(get("/api/dashboard"), rider, 200).toString()).contains("A ride just appeared");

        call(post("/api/alerts/" + alert + "/seen"), rider, 204);
        assertThat(call(get("/api/alerts"), rider, 200).path("alerts").get(0).path("newRides").asInt()).isZero();
        // The driver never gets an alert about their own ride.
        assertThat(call(get("/api/alerts"), driver, 200).path("alerts").size()).isZero();
    }

    @Test
    void familyLinkIsPublicReadOnlyLocalisedAndRevocable() throws Exception {
        String owner = register();
        String other = register();
        String tripId = trip(owner, "Bengaluru", "Hyderabad", Map.of());

        postJson("/api/trips/" + tripId + "/family-links", Map.of("language", "hi"), other, 404);
        JsonNode link = postJson("/api/trips/" + tripId + "/family-links", Map.of("language", "hi", "familyName", "Amma"), owner, 201);
        String token = link.path("token").asText();
        assertThat(token).hasSizeGreaterThanOrEqualTo(30);
        assertThat(link.path("path").asText()).isEqualTo("/f/" + token);

        MvcResult r = mvc.perform(get("/api/family/" + token)).andExpect(status().isOk()).andReturn();
        assertThat(r.getResponse().getHeader("Cache-Control")).contains("no-store");
        JsonNode view = mapper.readTree(r.getResponse().getContentAsString());
        assertThat(view.path("travellerName").asText()).isEqualTo("Asha");
        assertThat(view.path("headline").asText()).contains("Asha").contains("Hyderabad").contains("जा रहे हैं");
        assertThat(view.path("statusLabel").asText()).isEqualTo("यात्रा की योजना बनी है");
        assertThat(view.toString()).doesNotContain("@example.com").doesNotContain("Rao").doesNotContain("email").doesNotContain("phone\":");

        call(patch("/api/family-links/" + link.path("id").asText()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ON_THE_WAY\",\"message\":\"Left home at 6\"}"), other, 404);
        call(patch("/api/family-links/" + link.path("id").asText()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"TELEPORTED\"}"), owner, 400);
        call(patch("/api/family-links/" + link.path("id").asText()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ON_THE_WAY\",\"message\":\"Left home at 6\"}"), owner, 200);
        JsonNode after = call(get("/api/family/" + token), null, 200);
        assertThat(after.path("status").asText()).isEqualTo("ON_THE_WAY");
        assertThat(after.path("message").asText()).isEqualTo("Left home at 6");
        assertThat(after.path("statusLabel").asText()).isEqualTo("रास्ते में हैं");
        assertThat(call(get("/api/trips/" + tripId + "/family-links"), owner, 200).get(0).path("views").asInt()).isGreaterThanOrEqualTo(2);

        call(delete("/api/family-links/" + link.path("id").asText()), other, 404);
        call(delete("/api/family-links/" + link.path("id").asText()), owner, 204);
        call(get("/api/family/" + token), null, 404);
        call(get("/api/family/not-a-real-token"), null, 404);
        call(get("/api/family/" + "A".repeat(32)), null, 404);
    }
}

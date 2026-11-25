package com.gharpahucho.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end API behaviour on the H2 test database with seeded demo data and no third-party credentials. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    // ------------------------------------------------------------------ helpers

    private JsonNode json(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token);
    }

    private JsonNode call(MockHttpServletRequestBuilder b, String token, int expected) throws Exception {
        MvcResult r = mvc.perform(token == null ? b : auth(b, token)).andExpect(status().is(expected)).andReturn();
        String body = r.getResponse().getContentAsString();
        return body.isBlank() ? mapper.createObjectNode() : mapper.readTree(body);
    }

    private JsonNode postJson(String path, Object body, String token, int expected) throws Exception {
        return call(post(path).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)), token, expected);
    }

    private String demoToken() throws Exception {
        return call(post("/api/auth/demo-login"), null, 200).path("token").asText();
    }

    private String register(String email) throws Exception {
        return postJson("/api/auth/register", java.util.Map.of("email", email, "password", "password123", "displayName", "Test User", "homeCity", "Pune"), null, 201)
                .path("token").asText();
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static final LocalDate TRAVEL_DAY = LocalDate.now().plusDays(21);

    // ------------------------------------------------------------------ public surface and security

    @Test
    @Order(1)
    void healthIsPublicAndReportsComponents() throws Exception {
        JsonNode j = call(get("/api/health"), null, 200);
        assertThat(j.path("status").asText()).isEqualTo("UP");
        assertThat(j.path("components").path("database").asText()).isEqualTo("UP");
        assertThat(j.path("components").path("Sarvam AI").asText()).isEqualTo("NOT_CONFIGURED");
        assertThat(j.path("components").path("Flagsmith").asText()).isEqualTo("NOT_CONFIGURED");
    }

    @Test
    @Order(2)
    void protectedEndpointsRejectAnonymousCallersWithJson401() throws Exception {
        JsonNode j = call(get("/api/dashboard"), null, 401);
        assertThat(j.path("code").asText()).isEqualTo("UNAUTHENTICATED");
        call(get("/api/trips"), "not-a-real-token", 401);
        call(get("/api/ops/status"), null, 401);
    }

    @Test
    @Order(3)
    void publicFeatureEndpointNeverContainsServerSecretsOrIdentity() throws Exception {
        JsonNode j = call(get("/api/features/public"), null, 200);
        assertThat(j.path("flags").size()).isEqualTo(15);
        assertThat(j.path("flagsmith").path("configured").asBoolean()).isFalse();
        assertThat(j.toString()).doesNotContain("ser.").doesNotContain("serverKey");
    }

    // ------------------------------------------------------------------ auth and validation

    @Test
    @Order(10)
    void registrationValidatesInput() throws Exception {
        JsonNode j = postJson("/api/auth/register", java.util.Map.of("email", "not-an-email", "password", "short", "displayName", ""), null, 400);
        assertThat(j.path("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(j.path("details").path("fields").has("email")).isTrue();
        assertThat(j.path("details").path("fields").has("password")).isTrue();
        assertThat(j.path("requestId").asText()).isNotBlank();
    }

    @Test
    @Order(11)
    void duplicateEmailIsRejectedAndWrongPasswordIs401() throws Exception {
        String email = "dup-" + unique() + "@example.com";
        register(email);
        postJson("/api/auth/register", java.util.Map.of("email", email, "password", "password123", "displayName", "X"), null, 409);
        postJson("/api/auth/login", java.util.Map.of("email", email, "password", "wrong-password"), null, 401);
        JsonNode ok = postJson("/api/auth/login", java.util.Map.of("email", email, "password", "password123"), null, 200);
        assertThat(ok.path("user").path("email").asText()).isEqualTo(email);
        assertThat(ok.toString()).doesNotContain("passwordHash");
    }

    // ------------------------------------------------------------------ planner

    @Test
    @Order(20)
    void plannerLabelsTheSourceOfEveryOptionAndNeverClaimsLiveData() throws Exception {
        String token = demoToken();
        JsonNode plan = postJson("/api/trips/plan", java.util.Map.of("sourceCity", "Bengaluru", "destinationCity", "Hyderabad",
                "travelDate", TRAVEL_DAY.toString(), "travellers", 1, "maxBudget", 1500, "priority", "CHEAPEST"), token, 200);
        assertThat(plan.path("options").size()).isGreaterThan(2);
        assertThat(plan.path("disclaimer").asText()).isNotBlank();
        for (JsonNode o : plan.path("options")) {
            assertThat(o.path("dataSource").asText()).isIn("ROUTE_CALCULATION", "USER_TRIP", "SIMULATED_INVENTORY");
            assertThat(o.path("dataSourceLabel").asText()).isNotBlank();
            assertThat(o.path("availability").asText()).isNotEqualToIgnoringCase("LIVE");
            if (o.path("dataSource").asText().equals("SIMULATED_INVENTORY")) {
                assertThat(o.path("availabilityLabel").asText().toLowerCase()).contains("sample");
            }
            if (!o.path("meetsConstraints").asBoolean()) {
                assertThat(o.path("tags").toString()).contains("ALTERNATIVE");
                assertThat(o.path("violations").size()).isGreaterThan(0);
            }
        }
        // Anything marked as within budget really is within budget.
        for (JsonNode o : plan.path("options")) {
            if (o.path("tags").toString().contains("WITHIN_BUDGET")) {
                assertThat(o.path("costPerPerson").asInt()).isLessThanOrEqualTo(1500);
            }
        }
    }

    @Test
    @Order(21)
    void plannerRejectsPastDatesSameCityAndUnknownCities() throws Exception {
        String token = demoToken();
        postJson("/api/trips/plan", java.util.Map.of("sourceCity", "Bengaluru", "destinationCity", "Hyderabad", "travelDate", LocalDate.now().minusDays(2).toString()), token, 422);
        postJson("/api/trips/plan", java.util.Map.of("sourceCity", "Pune", "destinationCity", "Pune", "travelDate", TRAVEL_DAY.toString()), token, 422);
        postJson("/api/trips/plan", java.util.Map.of("sourceCity", "Atlantis", "destinationCity", "Hyderabad", "travelDate", TRAVEL_DAY.toString()), token, 422);
        postJson("/api/trips/plan", java.util.Map.of("sourceCity", "Pune", "destinationCity", "Mumbai"), token, 400);
    }

    // ------------------------------------------------------------------ ownership

    @Test
    @Order(30)
    void aTravellerCannotReadOrChangeAnotherTravellersTrip() throws Exception {
        String owner = register("owner-" + unique() + "@example.com");
        String other = register("other-" + unique() + "@example.com");
        JsonNode created = postJson("/api/trips", java.util.Map.of("sourceCity", "Pune", "destinationCity", "Mumbai",
                "travelDate", TRAVEL_DAY.toString(), "maxBudget", 700), owner, 201);
        String id = created.path("id").asText();
        assertThat(created.path("visibility").asText()).as("trips are private by default").isEqualTo("PRIVATE");

        call(get("/api/trips/" + id), owner, 200);
        call(get("/api/trips/" + id), other, 404);
        call(patch("/api/trips/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"DISCOVERABLE\"}"), other, 404);
        call(get("/api/trips/" + UUID.randomUUID()), owner, 404);
        // The booking plan is scoped to the owner too, and the owner's plan has the booking window and steps.
        call(get("/api/trips/" + id + "/booking-plan"), other, 404);
        JsonNode plan = call(get("/api/trips/" + id + "/booking-plan"), owner, 200);
        assertThat(plan.path("railWindow").path("status").asText()).isIn("OPEN", "UPCOMING");
        assertThat(plan.path("steps").size()).isEqualTo(4);
        assertThat(call(get("/api/trips/booking-radar"), owner, 200).path("items").size()).isGreaterThanOrEqualTo(1);
        // The owner can publish it.
        JsonNode published = call(patch("/api/trips/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"DISCOVERABLE\"}"), owner, 200);
        assertThat(published.path("visibility").asText()).isEqualTo("DISCOVERABLE");
    }

    // ------------------------------------------------------------------ matching and privacy

    @Test
    @Order(40)
    void matchesAreScoredSortedExplainedAndPrivacySafe() throws Exception {
        String token = demoToken();
        JsonNode dash = call(get("/api/dashboard"), token, 200);
        assertThat(dash.path("greetingName").asText()).isEqualTo("Priya");
        String tripId = dash.path("upcomingJourneys").get(0).path("id").asText();

        JsonNode res = call(get("/api/travellers/matches").param("tripId", tripId), token, 200);
        assertThat(res.path("matches").size()).isGreaterThan(2);
        int previous = 101;
        for (JsonNode m : res.path("matches")) {
            int score = m.path("score").asInt();
            assertThat(score).isBetween(0, 100);
            assertThat(score).isLessThanOrEqualTo(previous);
            previous = score;
            assertThat(m.path("reasons").size()).isGreaterThan(0);
            assertThat(m.path("breakdown").size()).isEqualTo(5);
            assertThat(m.path("userId").asText()).isNotEqualTo(dash.path("upcomingJourneys").get(0).path("userId").asText());
        }
        String raw = res.toString();
        assertThat(raw).as("no contact details in discovery").doesNotContain("@").doesNotContain("passwordHash").doesNotContain("phone");
        assertThat(res.path("weights").path("destination").asInt()).isEqualTo(35);
    }

    @Test
    @Order(41)
    void privateTripsAndUndiscoverableTravellersNeverAppearInDiscovery() throws Exception {
        String token = demoToken();
        String hidden = register("hidden-" + unique() + "@example.com");
        // A matching journey that stays PRIVATE must not be returned to others.
        postJson("/api/trips", java.util.Map.of("sourceCity", "Bengaluru", "destinationCity", "Hyderabad", "travelDate", TRAVEL_DAY.toString()), hidden, 201);
        String tripId = call(get("/api/dashboard"), token, 200).path("upcomingJourneys").get(0).path("id").asText();
        JsonNode res = call(get("/api/travellers/matches").param("tripId", tripId).param("size", "50"), token, 200);
        for (JsonNode m : res.path("matches")) {
            assertThat(m.path("name").asText()).isNotEqualTo("Test");
        }
        assertThat(res.path("total").asInt()).isGreaterThan(0);
    }

    // ------------------------------------------------------------------ interest -> accept -> group

    @Test
    @Order(50)
    void acceptingAConnectionRequestCreatesAGroupBothMembersCanUse() throws Exception {
        String priya = demoToken();
        String guest = register("guest-" + unique() + "@example.com");

        JsonNode priyaTrips = call(get("/api/trips"), priya, 200).path("content");
        String priyaTripId = priyaTrips.get(0).path("id").asText();
        JsonNode guestTrip = postJson("/api/trips", java.util.Map.of("sourceCity", "Bengaluru", "destinationCity", "Hyderabad",
                "travelDate", TRAVEL_DAY.toString(), "visibility", "DISCOVERABLE", "willingToShare", true), guest, 201);

        JsonNode interest = postJson("/api/travellers/interests", java.util.Map.of("fromTripId", guestTrip.path("id").asText(), "toTripId", priyaTripId,
                "message", "Can we share a cab?"), guest, 201);
        assertThat(interest.path("status").asText()).isEqualTo("PENDING");
        // Duplicate request is refused.
        postJson("/api/travellers/interests", java.util.Map.of("fromTripId", guestTrip.path("id").asText(), "toTripId", priyaTripId), guest, 409);
        // The sender cannot accept their own request; a stranger cannot either.
        String interestId = interest.path("id").asText();
        postJson("/api/travellers/interests/" + interestId + "/respond", java.util.Map.of("accept", true), guest, 404);

        JsonNode accepted = postJson("/api/travellers/interests/" + interestId + "/respond", java.util.Map.of("accept", true), priya, 200);
        assertThat(accepted.path("status").asText()).isEqualTo("ACCEPTED");
        String groupId = accepted.path("groupId").asText();
        assertThat(groupId).isNotBlank();
        // Cannot be answered twice.
        postJson("/api/travellers/interests/" + interestId + "/respond", java.util.Map.of("accept", false), priya, 409);

        JsonNode group = call(get("/api/groups/" + groupId), guest, 200);
        assertThat(group.path("members").size()).isEqualTo(2);
        assertThat(group.path("costSharing").path("dataSource").asText()).isIn("ROUTE_CALCULATION", "SIMULATED_INVENTORY");
        postJson("/api/groups/" + groupId + "/messages", java.util.Map.of("body", "See you at Silk Board!"), guest, 201);
        JsonNode messages = call(get("/api/groups/" + groupId + "/messages"), priya, 200);
        assertThat(messages.toString()).contains("See you at Silk Board!");

        // A traveller who is not in the group gets nothing, not even confirmation that it exists.
        String stranger = register("stranger-" + unique() + "@example.com");
        call(get("/api/groups/" + groupId), stranger, 404);
        call(get("/api/groups/" + groupId + "/messages"), stranger, 404);
        postJson("/api/groups/" + groupId + "/messages", java.util.Map.of("body", "hi"), stranger, 404);
    }

    @Test
    @Order(51)
    void aGroupInvitationCanBeAnsweredAndChatNeedsMembership() throws Exception {
        String priya = demoToken();
        JsonNode invites = call(get("/api/groups/invitations"), priya, 200);
        assertThat(invites.size()).isGreaterThan(0);
        String groupId = invites.get(0).path("id").asText();
        // Invited but not yet joined: may see the summary, may not read the chat.
        call(get("/api/groups/" + groupId + "/messages"), priya, 403);
        JsonNode joined = postJson("/api/groups/" + groupId + "/respond", java.util.Map.of("accept", true), priya, 200);
        assertThat(joined.path("myStatus").asText()).isEqualTo("JOINED");
        assertThat(call(get("/api/groups/" + groupId + "/messages"), priya, 200).size()).isGreaterThan(0);
    }

    // ------------------------------------------------------------------ assistant and AI honesty

    @Test
    @Order(60)
    void assistantUnderstandsTheTeluguExampleWithoutClaimingAI() throws Exception {
        String token = demoToken();
        JsonNode parsed = postJson("/api/assistant/parse", java.util.Map.of("text", "Naaku December 20th Bangalore nunchi Hyderabad vellali. Budget 1500."), token, 200);
        assertThat(parsed.path("draft").path("sourceCity").asText()).isEqualTo("Bengaluru");
        assertThat(parsed.path("draft").path("destinationCity").asText()).isEqualTo("Hyderabad");
        assertThat(parsed.path("draft").path("maxBudget").asInt()).isEqualTo(1500);
        assertThat(parsed.path("detectedLanguage").asText()).isEqualTo("te-IN");
        assertThat(parsed.path("extractionSource").asText()).as("no key, so it must not claim AI").isEqualTo("RULE_BASED_FALLBACK");
    }

    @Test
    @Order(61)
    void assistantAnswersAreGroundedInRealPlannerResults() throws Exception {
        String token = demoToken();
        JsonNode reply = postJson("/api/assistant/chat", java.util.Map.of("message", "Bangalore to Hyderabad on " + TRAVEL_DAY + " budget 1500"), token, 200);
        assertThat(reply.path("intent").asText()).isEqualTo("PLAN_TRIP");
        assertThat(reply.path("replySource").asText()).isEqualTo("TEMPLATE");
        assertThat(reply.path("aiAvailable").asBoolean()).isFalse();
        assertThat(reply.path("options").size()).isGreaterThan(0);
        // Every price quoted in the reply text comes from an option the planner actually returned.
        List<String> prices = new ArrayList<>();
        reply.path("options").forEach(o -> prices.add(String.valueOf(o.path("costPerPerson").asInt())));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("₹(\\d+)").matcher(reply.path("reply").asText());
        int found = 0;
        while (m.find()) {
            found++;
            assertThat(prices).contains(m.group(1));
        }
        assertThat(found).isGreaterThan(0);
        assertThat(reply.path("reply").asText()).containsIgnoringCase("sample");
    }

    @Test
    @Order(62)
    void assistantAsksForWhatIsMissingInsteadOfGuessing() throws Exception {
        String token = demoToken();
        JsonNode reply = postJson("/api/assistant/chat", java.util.Map.of("message", "I want to go to Goa"), token, 200);
        assertThat(reply.path("options").size()).isZero();
        assertThat(reply.path("draft").path("travelDate").isMissingNode()).isTrue();
        assertThat(reply.path("reply").asText()).containsIgnoringCase("date");
    }

    @Test
    @Order(63)
    void voiceWithoutASarvamKeyFailsHonestlyAndNeverReturnsAFakeTranscript() throws Exception {
        String token = demoToken();
        MockMultipartFile audio = new MockMultipartFile("audio", "voice.webm", "audio/webm", new byte[]{1, 2, 3, 4});
        MvcResult r = mvc.perform(auth(multipart("/api/assistant/voice").file(audio), token)).andExpect(status().isServiceUnavailable()).andReturn();
        JsonNode j = json(r);
        assertThat(j.path("code").asText()).isEqualTo("SPEECH_UNAVAILABLE");
        assertThat(j.has("transcript")).isFalse();
    }

    @Test
    @Order(64)
    void aiExplanationsReportUnavailableInsteadOfInventingText() throws Exception {
        String token = demoToken();
        JsonNode plan = postJson("/api/trips/plan", java.util.Map.of("sourceCity", "Bengaluru", "destinationCity", "Hyderabad", "travelDate", TRAVEL_DAY.toString()), token, 200);
        String optionId = plan.path("options").get(0).path("id").asText();
        JsonNode ex = postJson("/api/trips/plan/explain", java.util.Map.of("plan", java.util.Map.of("sourceCity", "Bengaluru", "destinationCity", "Hyderabad",
                "travelDate", TRAVEL_DAY.toString()), "optionId", optionId, "language", "te"), token, 200);
        assertThat(ex.path("available").asBoolean()).isFalse();
        assertThat(ex.path("source").asText()).isEqualTo("UNAVAILABLE");
        assertThat(ex.path("explanation").isMissingNode() || ex.path("explanation").isNull()).isTrue();
    }

    // ------------------------------------------------------------------ ops and demo

    @Test
    @Order(70)
    void opsStatusShowsTheTruthAboutEveryIntegration() throws Exception {
        String token = demoToken();
        JsonNode ops = call(get("/api/ops/status"), token, 200);
        assertThat(ops.path("integrations").toString()).contains("Flagsmith").contains("Sarvam AI").contains("NudgeBee");
        assertThat(ops.path("nudgebee").path("webhookConfigured").asBoolean()).isFalse();
        JsonNode test = postJson("/api/ops/nudgebee/test", java.util.Map.of(), token, 200);
        assertThat(test.path("sent").asBoolean()).isFalse();
        assertThat(test.path("message").asText()).containsIgnoringCase("not configured");
    }

    @Test
    @Order(99)
    void demoResetRebuildsTheCastAndDemoLoginStillWorks() throws Exception {
        String token = demoToken();
        call(post("/api/demo/reset"), token, 200);
        String fresh = demoToken();
        JsonNode dash = call(get("/api/dashboard"), fresh, 200);
        assertThat(dash.path("upcomingJourneys").size()).isGreaterThan(0);
        assertThat(dash.path("groupInvitations").size()).isGreaterThan(0);
        // The old token pointed at a deleted user and must stop working rather than act on someone else.
        call(get("/api/users/me"), token, 404);
    }

    @Test
    @Order(100)
    void unknownRoutesReturnTheStandardErrorEnvelope() throws Exception {
        JsonNode j = call(get("/api/does-not-exist"), demoToken(), 404);
        assertThat(j.path("code").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    @Order(101)
    void malformedJsonIsA400NotA500() throws Exception {
        String token = demoToken();
        JsonNode j = call(post("/api/trips").contentType(MediaType.APPLICATION_JSON).content("{not json"), token, 400);
        assertThat(j.path("code").asText()).isEqualTo("MALFORMED_REQUEST");
    }
}

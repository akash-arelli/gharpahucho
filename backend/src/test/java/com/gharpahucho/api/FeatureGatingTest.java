package com.gharpahucho.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the backend, not just the UI, honours switched-off Flagsmith features. Flagsmith is not configured in tests,
 * so the documented fallback defaults decide; here they switch several capabilities off.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gharpahucho_gating;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "gharpahucho.flagsmith.defaults.smart_trip_planner=false",
        "gharpahucho.flagsmith.defaults.travel_companion_matching=false",
        "gharpahucho.flagsmith.defaults.ai_voice_trip_creation=false",
        "gharpahucho.flagsmith.defaults.sarvam_multilingual_assistant=false",
        "gharpahucho.flagsmith.defaults.travel_group_creation=false",
        "gharpahucho.flagsmith.defaults.ai_trip_explanations=false",
        "gharpahucho.flagsmith.defaults.budget_optimization=false",
        "gharpahucho.flagsmith.defaults.alternative_route_recommendations=false",
        "gharpahucho.flagsmith.defaults.family_link=false",
        "gharpahucho.flagsmith.defaults.ride_alerts=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeatureGatingTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/demo-login")).andExpect(status().isOk()).andReturn();
        return mapper.readTree(r.getResponse().getContentAsString()).path("token").asText();
    }

    private JsonNode expectDisabled(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, String feature) throws Exception {
        MvcResult r = mvc.perform(b).andExpect(status().isForbidden()).andReturn();
        JsonNode j = mapper.readTree(r.getResponse().getContentAsString());
        assertThat(j.path("code").asText()).isEqualTo("FEATURE_DISABLED");
        assertThat(j.path("details").path("feature").asText()).isEqualTo(feature);
        return j;
    }

    @Test
    void familyLinksAndRideAlertsAreRefusedByTheBackendWhenSwitchedOff() throws Exception {
        String t = token();
        expectDisabled(post("/api/trips/" + UUID.randomUUID() + "/family-links").header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON).content("{}"), "family_link");
        expectDisabled(get("/api/alerts").header("Authorization", "Bearer " + t), "ride_alerts");
        // The public family page is a read of an existing link, so it does not need a login (unknown token: plain 404).
        mvc.perform(get("/api/family/" + "B".repeat(32))).andExpect(status().isNotFound());
    }

    @Test
    void theFeatureEndpointReportsTheFlagsAsOffWithTheirSource() throws Exception {
        MvcResult r = mvc.perform(get("/api/features").header("Authorization", "Bearer " + token())).andExpect(status().isOk()).andReturn();
        JsonNode j = mapper.readTree(r.getResponse().getContentAsString());
        assertThat(j.path("flags").path("smart_trip_planner").asBoolean()).isFalse();
        assertThat(j.path("flags").path("travel_companion_matching").asBoolean()).isFalse();
        assertThat(j.path("flags").path("shared_ride_matching").asBoolean()).isTrue();
        j.path("states").forEach(s -> assertThat(s.path("source").asText()).isEqualTo("FALLBACK_DEFAULT"));
        assertThat(j.path("identity").asText()).isNotBlank();
        assertThat(j.path("traits").path("city").asText()).isEqualTo("Bengaluru");
    }

    @Test
    void plannerIsRefusedByTheBackendWhenSwitchedOff() throws Exception {
        String t = token();
        expectDisabled(post("/api/trips/plan").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceCity\":\"Pune\",\"destinationCity\":\"Mumbai\",\"travelDate\":\"" + LocalDate.now().plusDays(10) + "\"}"), "smart_trip_planner");
    }

    @Test
    void matchingIsRefusedWhenSwitchedOff() throws Exception {
        String t = token();
        expectDisabled(get("/api/travellers/matches").param("tripId", "00000000-0000-0000-0000-000000000000").header("Authorization", "Bearer " + t), "travel_companion_matching");
        expectDisabled(get("/api/travellers/interests").header("Authorization", "Bearer " + t), "travel_companion_matching");
    }

    @Test
    void assistantChatVoiceAndExplanationsAreRefusedWhenSwitchedOff() throws Exception {
        String t = token();
        expectDisabled(post("/api/assistant/chat").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hello\"}"), "sarvam_multilingual_assistant");
        MockMultipartFile audio = new MockMultipartFile("audio", "v.webm", "audio/webm", new byte[]{1});
        expectDisabled(multipart("/api/assistant/voice").file(audio).header("Authorization", "Bearer " + t), "ai_voice_trip_creation");
        expectDisabled(post("/api/trips/plan/explain").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":{\"sourceCity\":\"Pune\",\"destinationCity\":\"Mumbai\",\"travelDate\":\"" + LocalDate.now().plusDays(10)
                        + "\"},\"optionId\":\"x\"}"), "ai_trip_explanations");
    }

    @Test
    void groupCreationIsRefusedButViewingGroupsStillWorks() throws Exception {
        String t = token();
        expectDisabled(post("/api/groups").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"G\",\"sourceCity\":\"Pune\",\"destinationCity\":\"Mumbai\",\"travelDate\":\"" + LocalDate.now().plusDays(10) + "\"}"), "travel_group_creation");
        mvc.perform(get("/api/groups").header("Authorization", "Bearer " + t)).andExpect(status().isOk());
    }

    @Test
    void naturalLanguageParsingStillWorksWithTheAssistantOffUsingBuiltInRules() throws Exception {
        String t = token();
        MvcResult r = mvc.perform(post("/api/assistant/parse").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Pune to Mumbai tomorrow\"}")).andExpect(status().isOk()).andReturn();
        JsonNode j = mapper.readTree(r.getResponse().getContentAsString());
        assertThat(j.path("extractionSource").asText()).isEqualTo("RULE_BASED_FALLBACK");
        assertThat(j.path("draft").path("destinationCity").asText()).isEqualTo("Mumbai");
    }
}

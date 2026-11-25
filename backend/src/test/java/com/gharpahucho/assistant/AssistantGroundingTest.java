package com.gharpahucho.assistant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The guard that stops an AI rewrite from altering the facts of a grounded reply. */
class AssistantGroundingTest {

    @Test
    void acceptsARewriteThatKeepsEveryNumber() {
        assertThat(AssistantService.preservesNumbers("Cheapest: Train, ₹555, departs 20:45.", "ఇదే చౌకైనది ₹555, 20:45 కి బయలుదేరుతుంది")).isTrue();
    }

    @Test
    void rejectsARewriteThatChangesAPrice() {
        assertThat(AssistantService.preservesNumbers("Cheapest: Train, ₹555, departs 20:45.", "Cheapest is ₹455, departs 20:45")).isFalse();
    }

    @Test
    void rejectsARewriteThatDropsANumber() {
        assertThat(AssistantService.preservesNumbers("Found 6 options, from ₹555", "Found some options from ₹555")).isFalse();
    }

    @Test
    void rejectsAnInventedDataSourceButAllowsKnownCityNames() {
        String template = "Delhi to Patna, 5 options.\nNote: train and bus fares are sample data.";
        assertThat(AssistantService.addsNoNewNames(template, "Delhi से Patna के लिए 5 विकल्प। ये sample data है।")).isTrue();
        assertThat(AssistantService.addsNoNewNames(template, "Delhi से Patna के लिए 5 विकल्प।\nData source: रेल मंत्रालय (Ministry of Railways)")).isFalse();
    }

    @Test
    void templatesExistInEveryLanguageWeAdvertise() {
        for (String lang : new String[]{"en", "hi", "te"}) {
            assertThat(Replies.greeting(lang)).isNotBlank();
            assertThat(Replies.askDate(lang)).isNotBlank();
            assertThat(Replies.sampleDataNote(lang)).isNotBlank();
        }
        assertThat(Replies.base("te-IN")).isEqualTo("te");
        assertThat(Replies.base("ta-IN")).isEqualTo("en");
    }
}

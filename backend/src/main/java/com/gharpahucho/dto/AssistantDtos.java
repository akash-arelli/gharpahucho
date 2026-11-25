package com.gharpahucho.dto;

import com.gharpahucho.entity.Priority;
import com.gharpahucho.entity.TransportMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public final class AssistantDtos {
    private AssistantDtos() {
    }

    /** Structured travel intent. Every field is optional because users reveal it gradually. */
    public record TripDraft(
            String sourceCity,
            String destinationCity,
            LocalDate travelDate,
            LocalDate returnDate,
            Integer travellers,
            Integer maxBudget,
            TransportMode preferredMode,
            Priority priority,
            LocalTime arriveBy,
            Boolean willingToShare) {

        public static TripDraft empty() {
            return new TripDraft(null, null, null, null, null, null, null, null, null, null);
        }
    }

    public record ParseRequest(@NotBlank @Size(max = 2000) String text, String languageHint) {
    }

    public record ParseResponse(
            TripDraft draft,
            /** SARVAM_AI or RULE_BASED_FALLBACK. Never claims AI when it was not used. */
            String extractionSource,
            String detectedLanguage,
            List<String> missing,
            List<String> notices) {
    }

    public record AssistantRequest(
            @NotBlank @Size(max = 2000) String message,
            String languageHint,
            @Valid TripDraft context) {
    }

    public record AssistantAction(String type, String label, Map<String, Object> payload) {
    }

    public record AssistantResponse(
            String reply,
            String replyLanguage,
            String intent,
            TripDraft draft,
            String extractionSource,
            String replySource,
            boolean aiAvailable,
            List<String> notices,
            List<PlanDtos.OptionDto> options,
            List<MatchDtos.MatchCandidate> matches,
            List<AssistantAction> actions) {
    }

    public record VoiceResponse(
            String transcript,
            String transcriptLanguage,
            String transcriptionSource,
            AssistantResponse assistant) {
    }
}

package com.gharpahucho.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gharpahucho.assistant.AssistantService;
import com.gharpahucho.dto.AssistantDtos.*;
import com.gharpahucho.exception.ApiException;
import com.gharpahucho.integration.flagsmith.FeatureKey;
import com.gharpahucho.integration.flagsmith.RequiresFeature;
import com.gharpahucho.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistant;
    private final ObjectMapper mapper;

    /**
     * Natural-language trip creation. Always available; uses Sarvam AI when the flag sarvam_multilingual_assistant
     * is on and a key exists, otherwise the deterministic parser, and says which one answered.
     */
    @PostMapping("/parse")
    public ParseResponse parse(@Valid @RequestBody ParseRequest request) {
        return assistant.parse(CurrentUser.id(), request);
    }

    @PostMapping("/chat")
    @RequiresFeature(FeatureKey.SARVAM_MULTILINGUAL_ASSISTANT)
    public AssistantResponse chat(@Valid @RequestBody AssistantRequest request) {
        return assistant.chat(CurrentUser.id(), request);
    }

    /** Voice message (multipart: audio file, optional languageHint and JSON context). Flag: ai_voice_trip_creation. */
    @PostMapping(value = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresFeature(FeatureKey.AI_VOICE_TRIP_CREATION)
    public VoiceResponse voice(@RequestPart("audio") MultipartFile audio,
                               @RequestParam(required = false) String languageHint,
                               @RequestParam(required = false) String context) {
        TripDraft draft = null;
        if (context != null && !context.isBlank()) {
            try {
                draft = mapper.readValue(context, TripDraft.class);
            } catch (Exception e) {
                throw ApiException.badRequest("The context is not valid JSON");
            }
        }
        return assistant.voice(CurrentUser.id(), audio, languageHint, draft);
    }
}

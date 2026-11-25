package com.gharpahucho.controller;

import com.gharpahucho.dto.MatchDtos.*;
import com.gharpahucho.integration.flagsmith.FeatureKey;
import com.gharpahucho.integration.flagsmith.RequiresFeature;
import com.gharpahucho.security.CurrentUser;
import com.gharpahucho.service.TravellerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Travel-companion discovery. The whole controller sits behind the Flagsmith flag travel_companion_matching. */
@RestController
@RequestMapping("/api/travellers")
@RequiredArgsConstructor
@RequiresFeature(FeatureKey.TRAVEL_COMPANION_MATCHING)
public class TravellerController {

    private final TravellerService travellers;

    @GetMapping("/matches")
    public MatchResponse matches(@RequestParam UUID tripId, @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "12") int size) {
        return travellers.matches(CurrentUser.id(), tripId, page, Math.min(50, size));
    }

    @PostMapping("/interests")
    @ResponseStatus(HttpStatus.CREATED)
    public InterestDto express(@Valid @RequestBody InterestRequest request) {
        return travellers.express(CurrentUser.id(), request);
    }

    @GetMapping("/interests")
    public InterestInbox inbox() {
        return travellers.inbox(CurrentUser.id());
    }

    @PostMapping("/interests/{id}/respond")
    public InterestDto respond(@PathVariable UUID id, @RequestBody RespondRequest request) {
        return travellers.respond(CurrentUser.id(), id, request.accept());
    }

    @PostMapping("/interests/{id}/withdraw")
    public InterestDto withdraw(@PathVariable UUID id) {
        return travellers.withdraw(CurrentUser.id(), id);
    }
}

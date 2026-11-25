package com.gharpahucho.controller;

import com.gharpahucho.dto.GrowthDtos.*;
import com.gharpahucho.dto.UserDtos.Me;
import com.gharpahucho.integration.flagsmith.FeatureKey;
import com.gharpahucho.integration.flagsmith.RequiresFeature;
import com.gharpahucho.security.CurrentUser;
import com.gharpahucho.service.BillingService;
import com.gharpahucho.service.FamilyLinkService;
import com.gharpahucho.service.RideAlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Festival Pass, ride alerts and family links. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GrowthController {

    private final BillingService billing;
    private final RideAlertService alerts;
    private final FamilyLinkService family;

    // ------------------------------------------------------------------ Festival Pass (demo checkout)

    @GetMapping("/billing/plans")
    public PlansResponse plans() {
        return billing.plans(CurrentUser.id());
    }

    @PostMapping("/billing/demo-checkout")
    public Me demoCheckout() {
        return billing.demoCheckout(CurrentUser.id());
    }

    @PostMapping("/billing/demo-cancel")
    public Me demoCancel() {
        return billing.demoCancel(CurrentUser.id());
    }

    // ------------------------------------------------------------------ ride alerts (flag: ride_alerts)

    @GetMapping("/alerts")
    @RequiresFeature(FeatureKey.RIDE_ALERTS)
    public AlertsResponse alerts() {
        return alerts.list(CurrentUser.id());
    }

    @PostMapping("/alerts")
    @RequiresFeature(FeatureKey.RIDE_ALERTS)
    @ResponseStatus(HttpStatus.CREATED)
    public AlertDto createAlert(@Valid @RequestBody CreateAlertRequest request) {
        return alerts.create(CurrentUser.id(), request.tripId(), request.flexDays());
    }

    @PostMapping("/alerts/{id}/seen")
    @RequiresFeature(FeatureKey.RIDE_ALERTS)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void seen(@PathVariable UUID id) {
        alerts.markSeen(CurrentUser.id(), id);
    }

    @DeleteMapping("/alerts/{id}")
    @RequiresFeature(FeatureKey.RIDE_ALERTS)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAlert(@PathVariable UUID id) {
        alerts.delete(CurrentUser.id(), id);
    }

    // ------------------------------------------------------------------ family link (flag: family_link, Plus)

    @GetMapping("/trips/{tripId}/family-links")
    @RequiresFeature(FeatureKey.FAMILY_LINK)
    public List<FamilyLinkDto> familyLinks(@PathVariable UUID tripId) {
        return family.list(CurrentUser.id(), tripId);
    }

    @PostMapping("/trips/{tripId}/family-links")
    @RequiresFeature(FeatureKey.FAMILY_LINK)
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyLinkDto createFamilyLink(@PathVariable UUID tripId, @Valid @RequestBody(required = false) CreateFamilyLinkRequest request) {
        return family.create(CurrentUser.id(), tripId, request);
    }

    @PatchMapping("/family-links/{id}")
    @RequiresFeature(FeatureKey.FAMILY_LINK)
    public FamilyLinkDto setStatus(@PathVariable UUID id, @Valid @RequestBody FamilyStatusRequest request) {
        return family.updateStatus(CurrentUser.id(), id, request.status(), request.message());
    }

    @DeleteMapping("/family-links/{id}")
    @RequiresFeature(FeatureKey.FAMILY_LINK)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id) {
        family.revoke(CurrentUser.id(), id);
    }

    /** Public, unauthenticated, read-only. The token is the only credential. */
    @GetMapping("/family/{token}")
    public ResponseEntity<FamilyView> publicView(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Robots-Tag", "noindex, nofollow").body(family.view(token));
    }
}

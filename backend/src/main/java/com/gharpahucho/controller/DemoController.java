package com.gharpahucho.controller;

import com.gharpahucho.config.GharPahuchoProperties;
import com.gharpahucho.exception.ApiException;
import com.gharpahucho.service.DemoDataService;
import com.gharpahucho.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final GharPahuchoProperties props;
    private final DemoDataService demo;

    /** Public: tells the login page whether demo mode is on (never returns passwords). */
    @GetMapping("/info")
    public Map<String, Object> info() {
        boolean on = props.getDemo().isEnabled();
        return Map.of(
                "demoMode", on,
                "resetEnabled", on && props.getDemo().isResetEndpointEnabled(),
                "demoLogin", on && props.getDemo().isResetEndpointEnabled(),
                "demoEmail", UserService.DEMO_LOGIN_EMAIL,
                "betaEmail", DemoDataService.BETA_LOGIN_EMAIL,
                "note", "Demo travellers are fictional. Train and bus inventory is simulated sample data and is always labelled as such.");
    }

    /** Wipes and re-creates the demo travellers, trips, groups and messages. Sign in again afterwards. */
    @PostMapping("/reset")
    public Map<String, String> reset() {
        if (!props.getDemo().isEnabled() || !props.getDemo().isResetEndpointEnabled()) {
            throw ApiException.forbidden("Demo reset is disabled in this environment");
        }
        demo.reset();
        return Map.of("status", "RESET", "message", "Demo data re-created. Sign in again as " + UserService.DEMO_LOGIN_EMAIL + ".");
    }
}

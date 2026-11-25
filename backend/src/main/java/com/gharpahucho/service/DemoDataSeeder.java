package com.gharpahucho.service;

import com.gharpahucho.config.GharPahuchoProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Seeds demo data at startup (separate bean so the seeding call goes through the transactional proxy). */
@Component
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final GharPahuchoProperties props;
    private final DemoDataService demo;

    @Override
    public void run(ApplicationArguments args) {
        if (!props.getDemo().isEnabled()) {
            log.info("event=demo_seed_skipped reason=demo_mode_disabled");
            return;
        }
        try {
            demo.seedIfMissing();
        } catch (RuntimeException e) {
            // A seeding problem must never stop the API from starting.
            log.error("event=demo_seed_failed error={}", e.getMessage(), e);
        }
    }
}

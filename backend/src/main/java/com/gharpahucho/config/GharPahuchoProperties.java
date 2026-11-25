package com.gharpahucho.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All application configuration in one typed place. Every secret is read from environment variables
 * (see ../../resources/application.yml and /.env.example); nothing is hard-coded.
 */
@Data
@ConfigurationProperties(prefix = "gharpahucho")
public class GharPahuchoProperties {

    private Demo demo = new Demo();
    private Security security = new Security();
    private Flagsmith flagsmith = new Flagsmith();
    private Sarvam sarvam = new Sarvam();
    private Maps maps = new Maps();
    private NudgeBee nudgebee = new NudgeBee();

    @Data
    public static class Demo {
        /** Seeds demo users, trips and simulated transport inventory. */
        private boolean enabled = true;
        /** Exposes /api/demo/reset and the one-click demo login. Never enable in production. */
        private boolean resetEndpointEnabled = true;
        /** Public demo deployments: reject e-mail/password sign-in and registration; only the one-click demo login works. */
        private boolean demoOnly = false;
        private String demoPassword = "demo1234";
    }

    @Data
    public static class Security {
        /** HMAC secret for demo JWTs (>= 32 chars). Override in every real environment. */
        private String jwtSecret = "change-me-change-me-change-me-change-me-0123456789";
        private long jwtTtlMinutes = 720;
        private List<String> allowedOrigins = List.of("http://localhost:5173", "http://127.0.0.1:5173", "http://localhost:4173", "http://127.0.0.1:4173", "http://localhost:8081");
    }

    @Data
    public static class Flagsmith {
        /** Server-side environment key (ser.xxx). NEVER sent to the browser. */
        private String serverKey = "";
        /** Override for self-hosted Flagsmith, e.g. http://localhost:8000/api/v1/ . */
        private String apiUrl = "";
        /** Local evaluation downloads the environment document and evaluates segments/rollouts in-process. */
        private boolean localEvaluation = true;
        private int refreshIntervalSeconds = 10;
        private int connectTimeoutMs = 2000;
        private int readTimeoutMs = 3000;
        /** Safe defaults used when Flagsmith is not configured or unreachable. */
        private Map<String, Boolean> defaults = new HashMap<>();
        private Map<String, String> configDefaults = new HashMap<>();

        public boolean isConfigured() {
            return serverKey != null && !serverKey.isBlank();
        }
    }

    @Data
    public static class Sarvam {
        private String apiKey = "";
        private String baseUrl = "https://api.sarvam.ai";
        private String chatModel = "sarvam-105b";
        private String sttModel = "saaras:v4";
        private int timeoutSeconds = 25;
        private int rateLimitPerMinute = 20;

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    @Data
    public static class Maps {
        /** osrm | google */
        private String provider = "osrm";
        private String osrmBaseUrl = "https://router.project-osrm.org";
        private String googleApiKey = "";
        private boolean nominatimEnabled = true;
        private String nominatimBaseUrl = "https://nominatim.openstreetmap.org";
        private String userAgent = "GharPahucho/1.0 (hackathon demo)";
        /** Assumptions for the cab estimate (clearly labelled as an estimate in the UI). */
        private double cabRatePerKm = 14.0;
        private double cabBaseFare = 250.0;
        private int timeoutSeconds = 6;
    }

    @Data
    public static class NudgeBee {
        /** Event Webhook URL from NudgeBee: Admin > Integrations > Webhooks > Event Webhook. */
        private String eventWebhookUrl = "";
        /** Webhook token; sent as Authorization: Bearer so it never appears in URLs or logs. */
        private String webhookToken = "";
        /** Optional: GraphQL API (https://<domain>/api/graphql) + API token (sk-nb-...) for connection checks. */
        private String graphqlUrl = "";
        private String apiToken = "";
        private String serviceName = "gharpahucho-backend";
        private String namespace = "gharpahucho";
        private String environment = "local";
        private boolean monitorEnabled = true;
        /** Consecutive failures before a dependency alert is raised. */
        private int failureThreshold = 3;
        private int dedupeMinutes = 10;

        public boolean isWebhookConfigured() {
            return eventWebhookUrl != null && !eventWebhookUrl.isBlank();
        }

        public boolean isGraphqlConfigured() {
            return graphqlUrl != null && !graphqlUrl.isBlank() && apiToken != null && !apiToken.isBlank();
        }
    }
}

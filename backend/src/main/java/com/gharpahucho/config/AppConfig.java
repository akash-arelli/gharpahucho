package com.gharpahucho.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(GharPahuchoProperties.class)
public class AppConfig {

    /** All travel dates are interpreted in India Standard Time. */
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Bean
    public Clock clock() {
        return Clock.system(IST);
    }

    /** Factory with explicit timeouts: a slow third party must never hang a user request. */
    public static ClientHttpRequestFactory requestFactory(Duration connect, Duration read) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(connect).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(read);
        return factory;
    }

    public static RestClient.Builder restClient(String baseUrl, int timeoutSeconds) {
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory(Duration.ofSeconds(Math.min(5, timeoutSeconds)), Duration.ofSeconds(timeoutSeconds)));
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder;
    }
}

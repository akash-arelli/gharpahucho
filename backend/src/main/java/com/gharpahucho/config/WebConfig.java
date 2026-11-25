package com.gharpahucho.config;

import com.gharpahucho.integration.flagsmith.FeatureGateInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final FeatureGateInterceptor featureGate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(featureGate).addPathPatterns("/api/**");
    }
}

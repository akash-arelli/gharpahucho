package com.gharpahucho.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the built React app from the same process as the API, so a deployment is one container on one address (no CORS,
 * no second host). Real files are served as-is; any other non-API path falls back to index.html so client-side routes such
 * as /app/pass and the public family page /f/{token} survive a page refresh.
 *
 * In development the frontend runs on the Vite dev server instead; with no built files present this simply returns 404.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    private static final String[] NOT_THE_APP = {"api/", "actuator", "swagger-ui", "v3/"};

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        for (String prefix : NOT_THE_APP) {
                            if (resourcePath.startsWith(prefix)) {
                                return null;
                            }
                        }
                        if (resourcePath.contains(".")) {
                            return null; // a missing asset (js, css, png...) is a real 404, not the app shell
                        }
                        Resource index = location.createRelative("index.html");
                        return index.exists() && index.isReadable() ? index : null;
                    }
                });
    }
}

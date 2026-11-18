package com.stacc.backend.auth.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stacc.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream().map(String::strip).filter(origin -> !origin.isEmpty()).toList();
        for (String origin : allowedOrigins) {
            if (!isExactOrigin(origin)) {
                throw new IllegalArgumentException(
                        "FRONTEND_ORIGIN must list exact origins such as http://localhost:5173, separated by commas. "
                                + "Wildcards and paths are not allowed");
            }
        }
    }

    private static boolean isExactOrigin(String origin) {
        if (origin.contains("*")) {
            return false;
        }
        try {
            URI uri = new URI(origin);
            boolean web = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
            return web
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getRawPath().isEmpty()
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}

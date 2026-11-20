package com.seashare.seasharem3.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.List;

/**
 * Orígenes exactos autorizados para el frontend; una lista vacía no habilita CORS.
 */
@ConfigurationProperties(prefix = "seashare.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        for (String origin : allowedOrigins) {
            URI uri = URI.create(origin);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPort() > 65535) {
                throw new IllegalArgumentException(
                        "seashare.cors.allowed-origins must contain exact HTTP(S) origins: " + origin);
            }
        }
    }
}

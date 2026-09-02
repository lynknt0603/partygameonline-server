package com.partygameonline.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        Cors cors,
        Cookie cookie
) {

    public SecurityProperties {
        cors = cors == null ? new Cors(List.of()) : cors;
        cookie = cookie == null ? new Cookie(false, "lax") : cookie;
    }

    public record Cors(List<String> allowedOrigins) {

        public Cors {
            if (allowedOrigins == null) {
                allowedOrigins = List.of();
            } else {
                allowedOrigins = allowedOrigins.stream()
                        .flatMap((origin) -> java.util.Arrays.stream(origin.split(",")))
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        // Credentialed browser requests must never use a
                        // wildcard. Wildcards are ignored instead of being
                        // reflected back as an arbitrary Origin.
                        .filter(origin -> !origin.contains("*"))
                        .toList();
            }
        }
    }

    public record Cookie(boolean secure, String sameSite) {

        public Cookie {
            if (sameSite == null || sameSite.isBlank()) {
                sameSite = "lax";
            }
        }
    }
}

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

        private static final List<String> PRODUCTION_ORIGINS = List.of(
                "https://partygamefun-online.vercel.app",
                "https://partygameonline-platform-olbh8ixzt-linh-7808.vercel.app"
        );

        public Cors {
            if (allowedOrigins == null) {
                allowedOrigins = List.of();
            } else {
                List<String> normalized = allowedOrigins.stream()
                        .flatMap((origin) -> java.util.Arrays.stream(origin.split(",")))
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .filter(origin -> !origin.contains("*"))
                        .toList();
                boolean wildcardOnly = normalized.isEmpty() && allowedOrigins.stream()
                        .anyMatch(origin -> origin != null && origin.contains("*"));
                // Replace the legacy production wildcard with the known exact
                // SPA origins. Credentialed requests must never reflect an
                // arbitrary Origin.
                allowedOrigins = wildcardOnly ? PRODUCTION_ORIGINS : normalized;
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

package com.partygameonline.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SecurityPropertiesTests {

    @Test
    void replacesLegacyWildcardWithExactProductionOrigins() {
        SecurityProperties.Cors cors = new SecurityProperties.Cors(List.of("*"));

        assertThat(cors.allowedOrigins()).containsExactly(
                "https://partygamefun-online.vercel.app",
                "https://partygameonline-platform-olbh8ixzt-linh-7808.vercel.app"
        );
        assertThat(cors.allowedOrigins()).doesNotContain("https://evil.example", "*");
    }

    @Test
    void preservesExplicitOrigins() {
        SecurityProperties.Cors cors = new SecurityProperties.Cors(List.of("https://boardverse.example"));

        assertThat(cors.allowedOrigins()).containsExactly("https://boardverse.example");
    }
}

package com.partygameonline.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.session.domain.PlayerPrincipal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuthTokenServiceTests {

    private static final String SECRET = "test-auth-token-secret-with-at-least-32-characters";
    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");

    @Test
    void aesGcmTokenRoundTripsWithoutExposingIdentity() {
        AuthTokenService tokens = serviceAt(NOW);
        PlayerPrincipal player = new PlayerPrincipal(
                "player-secret-id",
                "Linh",
                com.partygameonline.session.domain.SessionKind.GUEST,
                NOW,
                "/assets/avatars/default.png"
        );

        String first = tokens.issue(player);
        String second = tokens.issue(player);

        assertThat(first).startsWith("pgo1.").doesNotContain("player-secret-id", "Linh");
        assertThat(second).isNotEqualTo(first);
        assertThat(tokens.verify(first)).contains(player);
    }

    @Test
    void rejectsTamperedAndExpiredTokens() {
        String token = serviceAt(NOW).issue(PlayerPrincipal.guest("p1", "Linh"));
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");

        assertThat(serviceAt(NOW).verify(tampered)).isEmpty();
        assertThat(serviceAt(NOW.plus(Duration.ofHours(25))).verify(token)).isEmpty();
    }

    @Test
    void refusesWeakConfiguration() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new AuthTokenService("too-short", Duration.ofHours(24), Clock.systemUTC())
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_TOKEN_KEY");
    }

    private static AuthTokenService serviceAt(Instant instant) {
        return new AuthTokenService(
                SECRET,
                Duration.ofHours(24),
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }
}

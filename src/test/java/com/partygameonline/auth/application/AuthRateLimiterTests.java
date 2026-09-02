package com.partygameonline.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AuthRateLimiterTests {

    @Test
    void limitsLoginAttemptsPerRemoteAddress() {
        AuthRateLimiter limiter = new AuthRateLimiter(2, Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("203.0.113.10")).isTrue();
        assertThat(limiter.tryAcquire("203.0.113.10")).isTrue();
        assertThat(limiter.tryAcquire("203.0.113.10")).isFalse();
        assertThat(limiter.tryAcquire("203.0.113.11")).isTrue();
    }
}

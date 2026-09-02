package com.partygameonline.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GameActionRateLimiterTests {

    @Test
    void limitsEachPlayerAndOperationWithinTheWindow() {
        GameActionRateLimiter limiter = new GameActionRateLimiter(2, Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("p1", "PLAY_INGREDIENT")).isTrue();
        assertThat(limiter.tryAcquire("p1", "PLAY_INGREDIENT")).isTrue();
        assertThat(limiter.tryAcquire("p1", "PLAY_INGREDIENT")).isFalse();
        assertThat(limiter.tryAcquire("p1", "SELECT_TARGET")).isFalse();
        assertThat(limiter.tryAcquire("p2", "PLAY_INGREDIENT")).isTrue();
    }

    @Test
    void timeoutCommandsAreNotCountedAsClientFloods() {
        GameActionRateLimiter limiter = new GameActionRateLimiter(1, Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("p1", "TIMEOUT")).isTrue();
        assertThat(limiter.tryAcquire("p1", "TIMEOUT")).isTrue();
    }
}

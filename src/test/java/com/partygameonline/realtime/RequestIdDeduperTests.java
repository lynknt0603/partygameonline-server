package com.partygameonline.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RequestIdDeduperTests {

    @Test
    void remembersRecentIdsPerPlayer() {
        RequestIdDeduper deduper = new RequestIdDeduper();
        assertThat(deduper.isDuplicate("p1", "r1")).isFalse();
        assertThat(deduper.isDuplicate("p1", "r1")).isTrue();
        assertThat(deduper.isDuplicate("p2", "r1")).isFalse();
    }

    @Test
    void evictsLeastRecentlyUsedPlayerWhenCapacityIsReached() {
        AtomicLong now = new AtomicLong(1_000L);
        RequestIdDeduper deduper = new RequestIdDeduper(64, 2, Duration.ofMinutes(15), now::get);

        assertThat(deduper.isDuplicate("p1", "r1")).isFalse();
        assertThat(deduper.isDuplicate("p2", "r1")).isFalse();
        assertThat(deduper.isDuplicate("p3", "r1")).isFalse();

        assertThat(deduper.trackedPlayerCount()).isEqualTo(2);
        assertThat(deduper.isDuplicate("p1", "r1")).isFalse();
    }

    @Test
    void expiresInactivePlayers() {
        AtomicLong now = new AtomicLong(1_000L);
        RequestIdDeduper deduper = new RequestIdDeduper(64, 10, Duration.ofMinutes(15), now::get);

        assertThat(deduper.isDuplicate("p1", "r1")).isFalse();
        now.addAndGet(Duration.ofMinutes(15).toMillis());

        assertThat(deduper.trackedPlayerCount()).isZero();
        assertThat(deduper.isDuplicate("p1", "r1")).isFalse();
    }
}

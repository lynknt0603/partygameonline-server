package com.partygameonline.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestIdDeduperTests {

    @Test
    void remembersRecentIdsPerPlayer() {
        RequestIdDeduper deduper = new RequestIdDeduper();
        assertThat(deduper.isDuplicate("p1", "r1")).isFalse();
        assertThat(deduper.isDuplicate("p1", "r1")).isTrue();
        assertThat(deduper.isDuplicate("p2", "r1")).isFalse();
    }
}

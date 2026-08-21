package com.partygameonline.game.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeededRandomSourceTests {

    @Test
    void sameSeedProducesSameSequence() {
        SeededRandomSource first = new SeededRandomSource(42);
        SeededRandomSource second = new SeededRandomSource(42);

        assertThat(first.nextInt(100)).isEqualTo(second.nextInt(100));
        assertThat(first.nextLong()).isEqualTo(second.nextLong());

        List<Integer> a = new ArrayList<>(List.of(1, 2, 3, 4, 5));
        List<Integer> b = new ArrayList<>(List.of(1, 2, 3, 4, 5));
        first.shuffle(a);
        second.shuffle(b);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void differentSeedsDiverge() {
        SeededRandomSource first = new SeededRandomSource(1);
        SeededRandomSource second = new SeededRandomSource(2);
        assertThat(first.nextInt(10_000)).isNotEqualTo(second.nextInt(10_000));
    }
}

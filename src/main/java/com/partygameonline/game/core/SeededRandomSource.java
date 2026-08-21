package com.partygameonline.game.core;

import java.util.Random;

public final class SeededRandomSource implements RandomSource {

    private final Random random;

    public SeededRandomSource(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    @Override
    public long nextLong() {
        return random.nextLong();
    }
}

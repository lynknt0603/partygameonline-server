package com.partygameonline.game.core;

import java.util.Collections;
import java.util.List;

public interface RandomSource {

    int nextInt(int bound);

    long nextLong();

    default void shuffle(List<?> items) {
        for (int i = items.size() - 1; i > 0; i--) {
            int j = nextInt(i + 1);
            Collections.swap(items, i, j);
        }
    }
}

package com.partygameonline.game.games.demo;

import java.util.ArrayList;
import java.util.List;

final class DemoCards {

    static final int STARTING_HAND_SIZE = 5;
    static final List<String> SUITS = List.of("C", "D", "H", "S");

    private DemoCards() {
    }

    static List<String> standard52() {
        List<String> cards = new ArrayList<>(52);
        for (String suit : SUITS) {
            for (int rank = 1; rank <= 13; rank++) {
                cards.add(suit + "-" + String.format("%02d", rank));
            }
        }
        return cards;
    }
}

package com.partygameonline.game.games.demo;

public sealed interface DemoAction {

    record DrawCard() implements DemoAction {
    }

    record PlayCard(String cardId) implements DemoAction {
    }

    record EndTurn() implements DemoAction {
    }
}

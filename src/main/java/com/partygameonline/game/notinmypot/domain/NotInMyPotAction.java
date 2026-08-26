package com.partygameonline.game.notinmypot.domain;

import java.util.List;

public record NotInMyPotAction(
        String type,
        String commandId,
        Integer expectedVersion,
        String cardId,
        String declaredType,
        String actionType,
        String targetPlayerId,
        List<String> cardIds
) {

    public static final String PLAY_INGREDIENT = "PLAY_INGREDIENT";
    public static final String PLAY_ACTION = "PLAY_ACTION";
    public static final String SELECT_TARGET = "SELECT_TARGET";
    public static final String RETURN_SHOPPING_CARDS = "RETURN_SHOPPING_CARDS";
    public static final String REORDER_POT_CARDS = "REORDER_POT_CARDS";
    public static final String DECLARE_POT_READY = "DECLARE_POT_READY";
    public static final String TIMEOUT = "TIMEOUT";

    public NotInMyPotAction {
        cardIds = cardIds == null ? List.of() : List.copyOf(cardIds);
    }
}

package com.partygameonline.game.notinmypot.application;

import com.partygameonline.game.notinmypot.domain.NotInMyPotActionType;
import com.partygameonline.game.notinmypot.domain.NotInMyPotCard;
import com.partygameonline.game.notinmypot.domain.NotInMyPotIngredientType;
import com.partygameonline.game.notinmypot.domain.NotInMyPotRole;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable rule tables for Not In My Pot. */
public final class NotInMyPotRules {

    public static final int VEGETABLE_COUNT = 20;
    public static final int SALT_COUNT = 10;
    public static final int MEAT_COUNT = 12;
    public static final int SPECIAL_ACTION_COUNT = 3;

    private static final Map<Integer, RoleDistribution> ROLE_DISTRIBUTIONS = Map.of(
            3, new RoleDistribution(2, 1),
            4, new RoleDistribution(3, 1),
            5, new RoleDistribution(4, 1),
            6, new RoleDistribution(4, 2),
            7, new RoleDistribution(5, 2),
            8, new RoleDistribution(6, 2)
    );

    private static final Map<Integer, Integer> OUT_OF_HOUSE_COUNTS = Map.of(
            3, 12,
            4, 14,
            5, 16,
            6, 18,
            7, 20,
            8, 22
    );

    private static final Map<Integer, Integer> TARGET_SCORES = Map.of(
            3, 5,
            4, 8,
            5, 11,
            6, 6,
            7, 9,
            8, 12
    );

    private NotInMyPotRules() {
    }

    public static void validatePlayerCount(int playerCount) {
        if (!ROLE_DISTRIBUTIONS.containsKey(playerCount)) {
            throw new IllegalArgumentException("Not In My Pot requires 3-8 players");
        }
    }

    public static RoleDistribution roleDistribution(int playerCount) {
        validatePlayerCount(playerCount);
        return ROLE_DISTRIBUTIONS.get(playerCount);
    }

    public static int targetScore(int playerCount) {
        validatePlayerCount(playerCount);
        return TARGET_SCORES.get(playerCount);
    }

    public static int outOfHouseCount(int playerCount) {
        validatePlayerCount(playerCount);
        return OUT_OF_HOUSE_COUNTS.get(playerCount);
    }

    public static List<NotInMyPotRole> rolesFor(int playerCount) {
        RoleDistribution distribution = roleDistribution(playerCount);
        List<NotInMyPotRole> roles = new ArrayList<>();
        for (int i = 0; i < distribution.vegetarians(); i++) {
            roles.add(NotInMyPotRole.VEGETARIAN);
        }
        for (int i = 0; i < distribution.meatEaters(); i++) {
            roles.add(NotInMyPotRole.MEAT_EATER);
        }
        return roles;
    }

    public static List<NotInMyPotCard> buildDeck(int playerCount) {
        validatePlayerCount(playerCount);
        List<NotInMyPotCard> deck = new ArrayList<>();
        addIngredients(deck, NotInMyPotIngredientType.VEGETABLE, VEGETABLE_COUNT, "VEGETABLE");
        addIngredients(deck, NotInMyPotIngredientType.SALT, SALT_COUNT, "SALT");
        addIngredients(deck, NotInMyPotIngredientType.MEAT, MEAT_COUNT, "MEAT");
        addActions(deck, NotInMyPotActionType.OUT_OF_HOUSE, outOfHouseCount(playerCount));
        addActions(deck, NotInMyPotActionType.SCOOP_OUT, SPECIAL_ACTION_COUNT);
        addActions(deck, NotInMyPotActionType.SLOTTED_SPOON, SPECIAL_ACTION_COUNT);
        addActions(deck, NotInMyPotActionType.EMERGENCY_SHOPPING, SPECIAL_ACTION_COUNT);
        addActions(deck, NotInMyPotActionType.TRASH_OUT, SPECIAL_ACTION_COUNT);
        return deck;
    }

    public static int ingredientScore(NotInMyPotIngredientType type) {
        return type == null ? 0 : type.score();
    }

    private static void addIngredients(
            List<NotInMyPotCard> deck,
            NotInMyPotIngredientType type,
            int count,
            String idPart
    ) {
        for (int i = 1; i <= count; i++) {
            deck.add(NotInMyPotCard.ingredient(
                    "NIMP-I-" + idPart + "-" + String.format("%02d", i),
                    type
            ));
        }
    }

    private static void addActions(
            List<NotInMyPotCard> deck,
            NotInMyPotActionType type,
            int count
    ) {
        for (int i = 1; i <= count; i++) {
            deck.add(NotInMyPotCard.action(
                    "NIMP-A-" + type.name() + "-" + String.format("%02d", i),
                    type
            ));
        }
    }

    public record RoleDistribution(int vegetarians, int meatEaters) {
    }
}

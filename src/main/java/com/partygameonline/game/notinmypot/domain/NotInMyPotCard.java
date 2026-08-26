package com.partygameonline.game.notinmypot.domain;

import java.util.Objects;

/** Server-owned card. A request may reference its id, but never its value. */
public record NotInMyPotCard(
        String cardId,
        NotInMyPotCardCategory category,
        NotInMyPotIngredientType ingredientType,
        NotInMyPotActionType actionType
) {

    public NotInMyPotCard {
        Objects.requireNonNull(cardId, "cardId");
        Objects.requireNonNull(category, "category");
        if (category == NotInMyPotCardCategory.INGREDIENT
                && (ingredientType == null || actionType != null)) {
            throw new IllegalArgumentException("Ingredient card must have only an ingredient type");
        }
        if (category == NotInMyPotCardCategory.ACTION
                && (actionType == null || ingredientType != null)) {
            throw new IllegalArgumentException("Action card must have only an action type");
        }
    }

    public static NotInMyPotCard ingredient(String cardId, NotInMyPotIngredientType type) {
        return new NotInMyPotCard(cardId, NotInMyPotCardCategory.INGREDIENT, type, null);
    }

    public static NotInMyPotCard action(String cardId, NotInMyPotActionType type) {
        return new NotInMyPotCard(cardId, NotInMyPotCardCategory.ACTION, null, type);
    }

    public boolean isIngredient() {
        return category == NotInMyPotCardCategory.INGREDIENT;
    }

    public boolean isAction() {
        return category == NotInMyPotCardCategory.ACTION;
    }

    public int score() {
        return ingredientType == null ? 0 : ingredientType.score();
    }

    public int value() {
        return score();
    }

    public String id() {
        return cardId;
    }

    public String type() {
        return isIngredient() ? ingredientType.name() : actionType.name();
    }
}

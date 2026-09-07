package com.partygameonline.game.liarsnumber.application;

import com.partygameonline.game.liarsnumber.domain.LiarsNumberCard;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberCardVariant;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberPlayerState;
import java.util.ArrayList;
import java.util.List;

public final class LiarsNumberRules {

    private LiarsNumberRules() {
    }

    public static List<LiarsNumberCard> createDeck() {
        List<LiarsNumberCard> deck = new ArrayList<>(64);
        for (int typeId = 1; typeId <= 8; typeId++) {
            for (int copy = 1; copy <= 7; copy++) {
                deck.add(new LiarsNumberCard("type-" + typeId + "-normal-" + copy, typeId, LiarsNumberCardVariant.NORMAL));
            }
            deck.add(new LiarsNumberCard("type-" + typeId + "-roman", typeId, LiarsNumberCardVariant.ROMAN));
        }
        return deck;
    }

    public static int getPenaltyWeight(LiarsNumberCard card) {
        return card.variant() == LiarsNumberCardVariant.ROMAN ? 2 : 1;
    }

    public static int getPenaltyScoreForType(LiarsNumberPlayerState player, int typeId) {
        return player.getPenaltyCards().stream()
                .filter(card -> card.typeId() == typeId)
                .mapToInt(LiarsNumberRules::getPenaltyWeight)
                .sum();
    }

    public static boolean claimIsTrue(LiarsNumberCard card, int declaredType) {
        return card.typeId() == declaredType;
    }
}

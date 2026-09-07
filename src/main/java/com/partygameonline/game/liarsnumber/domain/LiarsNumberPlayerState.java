package com.partygameonline.game.liarsnumber.domain;

import java.util.ArrayList;
import java.util.List;

public class LiarsNumberPlayerState {

    private final String playerId;
    private final String displayName;
    private final int seat;
    private final List<LiarsNumberCard> hand = new ArrayList<>();
    private final List<LiarsNumberCard> penaltyCards = new ArrayList<>();

    public LiarsNumberPlayerState(String playerId, String displayName, int seat) {
        this.playerId = playerId;
        this.displayName = displayName;
        this.seat = seat;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSeat() {
        return seat;
    }

    public List<LiarsNumberCard> getHand() {
        return hand;
    }

    public List<LiarsNumberCard> getPenaltyCards() {
        return penaltyCards;
    }

    public LiarsNumberCard findHand(String cardId) {
        if (cardId == null) {
            return null;
        }
        return hand.stream().filter(card -> card.cardId().equals(cardId)).findFirst().orElse(null);
    }

    public int penaltyScoreForType(int typeId) {
        return penaltyCards.stream()
                .filter(card -> card.typeId() == typeId)
                .mapToInt(LiarsNumberCard::penaltyWeight)
                .sum();
    }

    public int totalPenaltyScore() {
        return penaltyCards.stream().mapToInt(LiarsNumberCard::penaltyWeight).sum();
    }
}

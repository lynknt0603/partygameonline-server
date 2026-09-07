package com.partygameonline.game.liarsnumber.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LiarsNumberActiveRound {

    private final LiarsNumberCard card;
    private final String originalSenderId;
    private String currentSenderId;
    private String currentReceiverId;
    private Integer declaredType;
    private final Set<String> seenBy = new LinkedHashSet<>();
    private final List<LiarsNumberClaim> history = new ArrayList<>();

    public LiarsNumberActiveRound(LiarsNumberCard card, String originalSenderId) {
        this.card = card;
        this.originalSenderId = originalSenderId;
        this.currentSenderId = originalSenderId;
        this.seenBy.add(originalSenderId);
    }

    public LiarsNumberCard getCard() {
        return card;
    }

    public String getOriginalSenderId() {
        return originalSenderId;
    }

    public String getCurrentSenderId() {
        return currentSenderId;
    }

    public void setCurrentSenderId(String currentSenderId) {
        this.currentSenderId = currentSenderId;
    }

    public String getCurrentReceiverId() {
        return currentReceiverId;
    }

    public void setCurrentReceiverId(String currentReceiverId) {
        this.currentReceiverId = currentReceiverId;
    }

    public Integer getDeclaredType() {
        return declaredType;
    }

    public void setDeclaredType(Integer declaredType) {
        this.declaredType = declaredType;
    }

    public Set<String> getSeenBy() {
        return seenBy;
    }

    public List<LiarsNumberClaim> getHistory() {
        return history;
    }

    public void addSeenBy(String playerId) {
        seenBy.add(playerId);
    }

    public void addClaim(LiarsNumberClaim claim) {
        history.add(claim);
    }
}

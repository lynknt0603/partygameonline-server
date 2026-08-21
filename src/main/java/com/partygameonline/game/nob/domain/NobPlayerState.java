package com.partygameonline.game.nob.domain;

import java.util.ArrayList;
import java.util.List;

public class NobPlayerState {

    private final String playerId;
    private final String displayName;
    private final int seat;
    private boolean alive = true;
    private boolean connected = true;
    private NobBloodline currentBloodline;
    private NobBloodlineKnowledge knowledgeState = NobBloodlineKnowledge.KNOWN;
    private final List<NobCardInstance> hand = new ArrayList<>();
    private final List<NobCardInstance> revealedCards = new ArrayList<>();
    private final List<NobCardInstance> usedCards = new ArrayList<>();
    private final List<NobMoonMark> moonMarks = new ArrayList<>();
    private final List<NobObservation> observations = new ArrayList<>();
    private final List<String> passedInstanceIds = new ArrayList<>();
    private NobInspectReveal inspectReveal;

    public NobPlayerState(String playerId, String displayName, int seat) {
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

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public NobBloodline getCurrentBloodline() {
        return currentBloodline;
    }

    public void setCurrentBloodline(NobBloodline currentBloodline) {
        this.currentBloodline = currentBloodline;
    }

    public NobBloodlineKnowledge getKnowledgeState() {
        return knowledgeState;
    }

    public void setKnowledgeState(NobBloodlineKnowledge knowledgeState) {
        this.knowledgeState = knowledgeState;
    }

    public List<NobCardInstance> getHand() {
        return hand;
    }

    public List<NobCardInstance> getRevealedCards() {
        return revealedCards;
    }

    public List<NobCardInstance> getUsedCards() {
        return usedCards;
    }

    public List<NobMoonMark> getMoonMarks() {
        return moonMarks;
    }

    public int moonMarkCount() {
        return moonMarks.size();
    }

    public int score() {
        return moonMarks.stream().mapToInt(NobMoonMark::value).sum();
    }

    public List<NobObservation> getObservations() {
        return observations;
    }

    public List<String> getPassedInstanceIds() {
        return passedInstanceIds;
    }

    public NobInspectReveal getInspectReveal() {
        return inspectReveal;
    }

    public void setInspectReveal(NobInspectReveal inspectReveal) {
        this.inspectReveal = inspectReveal;
    }

    public NobCardInstance findHand(String instanceId) {
        return hand.stream().filter(card -> card.instanceId().equals(instanceId)).findFirst().orElse(null);
    }

    public List<NobCardInstance> unrevealedHand() {
        return List.copyOf(hand);
    }

    public boolean knowsOwnBloodline() {
        return knowledgeState == NobBloodlineKnowledge.KNOWN
                || knowledgeState == NobBloodlineKnowledge.PUBLICLY_REVEALED;
    }
}

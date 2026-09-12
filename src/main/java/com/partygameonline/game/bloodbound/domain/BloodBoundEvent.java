package com.partygameonline.game.bloodbound.domain;

public record BloodBoundEvent(
        String type,
        String text,
        String textVi,
        String actorPlayerId,
        String targetPlayerId
) {
    public static BloodBoundEvent log(String text, String textVi) {
        return new BloodBoundEvent("LOG", text, textVi, null, null);
    }
}

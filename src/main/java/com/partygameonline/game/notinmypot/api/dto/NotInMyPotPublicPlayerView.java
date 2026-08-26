package com.partygameonline.game.notinmypot.api.dto;

public record NotInMyPotPublicPlayerView(
        String playerId,
        String displayName,
        int seat,
        boolean active,
        boolean expelled,
        boolean connected,
        boolean you,
        int doorCount,
        int handCount,
        String role,
        boolean winner,
        Integer oldElo,
        Integer eloDelta,
        Integer newElo
) {
}

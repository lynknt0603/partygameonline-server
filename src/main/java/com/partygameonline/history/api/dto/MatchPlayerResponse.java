package com.partygameonline.history.api.dto;

public record MatchPlayerResponse(
        String playerId,
        String displayName,
        Integer seat,
        boolean winner,
        String result,
        Integer score,
        String role,
        String bloodline
) {

    public MatchPlayerResponse(String playerId, String displayName, Integer seat, boolean winner) {
        this(playerId, displayName, seat, winner, winner ? "WIN" : "LOSS", null, null, null);
    }
}
